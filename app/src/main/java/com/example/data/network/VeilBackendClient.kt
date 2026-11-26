package com.example.data.network

import android.util.Base64
import com.example.data.local.AuthType
import com.example.data.local.BackendProfileEntity
import com.example.data.local.BackendType
import com.example.data.local.ChannelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class ResolvedBackendConfig(
    val profile: BackendProfileEntity,
    val apiKey: String,
    val customHeaders: Map<String, String>
)

data class ConnectionTestResult(
    val success: Boolean,
    val statusCode: Int,
    val latencyMs: Long,
    val message: String,
    val isCleartextWarning: Boolean = false
)

data class RemoteDeviceBinding(
    val deviceBindingId: String,
    val publicKey: String,
    val username: String,
    val motto: String,
    val gender: String,
    val pfpCipherUrl: String,
    val updatedAt: Long
)

data class RemoteRoomRecord(
    val roomCode: String,
    val roomName: String,
    val hostPublicKey: String,
    val hostDeviceId: String,
    val createdAt: Long
)

data class RemoteMemberRecord(
    val id: String,
    val roomCode: String,
    val deviceBindingId: String,
    val username: String,
    val motto: String,
    val gender: String,
    val pfpCipherUrl: String,
    val publicKey: String,
    val joinedAt: Long
)

data class RemoteCommunityRecord(
    val id: String,
    val roomCode: String,
    val name: String,
    val description: String,
    val isDefault: Boolean,
    val createdByPublicKey: String,
    val createdAt: Long
)

data class RemoteCommunityMemberRecord(
    val communityId: String,
    val memberPublicKey: String,
    val encryptedCommunityKey: String
)

data class RemoteEncryptedMessage(
    val id: String,
    val roomCode: String,
    val channelType: ChannelType,
    val targetId: String,
    val senderPublicKey: String,
    val iv: String,
    val ciphertext: String,
    val maxViews: Int = 0,
    val viewsConsumed: Int = 0,
    val createdAt: Long
)

data class RemoteCallSignal(
    val id: String,
    val roomCode: String,
    val callerPublicKey: String,
    val calleePublicKey: String,
    val signalType: String,
    val encryptedPayload: String,
    val createdAt: Long
)

class VeilBackendClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val octetMediaType = "application/octet-stream".toMediaType()

    fun validateHttpUrl(rawUrl: String): Result<String> {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("Server URL is required"))
        return runCatching {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase()
            require(scheme == "https" || scheme == "http") { "URL must start with https:// or http://" }
            require(!uri.host.isNullOrBlank()) { "URL must include a valid host" }
            trimmed
        }
    }

    fun validateWebSocketUrl(rawUrl: String): Result<String> {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return Result.success("")
        return runCatching {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase()
            require(scheme == "wss" || scheme == "ws") { "WebSocket URL must start with wss:// or ws://" }
            require(!uri.host.isNullOrBlank()) { "WebSocket URL must include a valid host" }
            trimmed
        }
    }

    fun isCleartextUrl(url: String): Boolean {
        val lower = url.trim().lowercase()
        return lower.startsWith("http://") || lower.startsWith("ws://")
    }

    suspend fun testConnection(config: ResolvedBackendConfig): ConnectionTestResult =
        withContext(Dispatchers.IO) {
            val validUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { err ->
                return@withContext ConnectionTestResult(false, 0, 0, err.message ?: "Invalid URL")
            }
            val cleartext = isCleartextUrl(validUrl)
            val targetUrl = when (config.profile.backendType) {
                BackendType.SUPABASE -> "$validUrl/rest/v1/veil_rooms?select=room_code&limit=1"
                BackendType.SELF_HOSTED -> buildPathUrl(validUrl, config.profile.healthPath)
            }
            val start = System.currentTimeMillis()
            runCatching {
                val request = applyAuthHeaders(Request.Builder().url(targetUrl).get(), config).build()
                client.newCall(request).execute().use { resp ->
                    val elapsed = System.currentTimeMillis() - start
                    when {
                        resp.isSuccessful -> ConnectionTestResult(
                            true, resp.code, elapsed,
                            if (config.profile.backendType == BackendType.SUPABASE) {
                                "Supabase connected and Veil v1.0.1 tables ready (${elapsed}ms)"
                            } else {
                                "Server reachable (${resp.code} · ${elapsed}ms)"
                            },
                            cleartext
                        )
                        config.profile.backendType == BackendType.SUPABASE && resp.code == 404 -> ConnectionTestResult(
                            true, resp.code, elapsed,
                            "Supabase reachable (${elapsed}ms) — run the SQL schema in Supabase to create Veil tables",
                            cleartext
                        )
                        resp.code == 401 || resp.code == 403 -> ConnectionTestResult(
                            false, resp.code, elapsed,
                            "Authentication rejected (${resp.code}). Verify your API key or headers.",
                            cleartext
                        )
                        else -> ConnectionTestResult(
                            false, resp.code, elapsed,
                            "Endpoint returned HTTP ${resp.code} (${elapsed}ms)",
                            cleartext
                        )
                    }
                }
            }.getOrElse { err ->
                ConnectionTestResult(
                    false, 0, System.currentTimeMillis() - start,
                    "Connection failed: ${err.localizedMessage ?: "unreachable"}",
                    cleartext
                )
            }
        }

    // 1. Device Bindings (Single-Account Anti-Duplicate)
    suspend fun upsertDeviceBinding(
        config: ResolvedBackendConfig,
        binding: RemoteDeviceBinding
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        val url = when (config.profile.backendType) {
            BackendType.SUPABASE -> "$baseUrl/rest/v1/veil_device_bindings"
            BackendType.SELF_HOSTED -> "$baseUrl/api/v1/device-bindings"
        }
        val body = JSONObject().apply {
            put("device_binding_id", binding.deviceBindingId)
            put("public_key", binding.publicKey)
            put("username", binding.username)
            put("motto", binding.motto)
            put("gender", binding.gender)
            put("pfp_cipher_url", binding.pfpCipherUrl)
            put("updated_at", binding.updatedAt)
        }.toString()
        postJson(url, body, config, mergeDuplicates = true)
    }

    suspend fun fetchDeviceBinding(
        config: ResolvedBackendConfig,
        deviceBindingId: String
    ): Result<RemoteDeviceBinding?> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        val enc = URLEncoder.encode(deviceBindingId, "UTF-8")
        val url = when (config.profile.backendType) {
            BackendType.SUPABASE -> "$baseUrl/rest/v1/veil_device_bindings?device_binding_id=eq.$enc&limit=1"
            BackendType.SELF_HOSTED -> "$baseUrl/api/v1/device-bindings?device_binding_id=$enc"
        }
        runCatching {
            val req = applyAuthHeaders(Request.Builder().url(url).get(), config).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val raw = resp.body?.string().orEmpty().trim()
                val obj = extractFirstObject(raw) ?: return@use null
                RemoteDeviceBinding(
                    deviceBindingId = obj.optString("device_binding_id"),
                    publicKey = obj.optString("public_key"),
                    username = obj.optString("username"),
                    motto = obj.optString("motto"),
                    gender = obj.optString("gender", "Prefer not to say"),
                    pfpCipherUrl = obj.optString("pfp_cipher_url"),
                    updatedAt = obj.optLong("updated_at", System.currentTimeMillis())
                )
            }
        }
    }

    // 2. Veil Rooms
    suspend fun createRoom(
        config: ResolvedBackendConfig,
        roomCode: String,
        roomName: String,
        hostPublicKey: String,
        hostDeviceId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        val url = when (config.profile.backendType) {
            BackendType.SUPABASE -> "$baseUrl/rest/v1/veil_rooms"
            BackendType.SELF_HOSTED -> buildPathUrl(baseUrl, config.profile.roomsPath)
        }
        val body = JSONObject().apply {
            put("room_code", roomCode)
            put("room_name", roomName)
            put("host_public_key", hostPublicKey)
            put("host_device_id", hostDeviceId)
            put("created_at", System.currentTimeMillis())
        }.toString()
        postJson(url, body, config, mergeDuplicates = true)
    }

    suspend fun fetchRoom(
        config: ResolvedBackendConfig,
        roomCode: String
    ): Result<RemoteRoomRecord?> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        val enc = URLEncoder.encode(roomCode, "UTF-8")
        val url = when (config.profile.backendType) {
            BackendType.SUPABASE -> "$baseUrl/rest/v1/veil_rooms?room_code=eq.$enc&limit=1"
            BackendType.SELF_HOSTED -> "${buildPathUrl(baseUrl, config.profile.roomsPath)}?room_code=$enc"
        }
        runCatching {
            val req = applyAuthHeaders(Request.Builder().url(url).get(), config).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val obj = extractFirstObject(resp.body?.string().orEmpty().trim()) ?: return@use null
                RemoteRoomRecord(
                    roomCode = obj.optString("room_code", roomCode),
                    roomName = obj.optString("room_name", "Veil Room"),
                    hostPublicKey = obj.optString("host_public_key"),
                    hostDeviceId = obj.optString("host_device_id"),
                    createdAt = obj.optLong("created_at", System.currentTimeMillis())
                )
            }
        }
    }

    // 3. Veil Members (Room Profile Directory)
    suspend fun upsertMember(
        config: ResolvedBackendConfig,
        member: RemoteMemberRecord
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        val url = when (config.profile.backendType) {
            BackendType.SUPABASE -> "$baseUrl/rest/v1/veil_members?on_conflict=room_code,device_binding_id"
            BackendType.SELF_HOSTED -> buildPathUrl(baseUrl, config.profile.membersPath)
        }
        val body = JSONObject().apply {
            put("id", member.id)
            put("room_code", member.roomCode)
            put("device_binding_id", member.deviceBindingId)
            put("username", member.username)
            put("motto", member.motto)
            put("gender", member.gender)
            put("pfp_cipher_url", member.pfpCipherUrl)
            put("public_key", member.publicKey)
            put("joined_at", member.joinedAt)
        }.toString()
        postJson(url, body, config, mergeDuplicates = true)
    }

    suspend fun fetchMembers(
        config: ResolvedBackendConfig,
        roomCode: String
    ): Result<List<RemoteMemberRecord>> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        val enc = URLEncoder.encode(roomCode, "UTF-8")
        val url = when (config.profile.backendType) {
            BackendType.SUPABASE -> "$baseUrl/rest/v1/veil_members?room_code=eq.$enc&order=joined_at.asc"
            BackendType.SELF_HOSTED -> "${buildPathUrl(baseUrl, config.profile.membersPath)}?room_code=$enc"
        }
        runCatching {
            val req = applyAuthHeaders(Request.Builder().url(url).get(), config).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val arr = extractJsonArray(resp.body?.string().orEmpty().trim(), "members")
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    RemoteMemberRecord(
                        id = o.optString("id"),
                        roomCode = o.optString("room_code", roomCode),
                        deviceBindingId = o.optString("device_binding_id"),
                        username = o.optString("username", o.optString("member_handle", "Peer")),
                        motto = o.optString("motto"),
                        gender = o.optString("gender", "Prefer not to say"),
                        pfpCipherUrl = o.optString("pfp_cipher_url"),
                        publicKey = o.optString("public_key"),
                        joinedAt = o.optLong("joined_at", System.currentTimeMillis())
                    )
                }.filter { it.publicKey.isNotBlank() }
            }
        }
    }

    // 4. Communities & Community Members
    suspend fun createCommunity(
        config: ResolvedBackendConfig,
        community: RemoteCommunityRecord
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        val url = when (config.profile.backendType) {
            BackendType.SUPABASE -> "$baseUrl/rest/v1/veil_communities"
            BackendType.SELF_HOSTED -> "$baseUrl/api/v1/communities"
        }
        val body = JSONObject().apply {
            put("id", community.id)
            put("room_code", community.roomCode)
            put("name", community.name)
            put("description", community.description)
            put("is_default", community.isDefault)
            put("created_by_public_key", community.createdByPublicKey)
            put("created_at", community.createdAt)
        }.toString()
        postJson(url, body, config, mergeDuplicates = true)
    }

    suspend fun upsertCommunityMember(
        config: ResolvedBackendConfig,
        record: RemoteCommunityMemberRecord
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        val url = when (config.profile.backendType) {
            BackendType.SUPABASE -> "$baseUrl/rest/v1/veil_community_members"
            BackendType.SELF_HOSTED -> "$baseUrl/api/v1/community-members"
        }
        val body = JSONObject().apply {
            put("community_id", record.communityId)
            put("member_public_key", record.memberPublicKey)
            put("encrypted_community_key", record.encryptedCommunityKey)
        }.toString()
        postJson(url, body, config, mergeDuplicates = true)
    }

    suspend fun fetchCommunitiesInRoom(
        config: ResolvedBackendConfig,
        roomCode: String
    ): Result<List<RemoteCommunityRecord>> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        val enc = URLEncoder.encode(roomCode, "UTF-8")
        val url = when (config.profile.backendType) {
            BackendType.SUPABASE -> "$baseUrl/rest/v1/veil_communities?room_code=eq.$enc&order=created_at.asc"
            BackendType.SELF_HOSTED -> "$baseUrl/api/v1/communities?room_code=$enc"
        }
        runCatching {
            val req = applyAuthHeaders(Request.Builder().url(url).get(), config).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val arr = extractJsonArray(resp.body?.string().orEmpty().trim(), "communities")
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    RemoteCommunityRecord(
                        id = o.optString("id"),
                        roomCode = o.optString("room_code", roomCode),
                        name = o.optString("name"),
                        description = o.optString("description"),
                        isDefault = o.optBoolean("is_default", false),
                        createdByPublicKey = o.optString("created_by_public_key"),
                        createdAt = o.optLong("created_at", System.currentTimeMillis())
                    )
                }.filter { it.id.isNotBlank() }
            }
        }
    }

    suspend fun fetchMyCommunityMemberships(
        config: ResolvedBackendConfig,
        myPublicKey: String
    ): Result<List<RemoteCommunityMemberRecord>> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        val enc = URLEncoder.encode(myPublicKey, "UTF-8")
        val url = when (config.profile.backendType) {
            BackendType.SUPABASE -> "$baseUrl/rest/v1/veil_community_members?member_public_key=eq.$enc"
            BackendType.SELF_HOSTED -> "$baseUrl/api/v1/community-members?member_public_key=$enc"
        }
        runCatching {
            val req = applyAuthHeaders(Request.Builder().url(url).get(), config).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val arr = extractJsonArray(resp.body?.string().orEmpty().trim(), "community_members")
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    RemoteCommunityMemberRecord(
                        communityId = o.optString("community_id"),
                        memberPublicKey = o.optString("member_public_key"),
                        encryptedCommunityKey = o.optString("encrypted_community_key")
                    )
                }.filter { it.communityId.isNotBlank() }
            }
        }
    }

    // 5. Encrypted Messages (Direct & Community + Disappearing Views)
    suspend fun sendEncryptedMessage(
        config: ResolvedBackendConfig,
        message: RemoteEncryptedMessage
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        val payload = JSONObject().apply {
            put("id", message.id)
            put("room_code", message.roomCode)
            put("channel_type", message.channelType.name)
            put("target_id", message.targetId)
            put("sender_public_key", message.senderPublicKey)
            put("iv", message.iv)
            put("ciphertext", message.ciphertext)
            put("max_views", message.maxViews)
            put("views_consumed", message.viewsConsumed)
            put("created_at", message.createdAt)
        }.toString()

        val url = when (config.profile.backendType) {
            BackendType.SUPABASE -> "$baseUrl/rest/v1/veil_messages"
            BackendType.SELF_HOSTED -> buildPathUrl(baseUrl, config.profile.messagesPath)
        }
        val res = postJson(url, payload, config, mergeDuplicates = false)

        if (res.isSuccess &&
            config.profile.backendType == BackendType.SELF_HOSTED &&
            config.profile.webhookUrl.isNotBlank()
        ) {
            validateHttpUrl(config.profile.webhookUrl).onSuccess { webhookUrl ->
                runCatching {
                    val hookReq = applyAuthHeaders(
                        Request.Builder().url(webhookUrl).post(payload.toRequestBody(jsonMediaType)),
                        config
                    ).build()
                    client.newCall(hookReq).execute().close()
                }
            }
        }
        res
    }

    suspend fun fetchEncryptedMessages(
        config: ResolvedBackendConfig,
        roomCode: String
    ): Result<List<RemoteEncryptedMessage>> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        val enc = URLEncoder.encode(roomCode, "UTF-8")
        val url = when (config.profile.backendType) {
            BackendType.SUPABASE -> "$baseUrl/rest/v1/veil_messages?room_code=eq.$enc&order=created_at.asc"
            BackendType.SELF_HOSTED -> "${buildPathUrl(baseUrl, config.profile.messagesPath)}?room_code=$enc"
        }
        runCatching {
            val req = applyAuthHeaders(Request.Builder().url(url).get(), config).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val arr = extractJsonArray(resp.body?.string().orEmpty().trim(), "messages")
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    val channelType = runCatching {
                        ChannelType.valueOf(o.optString("channel_type", "DIRECT"))
                    }.getOrDefault(ChannelType.DIRECT)
                    RemoteEncryptedMessage(
                        id = o.optString("id"),
                        roomCode = o.optString("room_code", roomCode),
                        channelType = channelType,
                        targetId = o.optString("target_id"),
                        senderPublicKey = o.optString("sender_public_key"),
                        iv = o.optString("iv"),
                        ciphertext = o.optString("ciphertext"),
                        maxViews = o.optInt("max_views", 0),
                        viewsConsumed = o.optInt("views_consumed", 0),
                        createdAt = o.optLong("created_at", System.currentTimeMillis())
                    )
                }.filter { it.id.isNotBlank() && it.iv.isNotBlank() && it.ciphertext.isNotBlank() }
            }
        }
    }

    suspend fun updateMessageViewsOrPurge(
        config: ResolvedBackendConfig,
        messageId: String,
        viewsConsumed: Int,
        purgeCompletely: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        val enc = URLEncoder.encode(messageId, "UTF-8")
        runCatching {
            val url = when (config.profile.backendType) {
                BackendType.SUPABASE -> "$baseUrl/rest/v1/veil_messages?id=eq.$enc"
                BackendType.SELF_HOSTED -> "${buildPathUrl(baseUrl, config.profile.messagesPath)}/$enc"
            }
            val builder = if (purgeCompletely) {
                Request.Builder().url(url).delete()
            } else {
                val body = JSONObject().put("views_consumed", viewsConsumed).toString()
                Request.Builder().url(url).patch(body.toRequestBody(jsonMediaType))
            }
            client.newCall(applyAuthHeaders(builder, config).build()).execute().close()
        }
    }

    // 6. Encrypted Voice Call Signals (WebRTC SDP/ICE)
    suspend fun sendCallSignal(
        config: ResolvedBackendConfig,
        signal: RemoteCallSignal
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        val url = when (config.profile.backendType) {
            BackendType.SUPABASE -> "$baseUrl/rest/v1/veil_call_signals"
            BackendType.SELF_HOSTED -> "$baseUrl/api/v1/call-signals"
        }
        val body = JSONObject().apply {
            put("id", signal.id)
            put("room_code", signal.roomCode)
            put("caller_public_key", signal.callerPublicKey)
            put("callee_public_key", signal.calleePublicKey)
            put("signal_type", signal.signalType)
            put("encrypted_payload", signal.encryptedPayload)
            put("created_at", signal.createdAt)
        }.toString()
        postJson(url, body, config, mergeDuplicates = false)
    }

    suspend fun fetchIncomingCallSignals(
        config: ResolvedBackendConfig,
        roomCode: String,
        myPublicKey: String,
        sinceTimestamp: Long
    ): Result<List<RemoteCallSignal>> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        val encRoom = URLEncoder.encode(roomCode, "UTF-8")
        val encKey = URLEncoder.encode(myPublicKey, "UTF-8")
        val url = when (config.profile.backendType) {
            BackendType.SUPABASE ->
                "$baseUrl/rest/v1/veil_call_signals?room_code=eq.$encRoom&callee_public_key=eq.$encKey&created_at=gt.$sinceTimestamp&order=created_at.asc"
            BackendType.SELF_HOSTED ->
                "$baseUrl/api/v1/call-signals?room_code=$encRoom&callee_public_key=$encKey&since=$sinceTimestamp"
        }
        runCatching {
            val req = applyAuthHeaders(Request.Builder().url(url).get(), config).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val arr = extractJsonArray(resp.body?.string().orEmpty().trim(), "signals")
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    RemoteCallSignal(
                        id = o.optString("id"),
                        roomCode = o.optString("room_code", roomCode),
                        callerPublicKey = o.optString("caller_public_key"),
                        calleePublicKey = o.optString("callee_public_key"),
                        signalType = o.optString("signal_type"),
                        encryptedPayload = o.optString("encrypted_payload"),
                        createdAt = o.optLong("created_at", System.currentTimeMillis())
                    )
                }.filter { it.id.isNotBlank() }
            }
        }
    }

    // 7. Moderation Reports
    suspend fun submitReport(
        config: ResolvedBackendConfig,
        id: String,
        roomCode: String,
        reporterPublicKey: String,
        reportedPublicKey: String,
        reason: String,
        notes: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        val url = when (config.profile.backendType) {
            BackendType.SUPABASE -> "$baseUrl/rest/v1/veil_reports"
            BackendType.SELF_HOSTED -> "$baseUrl/api/v1/reports"
        }
        val body = JSONObject().apply {
            put("id", id)
            put("room_code", roomCode)
            put("reporter_public_key", reporterPublicKey)
            put("reported_public_key", reportedPublicKey)
            put("reason", reason)
            put("notes", notes)
            put("created_at", System.currentTimeMillis())
        }.toString()
        postJson(url, body, config, mergeDuplicates = false)
    }

    // 8. Encrypted Binary Blob Storage (Media & Encrypted PFPs)
    suspend fun uploadEncryptedBlob(
        config: ResolvedBackendConfig,
        roomCode: String,
        blobId: String,
        encryptedBytes: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        runCatching {
            val targetUrl = when {
                config.profile.backendType == BackendType.SUPABASE -> {
                    val bucket = config.profile.supabaseBucket.trim().ifBlank { "veil-media" }
                    "$baseUrl/storage/v1/object/$bucket/$roomCode/$blobId.veil"
                }
                config.profile.mediaUploadUrl.isNotBlank() -> {
                    val mediaBase = validateHttpUrl(config.profile.mediaUploadUrl).getOrThrow()
                    "$mediaBase/$roomCode/$blobId.veil"
                }
                else -> error("No external media endpoint configured")
            }

            val reqBuilder = applyAuthHeaders(
                Request.Builder().url(targetUrl).apply {
                    if (config.profile.backendType == BackendType.SUPABASE) {
                        post(encryptedBytes.toRequestBody(octetMediaType))
                        header("x-upsert", "true")
                    } else {
                        put(encryptedBytes.toRequestBody(octetMediaType))
                    }
                },
                config
            )
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) error("Media upload HTTP ${resp.code}")
                targetUrl
            }
        }
    }

    suspend fun downloadEncryptedBlob(
        config: ResolvedBackendConfig,
        blobUrl: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val validUrl = validateHttpUrl(blobUrl).getOrElse { return@withContext Result.failure(it) }
        runCatching {
            val req = applyAuthHeaders(Request.Builder().url(validUrl).get(), config).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Media download HTTP ${resp.code}")
                resp.body?.bytes() ?: error("Empty blob")
            }
        }
    }

    /**
     * Realtime WebSocket listener supporting Supabase Realtime Phoenix channel
     * AND Self-Hosted WebSocket relays. Emits a Unit ping whenever a new row arrives.
     */
    fun observeRealtimePings(
        config: ResolvedBackendConfig,
        roomCode: String
    ): Flow<Unit> = callbackFlow {
        val wsUrl = when (config.profile.backendType) {
            BackendType.SUPABASE -> {
                val base = validateHttpUrl(config.profile.baseUrl).getOrNull() ?: run {
                    close()
                    return@callbackFlow
                }
                val wssBase = base.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
                val encKey = URLEncoder.encode(config.apiKey.trim(), "UTF-8")
                "$wssBase/realtime/v1/websocket?apikey=$encKey&vsn=1.0.0"
            }
            BackendType.SELF_HOSTED -> {
                val custom = config.profile.webSocketUrl.trim()
                if (custom.isBlank() || validateWebSocketUrl(custom).isFailure) {
                    close()
                    return@callbackFlow
                }
                if (custom.contains("?")) "$custom&room_code=$roomCode" else "$custom?room_code=$roomCode"
            }
        }

        val req = applyAuthHeaders(Request.Builder().url(wsUrl), config).build()
        val ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (config.profile.backendType == BackendType.SUPABASE) {
                    val joinPayload = JSONObject().apply {
                        put("topic", "realtime:public:veil_messages")
                        put("event", "phx_join")
                        put("payload", JSONObject())
                        put("ref", "1")
                    }.toString()
                    webSocket.send(joinPayload)
                    val callJoin = JSONObject().apply {
                        put("topic", "realtime:public:veil_call_signals")
                        put("event", "phx_join")
                        put("payload", JSONObject())
                        put("ref", "2")
                    }.toString()
                    webSocket.send(callJoin)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("INSERT") || text.contains("ciphertext") || text.contains("signal_type")) {
                    trySend(Unit)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close()
            }
        })

        awaitClose { ws.close(1000, "Closed") }
    }

    private fun postJson(
        url: String,
        body: String,
        config: ResolvedBackendConfig,
        mergeDuplicates: Boolean
    ): Result<Unit> = runCatching {
        val builder = applyAuthHeaders(
            Request.Builder().url(url).post(body.toRequestBody(jsonMediaType)),
            config
        )
        if (config.profile.backendType == BackendType.SUPABASE) {
            builder.header(
                "Prefer",
                if (mergeDuplicates) "resolution=merge-duplicates,return=minimal" else "resolution=ignore-duplicates,return=minimal"
            )
        }
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 409) {
                error("Request failed (HTTP ${resp.code})")
            }
        }
    }

    private fun applyAuthHeaders(
        builder: Request.Builder,
        config: ResolvedBackendConfig
    ): Request.Builder {
        val apiKey = config.apiKey.trim()
        when (config.profile.backendType) {
            BackendType.SUPABASE -> {
                if (apiKey.isNotBlank()) {
                    builder.header("apikey", apiKey)
                    builder.header("Authorization", "Bearer $apiKey")
                }
            }
            BackendType.SELF_HOSTED -> {
                when (config.profile.authType) {
                    AuthType.NONE -> Unit
                    AuthType.BEARER -> if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
                    AuthType.API_KEY_HEADER -> {
                        val headerName = config.profile.authHeaderName.trim().ifBlank { "X-API-Key" }
                        if (apiKey.isNotBlank()) builder.header(headerName, apiKey)
                    }
                    AuthType.BASIC -> if (apiKey.isNotBlank()) {
                        val encoded = Base64.encodeToString(apiKey.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                        builder.header("Authorization", "Basic $encoded")
                    }
                }
                config.customHeaders.forEach { (k, v) ->
                    val cleanKey = k.trim()
                    if (cleanKey.isNotBlank() && v.isNotBlank()) builder.header(cleanKey, v.trim())
                }
            }
        }
        return builder
    }

    private fun buildPathUrl(baseUrl: String, path: String): String {
        val normalized = if (path.startsWith("/")) path else "/$path"
        return "$baseUrl$normalized"
    }

    private fun extractFirstObject(raw: String): JSONObject? {
        if (raw.isBlank()) return null
        return when {
            raw.startsWith("[") -> {
                val arr = JSONArray(raw)
                if (arr.length() == 0) null else arr.getJSONObject(0)
            }
            raw.startsWith("{") -> JSONObject(raw)
            else -> null
        }
    }

    private fun extractJsonArray(raw: String, wrapperKey: String): JSONArray {
        if (raw.isBlank()) return JSONArray()
        return when {
            raw.startsWith("[") -> JSONArray(raw)
            raw.startsWith("{") -> {
                val obj = JSONObject(raw)
                obj.optJSONArray(wrapperKey) ?: obj.optJSONArray("data") ?: JSONArray()
            }
            else -> JSONArray()
        }
    }

    companion object {
        const val SUPABASE_SQL_SCHEMA = """-- Veil v1.0.1 Zero-Knowledge Supabase Schema
-- Run once in Supabase SQL Editor. All messages, media, PFPs, locations & call signals
-- are encrypted on-device with AES-256-GCM before insert. DB owners only see ciphertext.

create table if not exists public.veil_device_bindings (
  device_binding_id text primary key,
  public_key text not null,
  username text not null,
  motto text not null default '',
  gender text not null default 'Prefer not to say',
  pfp_cipher_url text not null default '',
  updated_at bigint not null
);

create table if not exists public.veil_rooms (
  room_code text primary key,
  room_name text not null,
  host_public_key text not null,
  host_device_id text not null default '',
  created_at bigint not null
);

create table if not exists public.veil_members (
  id text primary key,
  room_code text not null references public.veil_rooms(room_code) on delete cascade,
  device_binding_id text not null,
  username text not null,
  motto text not null default '',
  gender text not null default 'Prefer not to say',
  pfp_cipher_url text not null default '',
  public_key text not null,
  joined_at bigint not null,
  unique (room_code, device_binding_id)
);

create table if not exists public.veil_communities (
  id text primary key,
  room_code text not null references public.veil_rooms(room_code) on delete cascade,
  name text not null,
  description text not null default '',
  is_default boolean not null default false,
  created_by_public_key text not null,
  created_at bigint not null
);

create table if not exists public.veil_community_members (
  community_id text not null references public.veil_communities(id) on delete cascade,
  member_public_key text not null,
  encrypted_community_key text not null,
  primary key (community_id, member_public_key)
);

create table if not exists public.veil_messages (
  id text primary key,
  room_code text not null references public.veil_rooms(room_code) on delete cascade,
  channel_type text not null check (channel_type in ('DIRECT', 'COMMUNITY')),
  target_id text not null,
  sender_public_key text not null,
  iv text not null,
  ciphertext text not null,
  max_views integer not null default 0,
  views_consumed integer not null default 0,
  created_at bigint not null
);

create table if not exists public.veil_call_signals (
  id text primary key,
  room_code text not null references public.veil_rooms(room_code) on delete cascade,
  caller_public_key text not null,
  callee_public_key text not null,
  signal_type text not null,
  encrypted_payload text not null,
  created_at bigint not null
);

create table if not exists public.veil_reports (
  id text primary key,
  room_code text not null,
  reporter_public_key text not null,
  reported_public_key text not null,
  reason text not null,
  notes text not null default '',
  created_at bigint not null
);

alter table public.veil_device_bindings enable row level security;
alter table public.veil_rooms enable row level security;
alter table public.veil_members enable row level security;
alter table public.veil_communities enable row level security;
alter table public.veil_community_members enable row level security;
alter table public.veil_messages enable row level security;
alter table public.veil_call_signals enable row level security;
alter table public.veil_reports enable row level security;

create policy "veil_device_bindings_all" on public.veil_device_bindings for all using (true) with check (true);
create policy "veil_rooms_all" on public.veil_rooms for all using (true) with check (true);
create policy "veil_members_all" on public.veil_members for all using (true) with check (true);
create policy "veil_communities_all" on public.veil_communities for all using (true) with check (true);
create policy "veil_community_members_all" on public.veil_community_members for all using (true) with check (true);
create policy "veil_messages_all" on public.veil_messages for all using (true) with check (true);
create policy "veil_call_signals_all" on public.veil_call_signals for all using (true) with check (true);
create policy "veil_reports_all" on public.veil_reports for all using (true) with check (true);"""
    }
}

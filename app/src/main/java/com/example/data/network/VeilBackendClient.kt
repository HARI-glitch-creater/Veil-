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
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
                                "Supabase connected and Veil v${com.example.BuildConfig.VERSION_NAME} tables ready (${elapsed}ms)"
                            } else {
                                "Server reachable (${resp.code} · ${elapsed}ms)"
                            },
                            cleartext
                        )
                        config.profile.backendType == BackendType.SUPABASE && resp.code == 404 -> ConnectionTestResult(
                            true, resp.code, elapsed,
                            "Supabase reachable (${elapsed}ms) — run the One-Step SQL script in Supabase SQL Editor to create tables, veil-media bucket, and Realtime",
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
        fun buildBody(useIso: Boolean): String = JSONObject().apply {
            put("device_binding_id", binding.deviceBindingId)
            put("public_key", binding.publicKey)
            put("username", binding.username)
            put("motto", binding.motto)
            put("gender", binding.gender)
            put("pfp_cipher_url", binding.pfpCipherUrl)
            put("updated_at", if (useIso) formatIsoUtc(binding.updatedAt) else binding.updatedAt)
        }.toString()
        postJsonWithTimestampFallback(url, config, mergeDuplicates = true, buildBody = ::buildBody)
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
                    updatedAt = parseTimestampMs(obj, "updated_at")
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
        val now = System.currentTimeMillis()
        fun buildBody(useIso: Boolean): String = JSONObject().apply {
            put("room_code", roomCode)
            put("room_name", roomName)
            put("host_public_key", hostPublicKey)
            put("host_device_id", hostDeviceId)
            put("created_at", if (useIso) formatIsoUtc(now) else now)
        }.toString()
        postJsonWithTimestampFallback(url, config, mergeDuplicates = true, buildBody = ::buildBody)
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
                    createdAt = parseTimestampMs(obj, "created_at")
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
        fun buildBody(useIso: Boolean): String = JSONObject().apply {
            put("id", member.id)
            put("room_code", member.roomCode)
            put("device_binding_id", member.deviceBindingId)
            put("username", member.username)
            put("motto", member.motto)
            put("gender", member.gender)
            put("pfp_cipher_url", member.pfpCipherUrl)
            put("public_key", member.publicKey)
            put("joined_at", if (useIso) formatIsoUtc(member.joinedAt) else member.joinedAt)
        }.toString()
        postJsonWithTimestampFallback(url, config, mergeDuplicates = true, buildBody = ::buildBody)
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
                        joinedAt = parseTimestampMs(o, "joined_at")
                    )
                }.filter { it.publicKey.isNotBlank() }
            }
        }
    }

    /**
     * Exclusive Stealth Room Creator action: removes a member from veil_members and revokes
     * their memberships in all room communities. Strictly URL-encodes room_code and keys.
     */
    suspend fun removeMemberFromRoom(
        config: ResolvedBackendConfig,
        roomCode: String,
        targetPublicKey: String,
        roomCommunityIds: List<String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        val encRoom = URLEncoder.encode(roomCode, "UTF-8")
        val encPub = URLEncoder.encode(targetPublicKey, "UTF-8")
        runCatching {
            val memberDeleteUrl = when (config.profile.backendType) {
                BackendType.SUPABASE ->
                    "$baseUrl/rest/v1/veil_members?room_code=eq.$encRoom&public_key=eq.$encPub"
                BackendType.SELF_HOSTED ->
                    "${buildPathUrl(baseUrl, config.profile.membersPath)}?room_code=$encRoom&public_key=$encPub"
            }
            val memberReq = applyAuthHeaders(Request.Builder().url(memberDeleteUrl).delete(), config).build()
            client.newCall(memberReq).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 404) {
                    error("Failed to remove member (HTTP ${resp.code})")
                }
            }

            // Revoke the removed member's access from any room communities as well.
            for (communityId in roomCommunityIds) {
                val encComm = URLEncoder.encode(communityId, "UTF-8")
                val commMemberDeleteUrl = when (config.profile.backendType) {
                    BackendType.SUPABASE ->
                        "$baseUrl/rest/v1/veil_community_members?community_id=eq.$encComm&member_public_key=eq.$encPub"
                    BackendType.SELF_HOSTED ->
                        "$baseUrl/api/v1/community-members?community_id=$encComm&member_public_key=$encPub"
                }
                runCatching {
                    val req = applyAuthHeaders(Request.Builder().url(commMemberDeleteUrl).delete(), config).build()
                    client.newCall(req).execute().close()
                }
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
        fun buildBody(useIso: Boolean): String = JSONObject().apply {
            put("id", community.id)
            put("room_code", community.roomCode)
            put("name", community.name)
            put("description", community.description)
            put("is_default", community.isDefault)
            put("created_by_public_key", community.createdByPublicKey)
            put("created_at", if (useIso) formatIsoUtc(community.createdAt) else community.createdAt)
        }.toString()
        postJsonWithTimestampFallback(url, config, mergeDuplicates = true, buildBody = ::buildBody)
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
                        createdAt = parseTimestampMs(o, "created_at")
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
        fun buildBody(useIso: Boolean): String = JSONObject().apply {
            put("id", message.id)
            put("room_code", message.roomCode)
            put("channel_type", message.channelType.name)
            put("target_id", message.targetId)
            put("sender_public_key", message.senderPublicKey)
            put("iv", message.iv)
            put("ciphertext", message.ciphertext)
            put("max_views", message.maxViews)
            put("views_consumed", message.viewsConsumed)
            put("created_at", if (useIso) formatIsoUtc(message.createdAt) else message.createdAt)
        }.toString()

        val url = when (config.profile.backendType) {
            BackendType.SUPABASE -> "$baseUrl/rest/v1/veil_messages"
            BackendType.SELF_HOSTED -> buildPathUrl(baseUrl, config.profile.messagesPath)
        }
        val res = postJsonWithTimestampFallback(url, config, mergeDuplicates = false, buildBody = ::buildBody)

        if (res.isSuccess &&
            config.profile.backendType == BackendType.SELF_HOSTED &&
            config.profile.webhookUrl.isNotBlank()
        ) {
            val webhookPayload = buildBody(false)
            validateHttpUrl(config.profile.webhookUrl).onSuccess { webhookUrl ->
                runCatching {
                    val hookReq = applyAuthHeaders(
                        Request.Builder().url(webhookUrl).post(webhookPayload.toRequestBody(jsonMediaType)),
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
                        createdAt = parseTimestampMs(o, "created_at")
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
        fun buildBody(useIso: Boolean): String = JSONObject().apply {
            put("id", signal.id)
            put("room_code", signal.roomCode)
            put("caller_public_key", signal.callerPublicKey)
            put("callee_public_key", signal.calleePublicKey)
            put("signal_type", signal.signalType)
            put("encrypted_payload", signal.encryptedPayload)
            put("created_at", if (useIso) formatIsoUtc(signal.createdAt) else signal.createdAt)
        }.toString()
        postJsonWithTimestampFallback(url, config, mergeDuplicates = false, buildBody = ::buildBody)
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
        val encSinceIso = URLEncoder.encode(formatIsoUtc(sinceTimestamp), "UTF-8")
        val primaryUrl = when (config.profile.backendType) {
            BackendType.SUPABASE ->
                "$baseUrl/rest/v1/veil_call_signals?room_code=eq.$encRoom&callee_public_key=eq.$encKey&created_at=gte.$encSinceIso&order=created_at.asc"
            BackendType.SELF_HOSTED ->
                "$baseUrl/api/v1/call-signals?room_code=$encRoom&callee_public_key=$encKey&since=$sinceTimestamp"
        }

        fun parseSignals(raw: String): List<RemoteCallSignal> {
            val arr = extractJsonArray(raw, "signals")
            return List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                RemoteCallSignal(
                    id = o.optString("id"),
                    roomCode = o.optString("room_code", roomCode),
                    callerPublicKey = o.optString("caller_public_key"),
                    calleePublicKey = o.optString("callee_public_key"),
                    signalType = o.optString("signal_type"),
                    encryptedPayload = o.optString("encrypted_payload"),
                    createdAt = parseTimestampMs(o, "created_at")
                )
            }.filter { it.id.isNotBlank() }
        }

        runCatching {
            val req = applyAuthHeaders(Request.Builder().url(primaryUrl).get(), config).build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    return@runCatching parseSignals(resp.body?.string().orEmpty().trim())
                }
                // Backward-compatible fallback for older Supabase rooms with bigint created_at
                if (config.profile.backendType == BackendType.SUPABASE && resp.code == 400) {
                    val legacyUrl =
                        "$baseUrl/rest/v1/veil_call_signals?room_code=eq.$encRoom&callee_public_key=eq.$encKey&created_at=gte.$sinceTimestamp&order=created_at.asc"
                    val legacyReq = applyAuthHeaders(Request.Builder().url(legacyUrl).get(), config).build()
                    client.newCall(legacyReq).execute().use { legacyResp ->
                        if (!legacyResp.isSuccessful) error("HTTP ${legacyResp.code}")
                        return@runCatching parseSignals(legacyResp.body?.string().orEmpty().trim())
                    }
                }
                error("HTTP ${resp.code}")
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
        val now = System.currentTimeMillis()
        fun buildBody(useIso: Boolean): String = JSONObject().apply {
            put("id", id)
            put("room_code", roomCode)
            put("reporter_public_key", reporterPublicKey)
            put("reported_public_key", reportedPublicKey)
            put("reason", reason)
            put("notes", notes)
            put("created_at", if (useIso) formatIsoUtc(now) else now)
        }.toString()
        postJsonWithTimestampFallback(url, config, mergeDuplicates = false, buildBody = ::buildBody)
    }

    // 8. Encrypted Binary Blob Storage (Media & Encrypted PFPs)
    suspend fun uploadEncryptedPfpBlob(
        config: ResolvedBackendConfig,
        deviceBindingId: String,
        encryptedBytes: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        runCatching {
            val safeDeviceId = deviceBindingId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64).ifBlank { "device" }
            val targetUrl = when {
                config.profile.backendType == BackendType.SUPABASE -> {
                    val bucket = config.profile.supabaseBucket.trim().ifBlank { "veil-media" }
                    "$baseUrl/storage/v1/object/$bucket/pfp/$safeDeviceId.enc"
                }
                config.profile.mediaUploadUrl.isNotBlank() -> {
                    val mediaBase = validateHttpUrl(config.profile.mediaUploadUrl).getOrThrow()
                    "$mediaBase/pfp/$safeDeviceId.enc"
                }
                else -> error("No external media endpoint configured")
            }
            uploadRawBytes(config, targetUrl, encryptedBytes)
        }
    }

    suspend fun uploadEncryptedBlob(
        config: ResolvedBackendConfig,
        roomCode: String,
        blobId: String,
        encryptedBytes: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        runCatching {
            // Hash roomCode into a URL-safe hex folder so symbols (#, &, +, ?, %) never break object paths.
            val safeFolder = safeStorageFolder(roomCode)
            val safeBlobId = blobId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64).ifBlank { "blob" }
            val targetUrl = when {
                config.profile.backendType == BackendType.SUPABASE -> {
                    val bucket = config.profile.supabaseBucket.trim().ifBlank { "veil-media" }
                    "$baseUrl/storage/v1/object/$bucket/$safeFolder/$safeBlobId.enc"
                }
                config.profile.mediaUploadUrl.isNotBlank() -> {
                    val mediaBase = validateHttpUrl(config.profile.mediaUploadUrl).getOrThrow()
                    "$mediaBase/$safeFolder/$safeBlobId.enc"
                }
                else -> error("No external media endpoint configured")
            }
            uploadRawBytes(config, targetUrl, encryptedBytes)
        }
    }

    private fun uploadRawBytes(
        config: ResolvedBackendConfig,
        targetUrl: String,
        encryptedBytes: ByteArray
    ): String {
        val primaryReq = applyAuthHeaders(
            Request.Builder().url(targetUrl).apply {
                if (config.profile.backendType == BackendType.SUPABASE) {
                    post(encryptedBytes.toRequestBody(octetMediaType))
                    header("x-upsert", "true")
                } else {
                    put(encryptedBytes.toRequestBody(octetMediaType))
                }
            },
            config
        ).build()

        client.newCall(primaryReq).execute().use { resp ->
            if (resp.isSuccessful) return targetUrl
            if (config.profile.backendType == BackendType.SUPABASE && resp.code in listOf(400, 405, 409)) {
                // Retry with PUT for Supabase Storage update semantics when object already exists
                val putReq = applyAuthHeaders(
                    Request.Builder().url(targetUrl)
                        .put(encryptedBytes.toRequestBody(octetMediaType))
                        .header("x-upsert", "true"),
                    config
                ).build()
                client.newCall(putReq).execute().use { putResp ->
                    if (putResp.isSuccessful) return targetUrl
                    error("Media upload HTTP ${putResp.code}")
                }
            }
            error("Media upload HTTP ${resp.code}")
        }
    }

    suspend fun downloadEncryptedBlob(
        config: ResolvedBackendConfig,
        blobUrl: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        // Strip any client-side decryption fragment (#k=...&iv=...) before sending HTTP GET
        val cleanUrl = blobUrl.substringBefore('#').trim()
        val validUrl = validateHttpUrl(cleanUrl).getOrElse { return@withContext Result.failure(it) }
        runCatching {
            val primaryReq = applyAuthHeaders(Request.Builder().url(validUrl).get(), config).build()
            client.newCall(primaryReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    return@runCatching resp.body?.bytes() ?: error("Empty blob")
                }
                // Fallback between Supabase authenticated /storage/v1/object/ and /storage/v1/object/public/
                val alternateUrl = when {
                    validUrl.contains("/storage/v1/object/public/") ->
                        validUrl.replaceFirst("/storage/v1/object/public/", "/storage/v1/object/")
                    validUrl.contains("/storage/v1/object/") ->
                        validUrl.replaceFirst("/storage/v1/object/", "/storage/v1/object/public/")
                    else -> null
                }
                if (alternateUrl != null) {
                    val fallbackReq = applyAuthHeaders(Request.Builder().url(alternateUrl).get(), config).build()
                    client.newCall(fallbackReq).execute().use { fbResp ->
                        if (fbResp.isSuccessful) {
                            return@runCatching fbResp.body?.bytes() ?: error("Empty blob")
                        }
                    }
                }
                error("Media download HTTP ${resp.code}")
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
                val encRoom = URLEncoder.encode(roomCode, "UTF-8")
                if (custom.contains("?")) "$custom&room_code=$encRoom" else "$custom?room_code=$encRoom"
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

    private fun postJsonWithTimestampFallback(
        url: String,
        config: ResolvedBackendConfig,
        mergeDuplicates: Boolean,
        buildBody: (useIsoTimestamps: Boolean) -> String
    ): Result<Unit> {
        val preferIso = config.profile.backendType == BackendType.SUPABASE
        val firstRes = postJson(url, buildBody(preferIso), config, mergeDuplicates)
        if (firstRes.isSuccess || !preferIso) return firstRes
        val errMsg = firstRes.exceptionOrNull()?.message.orEmpty()
        if (errMsg.contains("HTTP 400")) {
            // Backward-compatible retry for pre-v1.0.5 Supabase tables using bigint timestamps
            return postJson(url, buildBody(false), config, mergeDuplicates)
        }
        return firstRes
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

    private fun formatIsoUtc(epochMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(epochMs))
    }

    private fun parseTimestampMs(
        obj: JSONObject,
        key: String,
        fallback: Long = System.currentTimeMillis()
    ): Long {
        if (!obj.has(key) || obj.isNull(key)) return fallback
        val raw = obj.opt(key) ?: return fallback
        if (raw is Number) return raw.toLong()
        val str = raw.toString().trim()
        if (str.isEmpty()) return fallback
        str.toLongOrNull()?.let { return it }
        val match = ISO_TIMESTAMP_REGEX.matchEntire(str) ?: return fallback
        val year = match.groupValues[1].toIntOrNull() ?: return fallback
        val month = match.groupValues[2].toIntOrNull() ?: return fallback
        val day = match.groupValues[3].toIntOrNull() ?: return fallback
        val hour = match.groupValues[4].toIntOrNull() ?: return fallback
        val minute = match.groupValues[5].toIntOrNull() ?: return fallback
        val second = match.groupValues[6].toIntOrNull() ?: return fallback
        val frac = match.groupValues[7]
        val millis = if (frac.isEmpty()) 0 else frac.padEnd(3, '0').take(3).toIntOrNull() ?: 0
        val tz = match.groupValues[8]

        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(year, month - 1, day, hour, minute, second)
        cal.set(Calendar.MILLISECOND, millis)
        var epoch = cal.timeInMillis
        if (tz.isNotEmpty() && tz != "Z" && tz != "z") {
            val sign = if (tz.startsWith("-")) -1 else 1
            val digits = tz.substring(1).replace(":", "")
            val tzHours = digits.take(2).toIntOrNull() ?: 0
            val tzMins = if (digits.length >= 4) digits.substring(2, 4).toIntOrNull() ?: 0 else 0
            val offsetMs = sign * ((tzHours * 60L) + tzMins) * 60_000L
            epoch -= offsetMs
        }
        return epoch
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

    private fun safeStorageFolder(roomCode: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("veil-storage:$roomCode".toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
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
        private val ISO_TIMESTAMP_REGEX =
            Regex("^(\\d{4})-(\\d{2})-(\\d{2})[T ](\\d{2}):(\\d{2}):(\\d{2})(?:\\.(\\d+))?(Z|z|[+-]\\d{2}:?\\d{2})?$")

        const val SUPABASE_SQL_SCHEMA = """-- 1. Core Tables
create table if not exists public.veil_device_bindings (
  device_binding_id text primary key,
  public_key text not null,
  username text not null,
  motto text not null,
  gender text not null,
  pfp_cipher_url text,
  updated_at timestamptz default now()
);

create table if not exists public.veil_rooms (
  room_code text primary key,
  room_name text not null,
  host_public_key text not null,
  host_device_id text not null,
  created_at timestamptz default now()
);

create table if not exists public.veil_members (
  id uuid primary key default gen_random_uuid(),
  room_code text references public.veil_rooms(room_code) on delete cascade,
  device_binding_id text not null,
  username text not null,
  motto text not null,
  gender text not null,
  pfp_cipher_url text,
  public_key text not null,
  joined_at timestamptz default now(),
  unique(room_code, device_binding_id)
);

create table if not exists public.veil_communities (
  id uuid primary key default gen_random_uuid(),
  room_code text references public.veil_rooms(room_code) on delete cascade,
  name text not null,
  description text not null,
  is_default boolean not null default false,
  created_by_public_key text not null,
  created_at timestamptz default now()
);

create table if not exists public.veil_community_members (
  community_id uuid references public.veil_communities(id) on delete cascade,
  member_public_key text not null,
  encrypted_community_key text not null,
  primary key (community_id, member_public_key)
);

create table if not exists public.veil_messages (
  id uuid primary key default gen_random_uuid(),
  room_code text references public.veil_rooms(room_code) on delete cascade,
  channel_type text not null,
  target_id text not null,
  sender_public_key text not null,
  iv text not null,
  ciphertext text not null,
  max_views integer not null default 0,
  views_consumed integer not null default 0,
  created_at timestamptz default now()
);

create table if not exists public.veil_call_signals (
  id uuid primary key default gen_random_uuid(),
  room_code text references public.veil_rooms(room_code) on delete cascade,
  caller_public_key text not null,
  callee_public_key text not null,
  signal_type text not null,
  encrypted_payload text not null,
  created_at timestamptz default now()
);

create table if not exists public.veil_reports (
  id uuid primary key default gen_random_uuid(),
  room_code text not null,
  reporter_public_key text not null,
  reported_public_key text not null,
  reason text not null,
  notes text not null default '',
  created_at timestamptz default now()
);

-- 2. Enable Row Level Security
alter table public.veil_device_bindings enable row level security;
alter table public.veil_rooms enable row level security;
alter table public.veil_members enable row level security;
alter table public.veil_communities enable row level security;
alter table public.veil_community_members enable row level security;
alter table public.veil_messages enable row level security;
alter table public.veil_call_signals enable row level security;
alter table public.veil_reports enable row level security;

-- 3. Open RLS Policies (Safe to Re-Run)
drop policy if exists "veil_device_bindings_all" on public.veil_device_bindings;
create policy "veil_device_bindings_all" on public.veil_device_bindings for all using (true) with check (true);

drop policy if exists "veil_rooms_all" on public.veil_rooms;
create policy "veil_rooms_all" on public.veil_rooms for all using (true) with check (true);

drop policy if exists "veil_members_all" on public.veil_members;
create policy "veil_members_all" on public.veil_members for all using (true) with check (true);

drop policy if exists "veil_communities_all" on public.veil_communities;
create policy "veil_communities_all" on public.veil_communities for all using (true) with check (true);

drop policy if exists "veil_community_members_all" on public.veil_community_members;
create policy "veil_community_members_all" on public.veil_community_members for all using (true) with check (true);

drop policy if exists "veil_messages_all" on public.veil_messages;
create policy "veil_messages_all" on public.veil_messages for all using (true) with check (true);

drop policy if exists "veil_call_signals_all" on public.veil_call_signals;
create policy "veil_call_signals_all" on public.veil_call_signals for all using (true) with check (true);

drop policy if exists "veil_reports_all" on public.veil_reports;
create policy "veil_reports_all" on public.veil_reports for all using (true) with check (true);

-- 4. Create Public 'veil-media' Storage Bucket + Object Policies
insert into storage.buckets (id, name, public)
values ('veil-media', 'veil-media', true)
on conflict (id) do update set public = true;

drop policy if exists "veil_media_public_all" on storage.objects;
create policy "veil_media_public_all" on storage.objects
for all using (bucket_id = 'veil-media') with check (bucket_id = 'veil-media');

-- 5. Enable Realtime Publications Safely
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'veil_messages'
  ) then
    alter publication supabase_realtime add table public.veil_messages;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'veil_members'
  ) then
    alter publication supabase_realtime add table public.veil_members;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'veil_call_signals'
  ) then
    alter publication supabase_realtime add table public.veil_call_signals;
  end if;
end $$;"""
    }
}

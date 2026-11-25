package com.example.data.network

import android.util.Base64
import com.example.data.local.AuthType
import com.example.data.local.BackendProfileEntity
import com.example.data.local.BackendType
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

data class RemoteRoomRecord(
    val roomCode: String,
    val hostPublicKey: String,
    val hostHandle: String,
    val createdAt: Long
)

data class RemoteMemberRecord(
    val roomCode: String,
    val memberHandle: String,
    val publicKey: String,
    val joinedAt: Long
)

data class RemoteEncryptedMessage(
    val id: String,
    val roomCode: String,
    val senderPublicKey: String,
    val iv: String,
    val ciphertext: String,
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
            require(scheme == "https" || scheme == "http") {
                "URL must start with https:// or http://"
            }
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
            require(scheme == "wss" || scheme == "ws") {
                "WebSocket URL must start with wss:// or ws://"
            }
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
                return@withContext ConnectionTestResult(
                    success = false,
                    statusCode = 0,
                    latencyMs = 0,
                    message = err.message ?: "Invalid URL"
                )
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
                            success = true,
                            statusCode = resp.code,
                            latencyMs = elapsed,
                            message = if (config.profile.backendType == BackendType.SUPABASE) {
                                "Supabase connected & encrypted tables ready (${elapsed}ms)"
                            } else {
                                "Server reachable (${resp.code} · ${elapsed}ms)"
                            },
                            isCleartextWarning = cleartext
                        )
                        config.profile.backendType == BackendType.SUPABASE && resp.code == 404 -> ConnectionTestResult(
                            success = true,
                            statusCode = resp.code,
                            latencyMs = elapsed,
                            message = "Supabase reachable (${elapsed}ms) — run the SQL schema in Supabase to create Veil tables",
                            isCleartextWarning = cleartext
                        )
                        resp.code == 401 || resp.code == 403 -> ConnectionTestResult(
                            success = false,
                            statusCode = resp.code,
                            latencyMs = elapsed,
                            message = "Authentication rejected (${resp.code}). Check your API key or headers.",
                            isCleartextWarning = cleartext
                        )
                        else -> ConnectionTestResult(
                            success = false,
                            statusCode = resp.code,
                            latencyMs = elapsed,
                            message = "Endpoint returned HTTP ${resp.code} (${elapsed}ms)",
                            isCleartextWarning = cleartext
                        )
                    }
                }
            }.getOrElse { err ->
                ConnectionTestResult(
                    success = false,
                    statusCode = 0,
                    latencyMs = System.currentTimeMillis() - start,
                    message = "Connection failed: ${err.localizedMessage ?: "unreachable"}",
                    isCleartextWarning = cleartext
                )
            }
        }

    suspend fun createRoom(
        config: ResolvedBackendConfig,
        roomCode: String,
        hostPublicKey: String,
        hostHandle: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        val now = System.currentTimeMillis()
        runCatching {
            val roomBody = JSONObject().apply {
                put("room_code", roomCode)
                put("host_public_key", hostPublicKey)
                put("host_handle", hostHandle)
                put("created_at", now)
            }.toString()

            val roomUrl = when (config.profile.backendType) {
                BackendType.SUPABASE -> "$baseUrl/rest/v1/veil_rooms"
                BackendType.SELF_HOSTED -> buildPathUrl(baseUrl, config.profile.roomsPath)
            }

            val reqBuilder = applyAuthHeaders(
                Request.Builder()
                    .url(roomUrl)
                    .post(roomBody.toRequestBody(jsonMediaType)),
                config
            )
            if (config.profile.backendType == BackendType.SUPABASE) {
                reqBuilder.header("Prefer", "resolution=merge-duplicates,return=minimal")
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 409) {
                    error("Room registration failed (HTTP ${resp.code})")
                }
            }

            // Register host public key in veil_members
            registerMember(config, roomCode, hostHandle, hostPublicKey).getOrThrow()
        }
    }

    suspend fun registerMember(
        config: ResolvedBackendConfig,
        roomCode: String,
        memberHandle: String,
        publicKey: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        runCatching {
            val memberBody = JSONObject().apply {
                put("room_code", roomCode)
                put("member_handle", memberHandle)
                put("public_key", publicKey)
                put("joined_at", System.currentTimeMillis())
            }.toString()

            val url = when (config.profile.backendType) {
                BackendType.SUPABASE -> "$baseUrl/rest/v1/veil_members"
                BackendType.SELF_HOSTED -> buildPathUrl(baseUrl, config.profile.membersPath)
            }

            val reqBuilder = applyAuthHeaders(
                Request.Builder()
                    .url(url)
                    .post(memberBody.toRequestBody(jsonMediaType)),
                config
            )
            if (config.profile.backendType == BackendType.SUPABASE) {
                reqBuilder.header("Prefer", "resolution=merge-duplicates,return=minimal")
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 409) {
                    error("Member registration failed (HTTP ${resp.code})")
                }
            }
        }
    }

    suspend fun fetchRoom(
        config: ResolvedBackendConfig,
        roomCode: String
    ): Result<RemoteRoomRecord?> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        val encodedCode = URLEncoder.encode(roomCode, "UTF-8")
        val url = when (config.profile.backendType) {
            BackendType.SUPABASE -> "$baseUrl/rest/v1/veil_rooms?room_code=eq.$encodedCode&limit=1"
            BackendType.SELF_HOSTED -> "${buildPathUrl(baseUrl, config.profile.roomsPath)}?room_code=$encodedCode"
        }
        runCatching {
            val request = applyAuthHeaders(Request.Builder().url(url).get(), config).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val raw = resp.body?.string().orEmpty().trim()
                if (raw.isBlank()) return@use null
                val obj = when {
                    raw.startsWith("[") -> {
                        val arr = JSONArray(raw)
                        if (arr.length() == 0) return@use null
                        arr.getJSONObject(0)
                    }
                    raw.startsWith("{") -> JSONObject(raw)
                    else -> return@use null
                }
                RemoteRoomRecord(
                    roomCode = obj.optString("room_code", roomCode),
                    hostPublicKey = obj.optString("host_public_key", ""),
                    hostHandle = obj.optString("host_handle", "Host"),
                    createdAt = obj.optLong("created_at", System.currentTimeMillis())
                )
            }
        }
    }

    suspend fun fetchMembers(
        config: ResolvedBackendConfig,
        roomCode: String
    ): Result<List<RemoteMemberRecord>> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        val encodedCode = URLEncoder.encode(roomCode, "UTF-8")
        val url = when (config.profile.backendType) {
            BackendType.SUPABASE -> "$baseUrl/rest/v1/veil_members?room_code=eq.$encodedCode&order=joined_at.asc"
            BackendType.SELF_HOSTED -> "${buildPathUrl(baseUrl, config.profile.membersPath)}?room_code=$encodedCode"
        }
        runCatching {
            val request = applyAuthHeaders(Request.Builder().url(url).get(), config).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val raw = resp.body?.string().orEmpty().trim()
                val arr = extractJsonArray(raw, "members")
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    RemoteMemberRecord(
                        roomCode = o.optString("room_code", roomCode),
                        memberHandle = o.optString("member_handle", "Peer"),
                        publicKey = o.optString("public_key", ""),
                        joinedAt = o.optLong("joined_at", System.currentTimeMillis())
                    )
                }.filter { it.publicKey.isNotBlank() }
            }
        }
    }

    /**
     * Pushes ONLY opaque AES-256-GCM encrypted ciphertext + random IV to the database.
     * Zero plaintext content, filenames, locations, or captions ever touch the wire or DB table.
     */
    suspend fun sendEncryptedMessage(
        config: ResolvedBackendConfig,
        message: RemoteEncryptedMessage
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        runCatching {
            val payload = JSONObject().apply {
                put("id", message.id)
                put("room_code", message.roomCode)
                put("sender_public_key", message.senderPublicKey)
                put("iv", message.iv)
                put("ciphertext", message.ciphertext)
                put("created_at", message.createdAt)
            }.toString()

            val url = when (config.profile.backendType) {
                BackendType.SUPABASE -> "$baseUrl/rest/v1/veil_messages"
                BackendType.SELF_HOSTED -> buildPathUrl(baseUrl, config.profile.messagesPath)
            }

            val reqBuilder = applyAuthHeaders(
                Request.Builder()
                    .url(url)
                    .post(payload.toRequestBody(jsonMediaType)),
                config
            )
            if (config.profile.backendType == BackendType.SUPABASE) {
                reqBuilder.header("Prefer", "resolution=ignore-duplicates,return=minimal")
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 409) {
                    error("Dispatch failed (HTTP ${resp.code})")
                }
            }

            // Optional outbound webhook notification (also ciphertext-only, zero plaintext)
            if (config.profile.backendType == BackendType.SELF_HOSTED &&
                config.profile.webhookUrl.isNotBlank()
            ) {
                validateHttpUrl(config.profile.webhookUrl).onSuccess { webhookUrl ->
                    runCatching {
                        val hookReq = applyAuthHeaders(
                            Request.Builder()
                                .url(webhookUrl)
                                .post(payload.toRequestBody(jsonMediaType)),
                            config
                        ).build()
                        client.newCall(hookReq).execute().close()
                    }
                }
            }
        }
    }

    suspend fun fetchEncryptedMessages(
        config: ResolvedBackendConfig,
        roomCode: String
    ): Result<List<RemoteEncryptedMessage>> = withContext(Dispatchers.IO) {
        val baseUrl = validateHttpUrl(config.profile.baseUrl).getOrElse { return@withContext Result.failure(it) }
        val encodedCode = URLEncoder.encode(roomCode, "UTF-8")
        val url = when (config.profile.backendType) {
            BackendType.SUPABASE -> "$baseUrl/rest/v1/veil_messages?room_code=eq.$encodedCode&order=created_at.asc"
            BackendType.SELF_HOSTED -> "${buildPathUrl(baseUrl, config.profile.messagesPath)}?room_code=$encodedCode"
        }
        runCatching {
            val request = applyAuthHeaders(Request.Builder().url(url).get(), config).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val raw = resp.body?.string().orEmpty().trim()
                val arr = extractJsonArray(raw, "messages")
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    RemoteEncryptedMessage(
                        id = o.optString("id"),
                        roomCode = o.optString("room_code", roomCode),
                        senderPublicKey = o.optString("sender_public_key"),
                        iv = o.optString("iv"),
                        ciphertext = o.optString("ciphertext"),
                        createdAt = o.optLong("created_at", System.currentTimeMillis())
                    )
                }.filter { it.id.isNotBlank() && it.iv.isNotBlank() && it.ciphertext.isNotBlank() }
            }
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
                Request.Builder()
                    .url(targetUrl)
                    .apply {
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
            val request = applyAuthHeaders(Request.Builder().url(validUrl).get(), config).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("Media download HTTP ${resp.code}")
                resp.body?.bytes() ?: error("Empty encrypted blob")
            }
        }
    }

    /**
     * Optional real-time WebSocket listener for self-hosted servers that expose a wss:// relay.
     */
    fun observeWebSocketMessages(
        config: ResolvedBackendConfig,
        roomCode: String
    ): Flow<RemoteEncryptedMessage> = callbackFlow {
        val wsUrl = config.profile.webSocketUrl.trim()
        if (config.profile.backendType != BackendType.SELF_HOSTED ||
            wsUrl.isBlank() ||
            validateWebSocketUrl(wsUrl).isFailure
        ) {
            close()
            return@callbackFlow
        }

        val fullUrl = if (wsUrl.contains("?")) "$wsUrl&room_code=$roomCode" else "$wsUrl?room_code=$roomCode"
        val req = applyAuthHeaders(Request.Builder().url(fullUrl), config).build()

        val ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val o = JSONObject(text)
                    val msg = RemoteEncryptedMessage(
                        id = o.optString("id"),
                        roomCode = o.optString("room_code", roomCode),
                        senderPublicKey = o.optString("sender_public_key"),
                        iv = o.optString("iv"),
                        ciphertext = o.optString("ciphertext"),
                        createdAt = o.optLong("created_at", System.currentTimeMillis())
                    )
                    if (msg.id.isNotBlank() && msg.iv.isNotBlank() && msg.ciphertext.isNotBlank()) {
                        trySend(msg)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Polling fallback continues seamlessly; close stream quietly
                close()
            }
        })

        awaitClose {
            ws.close(1000, "Room closed")
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
                    AuthType.BEARER -> {
                        if (apiKey.isNotBlank()) {
                            builder.header("Authorization", "Bearer $apiKey")
                        }
                    }
                    AuthType.API_KEY_HEADER -> {
                        val headerName = config.profile.authHeaderName.trim().ifBlank { "X-API-Key" }
                        if (apiKey.isNotBlank()) {
                            builder.header(headerName, apiKey)
                        }
                    }
                    AuthType.BASIC -> {
                        if (apiKey.isNotBlank()) {
                            val encoded = Base64.encodeToString(
                                apiKey.toByteArray(Charsets.UTF_8),
                                Base64.NO_WRAP
                            )
                            builder.header("Authorization", "Basic $encoded")
                        }
                    }
                }
                config.customHeaders.forEach { (k, v) ->
                    val cleanKey = k.trim()
                    if (cleanKey.isNotBlank() && v.isNotBlank()) {
                        builder.header(cleanKey, v.trim())
                    }
                }
            }
        }
        return builder
    }

    private fun buildPathUrl(baseUrl: String, path: String): String {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return "$baseUrl$normalizedPath"
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
        const val SUPABASE_SQL_SCHEMA = """-- Run once in your Supabase SQL Editor
-- All message contents, files & locations are encrypted on-device before insert.
-- Even the database owner only sees ciphertext + IV.

create table if not exists public.veil_rooms (
  room_code text primary key,
  host_public_key text not null,
  host_handle text not null,
  created_at bigint not null
);

create table if not exists public.veil_members (
  room_code text not null references public.veil_rooms(room_code) on delete cascade,
  public_key text not null,
  member_handle text not null,
  joined_at bigint not null,
  primary key (room_code, public_key)
);

create table if not exists public.veil_messages (
  id text primary key,
  room_code text not null references public.veil_rooms(room_code) on delete cascade,
  sender_public_key text not null,
  iv text not null,
  ciphertext text not null,
  created_at bigint not null
);

alter table public.veil_rooms enable row level security;
alter table public.veil_members enable row level security;
alter table public.veil_messages enable row level security;

create policy "veil_rooms_anon" on public.veil_rooms for all using (true) with check (true);
create policy "veil_members_anon" on public.veil_members for all using (true) with check (true);
create policy "veil_messages_anon" on public.veil_messages for all using (true) with check (true);"""
    }
}

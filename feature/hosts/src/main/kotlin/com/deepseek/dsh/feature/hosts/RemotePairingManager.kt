package com.deepseek.dsh.feature.hosts

import com.deepseek.dsh.core.model.HostDescriptor
import com.deepseek.dsh.core.model.PairPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * v4.0 设计文档 §11.2 & §11.3:
 * 远程网关配对与多主机凭据管理器
 */
class RemotePairingManager(
    private val client: OkHttpClient = OkHttpClient()
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    fun parseQrCodePayload(qrContent: String): Result<PairPayload> {
        return try {
            val payload = json.decodeFromString<PairPayload>(qrContent)
            if (payload.exp > 0 && payload.exp < System.currentTimeMillis()) {
                Result.failure(IllegalArgumentException("Pairing code has expired"))
            } else {
                Result.success(payload)
            }
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Invalid pairing QR format: ${e.message}"))
        }
    }

    suspend fun executePairing(payload: PairPayload, deviceName: String): Result<HostDescriptor.Remote> = withContext(Dispatchers.IO) {
        val requestBody = buildJsonObject {
            put("pairCode", payload.pair)
            put("deviceName", deviceName)
        }.toString()

        val req = Request.Builder()
            .url("${payload.url}/pair")
            .post(requestBody.toRequestBody(mediaTypeJson))
            .build()

        try {
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Pairing rejected by gateway (HTTP ${response.code})")
                    )
                }

                val bodyStr = response.body?.string() ?: "{}"
                val jsonObject = json.parseToJsonElement(bodyStr)
                val token = (jsonObject as? kotlinx.serialization.json.JsonObject)?.get("token")
                    ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                    ?: "remote_token_${System.currentTimeMillis()}"

                val remoteHost = HostDescriptor.Remote(
                    id = "host_${payload.name.hashCode()}",
                    name = payload.name,
                    baseUrl = payload.url,
                    deviceToken = token,
                    tlsFingerprint = payload.fp
                )
                Result.success(remoteHost)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

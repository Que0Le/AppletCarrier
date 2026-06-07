package com.example.applet_carrier.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

sealed interface ProxmoxResult<out T> {
    data class Ok<T>(val value: T) : ProxmoxResult<T>
    data class Err(val message: String) : ProxmoxResult<Nothing>
}

/**
 * Proxmox VE REST client using **API-token** auth (`Authorization: PVEAPIToken=…`). No
 * ticket/cookie/CSRF needed. TLS verification is disabled **for this client only** (Proxmox
 * ships a self-signed cert and is usually reached by IP) — trust-all + hostname verifier
 * scoped to this OkHttpClient, not the JVM.
 *
 * @param tokenId the token id, `user@realm!tokenid` (as shown in Proxmox).
 * @param secret  the token secret (the UUID shown once at creation).
 */
class ProxmoxClient(serverUrl: String, tokenId: String, secret: String) {

    private val baseUrl = serverUrl.trim().trimEnd('/') + "/api2/json"
    private val authValue = "PVEAPIToken=${tokenId.trim()}=${secret.trim()}"
    private val client = insecureClient()

    /** Cheap call to validate the server URL + token. */
    suspend fun testConnection(): ProxmoxResult<Unit> = withContext(Dispatchers.IO) {
        runCatchingResult { getOrThrow("/version"); Unit }
    }

    /** All VMs + containers across every node, sorted by VMID. */
    suspend fun listResources(): ProxmoxResult<List<ProxmoxResource>> = withContext(Dispatchers.IO) {
        runCatchingResult {
            val nodes = parseNodeNames(getOrThrow("/nodes"))
            buildList {
                for (node in nodes) {
                    addAll(parseResources(getOrThrow("/nodes/$node/qemu"), node, ResourceType.VM))
                    addAll(parseResources(getOrThrow("/nodes/$node/lxc"), node, ResourceType.CT))
                }
            }.sortedBy { it.vmid }
        }
    }

    /** POST a status action (start/shutdown/stop/reboot/reset) with an empty body. */
    suspend fun action(resource: ProxmoxResource, action: String): ProxmoxResult<Unit> =
        withContext(Dispatchers.IO) {
            runCatchingResult {
                val path = "/nodes/${resource.node}/${resource.type.apiPath}/${resource.vmid}/status/$action"
                client.newCall(request(path).post(EMPTY_BODY).build()).execute().use { resp ->
                    check(resp.isSuccessful) { "HTTP ${resp.code} ${resp.message}" }
                }
                Unit
            }
        }

    private fun getOrThrow(path: String): String =
        client.newCall(request(path).get().build()).execute().use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code} ${resp.message}" }
            resp.body?.string().orEmpty()
        }

    private fun request(path: String): Request.Builder =
        Request.Builder().url("$baseUrl$path").header("Authorization", authValue)

    /** Release this client's thread + connection pools. Call on logout. */
    fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private inline fun <T> runCatchingResult(block: () -> T): ProxmoxResult<T> =
        try {
            ProxmoxResult.Ok(block())
        } catch (e: Exception) {
            ProxmoxResult.Err(e.message ?: e::class.simpleName ?: "Request failed")
        }

    companion object {
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)

        private fun insecureClient(): OkHttpClient {
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
            return OkHttpClient.Builder()
                .sslSocketFactory(ssl.socketFactory, trustAll)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .build()
        }
    }
}

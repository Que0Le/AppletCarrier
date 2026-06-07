package com.example.applet_carrier.platform

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A VM (qemu) or container (lxc) as shown in the table. */
enum class ResourceType(val apiPath: String, val label: String) {
    VM("qemu", "VM"),
    CT("lxc", "CT"),
}

data class ProxmoxResource(
    val vmid: Long,
    val name: String,
    val type: ResourceType,
    val node: String,
    val status: String,        // running / stopped / paused
    val cpuPercent: Double,
    val memUsedMb: Long,
    val memTotalMb: Long,
)

private val proxmoxJson = Json { ignoreUnknownKeys = true }
private const val BYTES_PER_MB = 1_048_576L

/** Unwrap the `{"data": …}` envelope every Proxmox response uses. */
internal fun proxmoxData(body: String): JsonElement? =
    runCatching { proxmoxJson.parseToJsonElement(body).jsonObject["data"] }.getOrNull()

/** Parse `GET /nodes` → the `node` name of each entry. */
internal fun parseNodeNames(body: String): List<String> =
    (proxmoxData(body) as? JsonArray)
        ?.mapNotNull { it.jsonObject["node"]?.jsonPrimitive?.contentOrNull }
        ?: emptyList()

/** Parse `qmpstatus` from `GET …/qemu/{vmid}/status/current` (e.g. "running"/"paused"). */
internal fun parseQmpStatus(body: String): String? =
    (proxmoxData(body) as? JsonObject)?.get("qmpstatus")?.jsonPrimitive?.contentOrNull

/** Parse `GET /nodes/{node}/{qemu|lxc}` into resources (cpu→%, mem bytes→MB). */
internal fun parseResources(body: String, node: String, type: ResourceType): List<ProxmoxResource> {
    val array = proxmoxData(body) as? JsonArray ?: return emptyList()
    return array.mapNotNull { element ->
        val o = element.jsonObject
        val vmid = o["vmid"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
        ProxmoxResource(
            vmid = vmid,
            name = o["name"]?.jsonPrimitive?.contentOrNull ?: vmid.toString(),
            type = type,
            node = node,
            // A paused VM still has status="running" (qemu process alive); the real paused
            // state is in qmpstatus, so prefer it when present.
            status = o["qmpstatus"]?.jsonPrimitive?.contentOrNull
                ?: o["status"]?.jsonPrimitive?.contentOrNull
                ?: "unknown",
            cpuPercent = (o["cpu"]?.jsonPrimitive?.doubleOrNull ?: 0.0) * 100.0,
            memUsedMb = (o["mem"]?.jsonPrimitive?.longOrNull ?: 0L) / BYTES_PER_MB,
            memTotalMb = (o["maxmem"]?.jsonPrimitive?.longOrNull ?: 0L) / BYTES_PER_MB,
        )
    }
}

package com.example.applet_carrier.platform

import com.example.applet_carrier.api.ConfigStore
import com.example.applet_carrier.api.StateStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * File-backed key/value store. Values are held in memory as strings and persisted as a
 * flat JSON object on [flush]. Implements both [StateStore] and [ConfigStore] — the
 * concern is decided by which file it points at (AGENTS.md §5).
 *
 * Uses only the kotlinx-serialization-json runtime (no compiler plugin) via JsonObject,
 * so there are no generated serializers to maintain.
 */
class JsonStore(private val file: File) : StateStore, ConfigStore {

    private val data = linkedMapOf<String, String>()

    init {
        load()
    }

    private fun load() {
        try {
            if (file.exists()) {
                val root = Json.parseToJsonElement(file.readText()).jsonObject
                root.forEach { (key, value) -> data[key] = value.jsonPrimitive.content }
            }
        } catch (_: Exception) {
            // Corrupt or unreadable file → start from empty defaults.
            data.clear()
        }
    }

    override fun getString(key: String, default: String): String = data[key] ?: default
    override fun getInt(key: String, default: Int): Int = data[key]?.toIntOrNull() ?: default
    override fun getBoolean(key: String, default: Boolean): Boolean =
        data[key]?.toBooleanStrictOrNull() ?: default

    override fun putString(key: String, value: String) { data[key] = value }
    override fun putInt(key: String, value: Int) { data[key] = value.toString() }
    override fun putBoolean(key: String, value: Boolean) { data[key] = value.toString() }

    override fun flush() {
        val obj: JsonObject = buildJsonObject {
            data.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
        }
        file.parentFile?.mkdirs()
        file.writeText(obj.toString())
    }
}

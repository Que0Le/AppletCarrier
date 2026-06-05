package com.example.applet_carrier.api

/**
 * Minimal persisted key/value store. Values are stored as strings on disk (JSON);
 * typed accessors convert on read/write. Implementations are file-backed (see the
 * jvm `JsonStore`). Call [flush] to write pending changes to disk.
 *
 * Kept intentionally tiny so the on-disk format stays trivial and the abstraction
 * can later be swapped (e.g. kotlinx.serialization typed configs) without touching
 * applets. See AGENTS.md §5.
 */
interface KeyValueStore {
    fun getString(key: String, default: String): String
    fun getInt(key: String, default: Int): Int
    fun getBoolean(key: String, default: Boolean): Boolean

    fun putString(key: String, value: String)
    fun putInt(key: String, value: Int)
    fun putBoolean(key: String, value: Boolean)

    /** Persist current contents to disk. */
    fun flush()
}

/** Per-applet transient state (scroll position, selection, …). File: `<id>-state.json`. */
interface StateStore : KeyValueStore

/** Per-applet configuration. File: `<id>-config.json`. */
interface ConfigStore : KeyValueStore

package com.adrianrusu.pandawave.core.media.adapter.playback

import android.content.SharedPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaSessionPlaybackResumptionStoreTest {
    @Test
    fun `store persists stable media ids instead of capability urls`() {
        val store = MediaSessionPlaybackResumptionStore(InMemorySharedPreferences())

        store.save(
            mediaIds = listOf("track-1", "https://cdn.example/should-not-be-required", "track-3"),
            startIndex = 2,
            positionMillis = 12_000L
        )

        val loaded = store.load()
        assertEquals(listOf("track-1", "https://cdn.example/should-not-be-required", "track-3"), loaded?.mediaIds)
        assertEquals(2, loaded?.startIndex)
        assertEquals(12_000L, loaded?.positionMillis)
    }

    @Test
    fun `blank queues are not persisted`() {
        val store = MediaSessionPlaybackResumptionStore(InMemorySharedPreferences())

        store.save(mediaIds = listOf(" ", ""), startIndex = 0)

        assertNull(store.load())
    }

    @Test
    fun `position updates keep the stored queue`() {
        val store = MediaSessionPlaybackResumptionStore(InMemorySharedPreferences())
        store.save(mediaIds = listOf("track-1"), startIndex = 0, positionMillis = 0L)

        store.savePosition(4_500L)

        assertEquals(4_500L, store.load()?.positionMillis)
        assertEquals(listOf("track-1"), store.load()?.mediaIds)
    }
}

private class InMemorySharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST")
        (values[key] as? MutableSet<String>) ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            pending[key!!] = value
            return this
        }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            pending[key!!] = values
            return this
        }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            pending[key!!] = value
            return this
        }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            pending[key!!] = value
            return this
        }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            pending[key!!] = value
            return this
        }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            pending[key!!] = value
            return this
        }
        override fun remove(key: String?): SharedPreferences.Editor {
            pending[key!!] = REMOVED
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            values.clear()
            return this
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            pending.forEach { (key, value) ->
                if (value === REMOVED) values.remove(key) else values[key] = value
            }
            pending.clear()
        }
    }

    private companion object {
        val REMOVED = Any()
    }
}

package com.finalis.mobile.app

import android.content.SharedPreferences
import kotlin.test.Test
import kotlin.test.assertEquals

class RpcSettingsRepositoryTest {


    @Test
    fun `load settings appends builtin fallback endpoints`() {
        val preferences = FakeSharedPreferences()
        val repository = RpcSettingsRepository(preferences)
        val settings = repository.loadSettings()

        assertEquals(
            listOf(
                RpcEndpoint(normalizeRpcUrl(BuildConfig.LIGHTSERVER_RPC_URL)),
                RpcEndpoint("http://212.58.103.170:19444/rpc"),
            ),
            settings.savedEndpoints,
        )
        assertEquals(RpcEndpoint(normalizeRpcUrl(BuildConfig.LIGHTSERVER_RPC_URL)), settings.activeEndpoint)
    }
    @Test
    fun `load settings ignores malformed persisted endpoints`() {
        val preferences = FakeSharedPreferences()
        preferences.edit()
            .putString(
                "saved_endpoints",
                "https://good.example/rpc\nnot-a-url\nhttps://good.example/rpc/\nftp://bad.example",
            )
            .putString("active_endpoint", "not-a-url")
            .commit()

        val repository = RpcSettingsRepository(preferences)
        val settings = repository.loadSettings()

        assertEquals(
            listOf(
                RpcEndpoint("https://good.example/rpc"),
                RpcEndpoint(normalizeRpcUrl(BuildConfig.LIGHTSERVER_RPC_URL)),
                RpcEndpoint("http://212.58.103.170:19444/rpc"),
            ),
            settings.savedEndpoints,
        )
        assertEquals(RpcEndpoint("https://good.example/rpc"), settings.activeEndpoint)
    }
}

private class FakeSharedPreferences : SharedPreferences {
    private val values = linkedMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: Set<String>?): MutableSet<String>? =
        ((values[key] as? Set<String>) ?: defValues)?.toMutableSet()

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor(values)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private class Editor(
        private val target: MutableMap<String, Any?>,
    ) : SharedPreferences.Editor {
        private val pending = linkedMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = values?.toSet()
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = value
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            pending[key.orEmpty()] = RemovedValue
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clear = true
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) target.clear()
            pending.forEach { (key, value) ->
                if (value === RemovedValue) {
                    target.remove(key)
                } else {
                    target[key] = value
                }
            }
        }

        private companion object {
            val RemovedValue = Any()
        }
    }
}

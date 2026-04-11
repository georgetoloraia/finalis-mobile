package com.finalis.mobile.data.wallet

import android.content.SharedPreferences
import com.finalis.mobile.core.model.SubmittedTransactionRecord
import com.finalis.mobile.core.model.WalletOutPoint
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidSubmittedTransactionStoreTest {
    @Test
    fun `persists and restores consumed inputs`() {
        val preferences = FakeSharedPreferences()
        val store = AndroidSubmittedTransactionStore(preferences)
        val record = SubmittedTransactionRecord(
            txid = "aa".repeat(32),
            recipientAddress = "sc1recipient",
            amountUnits = 10L,
            requestedFeeUnits = 1L,
            appliedFeeUnits = 1L,
            changeUnits = 0L,
            createdAtEpochMillis = 100L,
            consumedInputs = listOf(
                WalletOutPoint("bb".repeat(32), 0),
                WalletOutPoint("cc".repeat(32), 1),
            ),
        )

        store.save(record)

        assertEquals(listOf(record), store.list())
    }

    @Test
    fun `loads old records without consumed inputs`() {
        val preferences = FakeSharedPreferences()
        val txid = "aa".repeat(32)
        preferences.edit()
            .putStringSet("submitted_txids", mutableSetOf(txid))
            .putString("submitted.$txid.recipient", "sc1recipient")
            .putLong("submitted.$txid.amount", 10L)
            .putLong("submitted.$txid.requested_fee", 1L)
            .putLong("submitted.$txid.applied_fee", 1L)
            .putLong("submitted.$txid.change", 0L)
            .putLong("submitted.$txid.created_at", 100L)
            .apply()

        val store = AndroidSubmittedTransactionStore(preferences)
        val loaded = store.list().single()

        assertEquals(emptyList(), loaded.consumedInputs)
    }

    @Test
    fun `decode consumed inputs ignores malformed values`() {
        val decoded = decodeConsumedInputs(
            setOf(
                "${"aa".repeat(32)}:0",
                "bad",
                "${"zz".repeat(32)}:1",
                "${"bb".repeat(32)}:-1",
            ),
        )

        assertEquals(
            listOf(WalletOutPoint("aa".repeat(32), 0)),
            decoded,
        )
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

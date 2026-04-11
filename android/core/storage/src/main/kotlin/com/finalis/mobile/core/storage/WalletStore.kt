package com.finalis.mobile.core.storage

import com.finalis.mobile.core.model.ImportedWalletRecord

interface WalletStore {
    fun load(): ImportedWalletRecord?
    fun save(record: ImportedWalletRecord)
    fun clear()
}

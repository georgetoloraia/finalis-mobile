package com.finalis.mobile.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.finalis.mobile.core.model.ImportedWalletRecord

@Composable
fun ReceiveSection(
    wallet: ImportedWalletRecord,
    onCopyAddress: () -> Unit,
    onShareAddress: () -> Unit,
) {
    WalletPanel(
        title = "Receive",
        subtitle = "Primary wallet address and QR.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ReceiveQrCard(address = wallet.walletProfile.address.value)
            CopyableCodeBlock(
                text = wallet.walletProfile.address.value,
                onCopy = onCopyAddress,
                onShare = onShareAddress,
            )
        }
    }
}

package com.finalis.mobile.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finalis.mobile.core.model.BroadcastResult

@Composable
fun SendSection(
    finalizedBalanceUnits: Long?,
    spendableUnits: Long?,
    reservedUnits: Long?,
    recipientAddress: String,
    amountUnitsInput: String,
    errorMessage: String?,
    previewState: SendPreviewState?,
    broadcastResult: BroadcastResult?,
    isSending: Boolean,
    sendBlockedMessage: String?,
    primaryAddress: String,
    onRecipientAddressChange: (String) -> Unit,
    onAmountUnitsChange: (String) -> Unit,
    onNormalizeAmountUnitsInput: () -> Unit,
    onPrepareTransaction: () -> Unit,
    onBroadcastTransaction: () -> Unit,
    onCopyPrimaryAddress: () -> Unit,
) {
    WalletPanel(
        title = "Send",
        subtitle = "Sign locally from finalized funds and submit to lightserver relay.",
    ) {
        if (finalizedBalanceUnits != null || spendableUnits != null) {
            finalizedBalanceUnits?.let { finalizedBalance ->
                LabelValue(
                    label = "Finalized balance",
                    value = formatFinalisAmountLabel(finalizedBalance),
                    emphasize = true,
                )
            }
            spendableUnits?.let { spendable ->
                LabelValue(
                    label = "Currently sendable",
                    value = formatFinalisAmountLabel(spendable),
                )
            }
            if ((reservedUnits ?: 0L) > 0L) {
                ValueRow(
                    title = "Temporary lock",
                    detail = "${formatFinalisAmountLabel(reservedUnits ?: 0L)} are reserved until finalized change becomes visible.",
                    tone = StatusTone.Warning,
                )
            }
            SectionDivider()
        }
        OutlinedTextField(
            value = recipientAddress,
            onValueChange = onRecipientAddressChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Recipient address") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
        )
        OutlinedTextField(
            value = amountUnitsInput,
            onValueChange = onAmountUnitsChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        onNormalizeAmountUnitsInput()
                    }
                },
            label = { Text("Amount (FLS)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        if (errorMessage != null) {
            StatusChip(
                text = errorMessage,
                tone = StatusTone.Danger,
            )
        }
        if (sendBlockedMessage != null) {
            ValueRow(
                title = "Sending blocked",
                detail = sendBlockedMessage,
                tone = StatusTone.Warning,
            )
        }
        Button(
            enabled = !isSending && sendBlockedMessage == null,
            onClick = onPrepareTransaction,
        ) {
            Text("Prepare transaction")
        }

        if (previewState != null && sendBlockedMessage == null) {
            SectionDivider()
            ConfirmationPanel(
                previewState = previewState,
                primaryAddress = primaryAddress,
                onCopyPrimaryAddress = onCopyPrimaryAddress,
                isSending = isSending,
                onBroadcast = onBroadcastTransaction,
            )
        }

        if (broadcastResult != null) {
            StatusChip(
                text = if (broadcastResult.accepted) {
                    "Submission accepted ${broadcastResult.txid ?: ""}".trim()
                } else {
                    "Broadcast rejected ${broadcastResult.errorCode?.name ?: ""}".trim()
                },
                tone = if (broadcastResult.accepted) StatusTone.Positive else StatusTone.Danger,
            )
        }
    }
}

@Composable
private fun ConfirmationPanel(
    previewState: SendPreviewState?,
    primaryAddress: String,
    onCopyPrimaryAddress: () -> Unit,
    isSending: Boolean,
    onBroadcast: () -> Unit,
) {
    if (previewState == null) return
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Preview before broadcast",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(text = "${previewState.inputCount} inputs")
                StatusChip(text = "${previewState.outputCount} outputs")
            }
            LabelValue(label = "Recipient", value = previewState.recipientAddress, monospace = true)
            LabelValue(label = "Amount", value = formatFinalisAmountLabel(previewState.amountUnits))
            LabelValue(label = "Fee", value = formatFinalisAmountLabel(previewState.feeUnits))
            LabelValue(label = "Total spend", value = formatFinalisAmountLabel(previewState.totalDeductionUnits))
            previewState.availableAfterReservationUnits?.let { availableAfterReservation ->
                LabelValue(
                    label = "Sendable after submission",
                    value = formatFinalisAmountLabel(availableAfterReservation),
                    emphasize = true,
                )
            }
            previewState.reservedAfterBroadcastUnits?.let { reservedAfterBroadcast ->
                LabelValue(
                    label = "Reserved after submission",
                    value = formatFinalisAmountLabel(reservedAfterBroadcast),
                )
            }
            if (previewState.reservedByThisTransactionUnits > previewState.totalDeductionUnits) {
                EmptyHint(
                    text = "${formatFinalisAmountLabel(previewState.reservedByThisTransactionUnits)} of consumed inputs stay reserved until finalized change returns.",
                )
            }
            LabelValue(
                label = "Change",
                value = if (previewState.changeUnits > 0) {
                    formatFinalisAmountLabel(previewState.changeUnits)
                } else {
                    "No change"
                },
            )
            if (previewState.changeUnits > 0) {
                CopyableCodeBlock(
                    text = primaryAddress,
                    onCopy = onCopyPrimaryAddress,
                )
            }
            LabelValue(label = "Txid", value = previewState.txid, monospace = true)
            Button(
                enabled = !isSending,
                onClick = onBroadcast,
            ) {
                Text(if (isSending) "Submitting…" else "Submit transaction")
            }
        }
    }
}

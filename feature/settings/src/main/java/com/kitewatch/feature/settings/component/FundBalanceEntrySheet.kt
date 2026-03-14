package com.kitewatch.feature.settings.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kitewatch.domain.model.FundEntryType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.feature.settings.SettingsIntent
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FundBalanceEntrySheet(
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    isSaving: Boolean = false,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(FundEntryType.DEPOSIT) }

    ModalBottomSheet(
        onDismissRequest = { onIntent(SettingsIntent.DismissFundEntrySheet) },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Add Fund Entry", style = MaterialTheme.typography.titleMedium)

            // Entry type chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FundEntryType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(type.toLabel()) },
                    )
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = errorMessage != null,
                supportingText = errorMessage?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
            )

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = { onIntent(SettingsIntent.DismissFundEntrySheet) }) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val rupees = amountText.toDoubleOrNull() ?: return@Button
                        val paisa = Paisa((rupees * 100).toLong())
                        onIntent(
                            SettingsIntent.AddFundEntry(
                                amount = paisa,
                                date = LocalDate.now(),
                                note = note.ifBlank { null },
                                entryType = selectedType,
                            ),
                        )
                    },
                    enabled = !isSaving && amountText.isNotBlank(),
                ) {
                    Text(if (isSaving) "Saving…" else "Save")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun FundEntryType.toLabel(): String =
    when (this) {
        FundEntryType.DEPOSIT -> "Deposit"
        FundEntryType.WITHDRAWAL -> "Withdrawal"
        FundEntryType.DIVIDEND -> "Dividend"
        FundEntryType.MISC_ADJUSTMENT -> "Misc"
    }

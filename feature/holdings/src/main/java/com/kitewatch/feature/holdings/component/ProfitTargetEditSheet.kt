package com.kitewatch.feature.holdings.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kitewatch.domain.model.ProfitTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfitTargetEditSheet(
    stockCode: String,
    currentDisplay: String,
    onDismiss: () -> Unit,
    onConfirm: (ProfitTarget) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var inputText by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "Edit profit target",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stockCode,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Text(
                text = "Current: $currentDisplay",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = { raw ->
                    inputText = raw
                    validationError = null
                },
                label = { Text("Profit target (%)") },
                placeholder = { Text("e.g. 5.0") },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = validationError != null,
                supportingText =
                    validationError?.let {
                        { Text(text = it, color = MaterialTheme.colorScheme.error) }
                    },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val result = validateAndBuild(inputText)
                    when {
                        result.isFailure -> validationError = result.exceptionOrNull()?.message
                        else -> onConfirm(result.getOrThrow())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel")
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Parses [input] as a percentage string (e.g. "5.0" → 500 bps) and returns
 * a [ProfitTarget.Percentage]. Returns failure with a human-readable message
 * on invalid or negative input.
 */
private fun validateAndBuild(input: String): Result<ProfitTarget.Percentage> {
    val trimmed = input.trim()
    return when {
        trimmed.isEmpty() -> Result.failure(IllegalArgumentException("Please enter a value"))
        else -> {
            val parsed = trimmed.toDoubleOrNull()
            when {
                parsed == null -> Result.failure(IllegalArgumentException("Enter a valid number"))
                parsed < 0.0 -> Result.failure(IllegalArgumentException("Target must be 0% or greater"))
                else ->
                    runCatching { ProfitTarget.Percentage((parsed * 100).toInt()) }
                        .recoverCatching { throw IllegalArgumentException("Target must be 0% or greater") }
            }
        }
    }
}

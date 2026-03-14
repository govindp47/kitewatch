package com.kitewatch.feature.portfolio.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kitewatch.feature.portfolio.model.AlertSeverityUi
import com.kitewatch.feature.portfolio.model.AlertUiModel
import com.kitewatch.ui.component.AlertBanner
import com.kitewatch.ui.component.AlertType

@Composable
internal fun PortfolioAlertRow(
    alert: AlertUiModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertBanner(
        message = alert.message,
        type = alert.severity.toAlertType(),
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

private fun AlertSeverityUi.toAlertType(): AlertType =
    when (this) {
        AlertSeverityUi.CRITICAL -> AlertType.Error
        AlertSeverityUi.WARNING -> AlertType.Warning
        AlertSeverityUi.INFO -> AlertType.Info
    }

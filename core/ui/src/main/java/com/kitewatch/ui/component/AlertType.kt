package com.kitewatch.ui.component

sealed interface AlertType {
    data object Error : AlertType

    data object Warning : AlertType

    data object Info : AlertType

    data object Success : AlertType
}

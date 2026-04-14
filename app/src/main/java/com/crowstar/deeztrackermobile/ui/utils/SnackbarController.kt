package com.crowstar.deeztrackermobile.ui.utils

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Global controller for showing snackbars from anywhere in the app.
 */
class SnackbarController(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope
) {
    fun showSnackbar(message: String) {
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            hostState.showSnackbar(message)
        }
    }
}

val LocalSnackbarController = compositionLocalOf<SnackbarController> {
    error("No SnackbarController provided")
}

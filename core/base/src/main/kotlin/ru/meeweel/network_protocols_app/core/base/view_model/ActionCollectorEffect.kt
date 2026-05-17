package ru.meeweel.network_protocols_app.core.base.view_model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

@Composable
fun <Action : UiAction> ActionCollectorEffect(
    actions: Flow<Action>,
    state: Lifecycle.State = Lifecycle.State.STARTED,
    collector: (Action) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(actions) {
        lifecycleOwner.repeatOnLifecycle(state) {
            actions.collect(collector)
        }
    }
}

package ru.meeweel.network_protocols_app.core.base.view_model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.LazyThreadSafetyMode
import kotlin.coroutines.CoroutineContext

abstract class NpViewModel<State : UiState, Intent : UiIntent, Action : UiAction> :
    ViewModel(),
    CoroutineScope {

    override val coroutineContext: CoroutineContext =
        viewModelScope.coroutineContext + CoroutineExceptionHandler(::onCoroutineExceptionHandled)

    protected open fun onCoroutineExceptionHandled(
        context: CoroutineContext,
        throwable: Throwable,
    ) {
        Log.e(
            "CoroutineException",
            "Coroutine exception handled in ${javaClass.simpleName}.",
            throwable,
        )
    }

    abstract fun createInitialState(): State

    private val _state: MutableStateFlow<State> by lazy(LazyThreadSafetyMode.NONE) {
        MutableStateFlow(createInitialState())
    }

    val state by lazy(LazyThreadSafetyMode.NONE) {
        _state.asStateFlow()
    }

    private val _intent = MutableSharedFlow<Intent>()

    val intent = _intent.asSharedFlow()

    private val _action = Channel<Action>()

    val action = _action.receiveAsFlow()

    abstract fun consumeIntent(intent: Intent)

    init {
        _intent
            .onEach(::consumeIntent)
            .launchIn(this)
    }

    fun intent(intent: Intent) {
        launch {
            _intent.emit(intent)
        }
    }

    fun action(action: Action) {
        launch {
            _action.send(action)
        }
    }

    fun produce(reduce: State.() -> State) {
        val newState = _state.value.reduce()
        _state.update { newState }
    }
}

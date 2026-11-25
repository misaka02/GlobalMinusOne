package com.example.globalminusone.service.screenassistant

import com.example.globalminusone.screenassistant.ScreenAssistantWorkSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

/** Binds provider completion and failure to the operation that requested it, within the existing session. */
internal class ScreenAssistantOperationWork(
    private val session: ScreenAssistantWorkSession,
    private val presentation: ScreenAssistantPresentation,
    private val onFailure: (String, Throwable) -> Unit
) {
    fun <Result> launch(
        label: String,
        operation: ScreenAssistantPresentation.Operation,
        produce: suspend () -> Result,
        publish: (Result) -> Unit
    ): Job? {
        if (!accepts(operation)) return null
        return session.launch(label) {
            try {
                val result = produce()
                if (accepts(operation)) publish(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (accepts(operation)) onFailure(label, failure)
            }
        }
    }

    private fun accepts(operation: ScreenAssistantPresentation.Operation): Boolean =
        session.isActive && presentation.accepts(operation)
}

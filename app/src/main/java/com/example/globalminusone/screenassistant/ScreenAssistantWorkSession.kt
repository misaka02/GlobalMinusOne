package com.example.globalminusone.screenassistant

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One visible capture owns all its work; a new capture never inherits old callbacks. */
internal class ScreenAssistantWorkSession(
    parent: CoroutineScope,
    private val onFailure: (String, Throwable) -> Unit
) {
    private val job = SupervisorJob(parent.coroutineContext[Job])
    private val scope = CoroutineScope(parent.coroutineContext + job)
    val isActive: Boolean get() = job.isActive

    fun launch(label: String, block: suspend CoroutineScope.() -> Unit): Job = scope.launch {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            if (isActive) onFailure(label, failure)
        }
    }

    fun close() = scope.cancel()
}

package com.example.globalminusone.system.privilege

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Once a bounded protocol operation enters, drain and finalize health before delivering UI cancellation. */
internal suspend fun <T> drainRootOperation(operation: suspend () -> T): T {
    currentCoroutineContext().ensureActive()
    val result = withContext(NonCancellable) { operation() }
    currentCoroutineContext().ensureActive()
    return result
}

package com.example.globalminusone.regression.v2410

import com.example.globalminusone.browser.ChromeRootTaskApi
import org.junit.Assert.*
import org.junit.Test

class ChromeRootTaskApiTest {
    interface Cookie
    private val cookie = object : Cookie { }
    class Android13Controller {
        var received: List<Any?>? = null
        fun createRootTask(display: Int, mode: Int, cookie: Cookie) { received = listOf(display, mode, cookie) }
    }
    class Android16Controller {
        var received: List<Any?>? = null
        fun createRootTask(display: Int, mode: Int, cookie: Cookie, remove: Boolean, reparent: Boolean, name: String) {
            received = listOf(display, mode, cookie, remove, reparent, name)
        }
    }
    class UnsupportedController { fun createRootTask(display: Int, mode: Int, packageName: String) = Unit }

    @Test fun api33ThreeArgumentBinderSignatureActuallyCreatesTheRequestedRoot() {
        val controller = Android13Controller()
        val method = ChromeRootTaskApi.creationMethod(controller.javaClass, Cookie::class.java)
        method.invoke(controller, *ChromeRootTaskApi.creationArguments(method, 42, cookie))
        assertEquals(listOf(42, 1, cookie), controller.received)
    }
    @Test fun extendedSignatureKeepsDisplayRemovalFromReparentingTasks() {
        val controller = Android16Controller()
        val method = ChromeRootTaskApi.creationMethod(controller.javaClass, Cookie::class.java)
        method.invoke(controller, *ChromeRootTaskApi.creationArguments(method, 42, cookie))
        assertEquals(listOf(42, 1, cookie, false, false, "GmoChrome"), controller.received)
    }
    @Test fun differentParameterTypesCannotAccidentallySelectUnrelatedRomMethod() {
        try { ChromeRootTaskApi.creationMethod(UnsupportedController::class.java, Cookie::class.java); fail("Wrong signature accepted") }
        catch (_: IllegalStateException) { }
    }
}

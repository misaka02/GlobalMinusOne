# Xposed API 82 Compile-Only ABI Stub

Minimal Apache-2.0 compatible ABI stub for `de.robv.android.xposed` API 82, compiled purely for `compileOnly(files("libs/xposed-api-82-stub.jar"))` offline builds.

- Runtime implementation is supplied by LSPosed inside `system_server` (`android`).
- `xposed-api-82-stub.jar` is NEVER packaged into the APK DEX (`compileOnly`).

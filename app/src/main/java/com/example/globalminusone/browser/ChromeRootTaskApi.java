package com.example.globalminusone.browser;

import java.lang.reflect.Method;

/** The native binder API added arguments across Android releases; no launch fallback. */
public final class ChromeRootTaskApi {
    private ChromeRootTaskApi() {}
    public static Method creationMethod(Class<?> controller, Class<?> binderType) {
        for (Method method : controller.getMethods()) {
            Class<?>[] p = method.getParameterTypes();
            if (method.getName().equals("createRootTask") && p.length >= 3 && p.length <= 6 &&
                    p[0] == int.class && p[1] == int.class && p[2] == binderType &&
                    (p.length < 4 || p[3] == boolean.class) &&
                    (p.length < 5 || p[4] == boolean.class) && (p.length < 6 || p[5] == String.class)) return method;
        }
        throw new IllegalStateException("ROM 缺少目标显示器根任务 API；未启动 Chrome");
    }
    public static Object[] creationArguments(Method create, int displayId, Object cookie) {
        Object[] args = new Object[create.getParameterCount()];
        args[0] = displayId; args[1] = 1; args[2] = cookie; // Fullscreen on the owned display.
        if (args.length >= 4) args[3] = false;
        if (args.length >= 5) args[4] = false; // Never reparent on display removal.
        if (args.length >= 6) args[5] = "GmoChrome";
        return args;
    }
}

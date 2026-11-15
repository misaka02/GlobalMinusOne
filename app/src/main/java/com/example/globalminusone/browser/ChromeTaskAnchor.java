package com.example.globalminusone.browser;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Owns one fullscreen organizer root on the virtual display; never registers a global organizer. */
final class ChromeTaskAnchor {
    private final Object controller;
    private final Object windowOrganizer;
    private final Class<?> tokenType;
    private final int displayId;
    private Object token;
    private int taskId = -1;

    ChromeTaskAnchor(int displayId) throws Exception {
        this.displayId = displayId;
        tokenType = Class.forName("android.window.WindowContainerToken");
        Class<?> atm = Class.forName("android.app.ActivityTaskManager");
        Object taskManager = atm.getMethod("getService").invoke(null);
        windowOrganizer = invoke(taskManager, "getWindowOrganizerController", new Class<?>[0]);
        controller = invoke(windowOrganizer, "getTaskOrganizerController", new Class<?>[0]);
        // Native permission/API checks run before any Chrome launch, not as a post-launch test.
        Set<Integer> before = new HashSet<>();
        for (ActivityManager.RunningTaskInfo info : roots()) before.add(EmbeddedTaskInfo.taskId(info));
        IBinder cookie = new Binder();
        Method create = ChromeRootTaskApi.creationMethod(controller.getClass(), IBinder.class);
        Object[] args = ChromeRootTaskApi.creationArguments(create, displayId, cookie);
        create.setAccessible(true); create.invoke(controller, args);
        List<ActivityManager.RunningTaskInfo> added = new ArrayList<>();
        for (ActivityManager.RunningTaskInfo info : roots()) if (!before.contains(EmbeddedTaskInfo.taskId(info))) added.add(info);
        if (added.size() != 1) throw new IllegalStateException("不能唯一确认本次显示器根任务；未启动 Chrome");
        ActivityManager.RunningTaskInfo root = added.get(0);
        token = root.getClass().getField("token").get(root); taskId = EmbeddedTaskInfo.taskId(root);
        if (!verified() || root.numActivities != 0) throw new IllegalStateException("本次根任务不是目标显示器上的空任务；未启动 Chrome");
    }
    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getMethod(name, types); method.setAccessible(true);
        return method.invoke(target, args);
    }
    @SuppressWarnings("unchecked") private List<ActivityManager.RunningTaskInfo> roots() throws Exception {
        return (List<ActivityManager.RunningTaskInfo>) invoke(controller, "getRootTasks", new Class<?>[]{int.class, int[].class}, displayId, null);
    }
    boolean verified() throws Exception {
        if (token == null) return false;
        Object expected = tokenType.getMethod("asBinder").invoke(token);
        for (ActivityManager.RunningTaskInfo root : roots()) {
            if (EmbeddedTaskInfo.taskId(root) != taskId || root.getClass().getField("displayId").getInt(root) != displayId) continue;
            Object actualToken = root.getClass().getField("token").get(root);
            return expected.equals(tokenType.getMethod("asBinder").invoke(actualToken));
        }
        return false;
    }
    /** Apply the VD canvas bounds only to this verified organizer root. Used
     * before launch and after an explicit same-display resize, never for a host
     * projection/drag or for any personal task outside this root. */
    void configureCanvasBounds(int width, int height) throws Exception {
        if (!verified()) throw new IllegalStateException("目标根任务已失效；未启动应用");
        Rect expected = new Rect(0, 0, width, height);
        if (matchesCanvasBounds(expected)) return;
        Class<?> transactionType = Class.forName("android.window.WindowContainerTransaction");
        Object transaction = transactionType.getConstructor().newInstance();
        invoke(transaction, "setWindowingMode", new Class<?>[]{tokenType, int.class}, token, 1);
        invoke(transaction, "setBounds", new Class<?>[]{tokenType, Rect.class}, token, expected);
        invoke(windowOrganizer, "applyTransaction", new Class<?>[]{transactionType}, transaction);
        if (!matchesCanvasBounds(expected))
            throw new IllegalStateException("ROM 未保留完整应用画布尺寸；未启动应用");
    }
    private boolean matchesCanvasBounds(Rect expected) throws Exception {
        if (!verified()) return false;
        for (ActivityManager.RunningTaskInfo root : roots()) {
            if (EmbeddedTaskInfo.taskId(root) != taskId) continue;
            Object config = root.getClass().getField("configuration").get(root);
            Object windowConfig = config.getClass().getField("windowConfiguration").get(config);
            Rect bounds = (Rect) invoke(windowConfig, "getBounds", new Class<?>[0]);
            int mode = (Integer) invoke(windowConfig, "getWindowingMode", new Class<?>[0]);
            return mode == 1 && expected.equals(bounds);
        }
        return false;
    }
    void bind(ActivityOptions options) throws Exception {
        if (!verified()) throw new IllegalStateException("目标根任务已失效；未启动 Chrome");
        invoke(options, "setLaunchRootTask", new Class<?>[]{tokenType}, token);
        // Validate the actual IPC bundle too, rather than only the mutable builder.
        Object serialized = ActivityOptions.class.getMethod("fromBundle", android.os.Bundle.class).invoke(null, options.toBundle());
        Object readBack = invoke(serialized, "getLaunchRootTask", new Class<?>[0]);
        IBinder expected = (IBinder) tokenType.getMethod("asBinder").invoke(token);
        if (tokenType.isInstance(readBack)) readBack = tokenType.getMethod("asBinder").invoke(readBack);
        int actualDisplay = (Integer) invoke(serialized, "getLaunchDisplayId", new Class<?>[0]);
        if (!expected.equals(readBack) || actualDisplay != displayId)
            throw new IllegalStateException("ROM 没有保留目标根任务/显示器参数；未启动 Chrome");
    }
    @SuppressWarnings("unchecked") List<ActivityManager.RunningTaskInfo> children() throws Exception {
        if (!verified()) throw new IllegalStateException("Chrome 根任务已退出");
        return (List<ActivityManager.RunningTaskInfo>) invoke(controller, "getChildTasks", new Class<?>[]{tokenType, int[].class}, token, null);
    }
    void setPresentationHidden(boolean hidden) throws Exception {
        if (!verified()) throw new IllegalStateException("本次应用根任务已失效；未改变窗口显示");
        Class<?> transactionType = Class.forName("android.window.WindowContainerTransaction");
        Object transaction = transactionType.getConstructor().newInstance();
        // Hiding the ordinary child calls moveToBack, which can reparent it out
        // of an organizer root. Hide our verified organizer root as a unit instead.
        invoke(transaction, "setHidden", new Class<?>[]{tokenType, boolean.class}, token, hidden);
        invoke(transaction, "setFocusable", new Class<?>[]{tokenType, boolean.class}, token, !hidden);
        invoke(windowOrganizer, "applyTransaction", new Class<?>[]{transactionType}, transaction);
        if (!verified()) throw new IllegalStateException("本次应用根任务显示变更未确认");
    }
    void close() throws Exception {
        if (token != null) {
            if (!verified()) { token = null; taskId = -1; return; }
            Object owned = token;
            if (!Boolean.TRUE.equals(invoke(controller, "deleteRootTask", new Class<?>[]{tokenType}, owned)))
                throw new IllegalStateException("系统未确认本次 Chrome 根任务释放");
            token = null; taskId = -1;
        }
    }
}

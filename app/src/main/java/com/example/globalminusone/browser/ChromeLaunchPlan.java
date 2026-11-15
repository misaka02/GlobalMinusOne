package com.example.globalminusone.browser;

/** Actual launch parameters shared by the native launcher and JVM regression tests. */
public final class ChromeLaunchPlan {
    public static final String PREFER_NEW = "com.android.chrome.prefer_new";
    public static final String WINDOW_ID = "org.chromium.chrome.browser.window_id";
    public static final String CREATE_NEW_TAB = "create_new_tab";
    public final boolean initial;
    public final int flags;
    public final int displayId;
    public final int taskId;
    public final boolean preferNew;
    public final boolean createNewTab;
    public final boolean nativeHome;

    private ChromeLaunchPlan(boolean initial, int displayId, int taskId, boolean nativeHome) {
        this.initial = initial; this.displayId = displayId; this.taskId = taskId;
        // An app window should let Chrome resume its own inactive persisted
        // window. Forcing a new instance on every open exhausts its window slots.
        // Live tasks still require the engine's independent ownership checks.
        this.preferNew = initial && !nativeHome;
        this.createNewTab = initial && !nativeHome;
        this.nativeHome = nativeHome && initial;
        // Chromium's own new-window path: NEW_TASK + MULTIPLE_TASK, not NEW_DOCUMENT
        // (document restoration) or LAUNCH_ADJACENT (split-screen positioning).
        this.flags = 0x10000000 | (initial ? 0x08000000 | 0x00800000 : 0x20000000);
    }
    public static void requireBrowser(int sdk, int chromeMajor, int launchMode, boolean exported, boolean resizable) {
        if (sdk < 31 || sdk > 36 || chromeMajor < 120 || launchMode != 4 || !exported || !resizable)
            throw new IllegalStateException("需要 Android 12–16 与支持独立多窗口的 Chrome 120+；未启动 Chrome");
    }
    public static ChromeLaunchPlan create(boolean initial, int displayId, int ownedTaskId, boolean nativeRootVerified) {
        return checked(initial, displayId, ownedTaskId, nativeRootVerified, false);
    }
    /** Chrome chooses its persisted app-window state, without a forced new tab or URL. */
    public static ChromeLaunchPlan forAppWindow(boolean initial, int displayId, int ownedTaskId, boolean nativeRootVerified) {
        return checked(initial, displayId, ownedTaskId, nativeRootVerified, true);
    }
    private static ChromeLaunchPlan checked(boolean initial, int displayId, int ownedTaskId,
            boolean nativeRootVerified, boolean nativeHome) {
        if (displayId <= 0 || !nativeRootVerified || !initial && ownedTaskId < 0)
            throw new IllegalStateException("系统未提供已核实的目标显示器根任务；未启动 Chrome");
        return new ChromeLaunchPlan(initial, displayId, initial ? -1 : ownedTaskId, nativeHome);
    }
}

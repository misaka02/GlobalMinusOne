package com.example.globalminusone.browser;

/** Native task semantics accepted by the generic route; never reuses/moves a personal task. */
public final class EmbeddedAppLaunchPlan {
    private EmbeddedAppLaunchPlan() {}
    public static void requireTargetDisplay(int displayId, boolean rootVerified) {
        if (displayId <= 0 || !rootVerified)
            throw new IllegalStateException("未确认本次应用的独立显示器根任务；未启动应用");
    }
    public static int flags(int sdk, int launchMode, int documentMode, boolean exported, boolean resizable) {
        if (sdk < 31 || sdk > 36 || !exported || !resizable || documentMode != 0 ||
                !(launchMode == 0 || launchMode == 1 || launchMode == 4))
            throw new IllegalStateException("此组件不支持独立可调整大小的新任务；未启动外部应用");
        return 0x10000000 | 0x08000000 | 0x00800000; // NEW_TASK | MULTIPLE_TASK | EXCLUDE_FROM_RECENTS.
    }
}

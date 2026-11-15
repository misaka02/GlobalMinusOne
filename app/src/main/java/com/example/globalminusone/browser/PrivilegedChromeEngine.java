package com.example.globalminusone.browser;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Runs the installed browser on an owned display. Browser storage always stays inside Chrome. */
public final class PrivilegedChromeEngine {
    private static final Set<String> CHROME = new HashSet<>(Arrays.asList(
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary"));
    private final EmbeddedLaunchGate launchGate = new EmbeddedLaunchGate();
    private final Context ownerContext;
    private final boolean privileged;
    private VirtualDisplay virtualDisplay;
    private Surface surface;
    private ChromeParkingSurface parkingSurface;
    private boolean outputSuspended;
    private boolean geometryUncertain;
    private String chromePackage;
    private android.content.ComponentName appComponent;
    private boolean chromeRoute;
    private boolean chromeNativeHome;
    private int appFlags;
    private String lastError = "";
    private Context shellContext;
    private int width, height;
    private int canvasDensity;
    private long lastContact;
    private long openedAt;
    private final EmbeddedTaskPresence taskPresence = new EmbeddedTaskPresence();
    private static final String SESSION_EXTRA = "com.example.globalminusone.CHROME_SESSION";
    private String launchSession;
    private final Set<Integer> preexistingTaskIds = new HashSet<>();
    private final EmbeddedOwnedTasks ownedTasks = new EmbeddedOwnedTasks();
    private ChromeTaskAnchor taskAnchor;
    private final EmbeddedExternalOverlayMonitor externalOverlays = new EmbeddedExternalOverlayMonitor();
    private int targetUid = -1;
    public PrivilegedChromeEngine(Context context, boolean privileged) {
        this.ownerContext = context;
        this.privileged = privileged;
    }
    private static int optionalFlag(String name) {
        try { return DisplayManager.class.getField(name).getInt(null); }
        catch (ReflectiveOperationException ignored) { return 0; }
    }
    private Context displayContext() throws Exception {
        if (!privileged) return ownerContext;
        if (shellContext != null) return shellContext;
        // createPackageContext alone inherits the app's base/op package; Android then
        // correctly rejects that package for this shell-UID process. Build a fresh
        // context from the actual Shell LoadedApk instead of changing/bypassing a UID.
        Context packaged = ownerContext.createPackageContext("com.android.shell", Context.CONTEXT_IGNORE_SECURITY);
        Class<?> impl = Class.forName("android.app.ContextImpl");
        java.lang.reflect.Field packageInfo = impl.getDeclaredField("mPackageInfo");
        packageInfo.setAccessible(true);
        Class<?> threadType = Class.forName("android.app.ActivityThread");
        Object thread = threadType.getMethod("currentActivityThread").invoke(null);
        Method create = impl.getDeclaredMethod("createAppContext", threadType, packageInfo.getType());
        create.setAccessible(true);
        shellContext = (Context) create.invoke(null, thread, packageInfo.get(packaged));
        if (!"com.android.shell".equals(shellContext.getPackageName()))
            throw new SecurityException("Shell context attribution could not be established");
        return shellContext;
    }
    private static void size(int width, int height, int density) {
        if (width < 64 || height < 64 || width > 4096 || height > 4096 || (long) width * height > 8_388_608 || density < 72 || density > 640)
            throw new IllegalArgumentException("Display size or density exceeds the supported range");
    }
    static String checkedUrl(String value) {
        if (value == null || value.trim().isEmpty()) return "about:blank";
        if (value.length() > 8192 || value.chars().anyMatch(c -> c < 32 || c == 127)) throw new IllegalArgumentException("Invalid URL");
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme) && !"about:blank".equals(value))
            throw new IllegalArgumentException("Only http, https and about:blank can be opened");
        return value;
    }
    public synchronized String open(Surface target, int width, int height, int density, String pkg, String url) throws Exception {
        if (!CHROME.contains(pkg)) throw new SecurityException("Only an installed Chrome channel may be embedded");
        return openWindow(target, width, height, density, pkg, url, null);
    }
    public synchronized String openApp(Surface target, int width, int height, int density, android.content.ComponentName component) throws Exception {
        if (component == null) throw new IllegalArgumentException("Missing explicit app component");
        if (CHROME.contains(component.getPackageName())) component = new android.content.ComponentName(
                component.getPackageName(), "org.chromium.chrome.browser.ChromeTabbedActivity");
        return openWindow(target, width, height, density, component.getPackageName(), "about:blank", component);
    }
    private String openWindow(Surface target, int width, int height, int density, String pkg, String url,
            android.content.ComponentName component) throws Exception {
        size(width, height, density); checkedUrl(url);
        if (target == null || !target.isValid()) throw new IllegalArgumentException("Browser surface is unavailable");
        if (ownerContext.getApplicationInfo().uid / 100000 != 0) throw new IllegalStateException("This backend has not yet verified work-profile browser isolation");
        if (!privileged) throw new SecurityException("应用小窗需要已授权的 Shizuku 或 Root；未启动外部应用");
        if (launchGate.isOpening()) throw new IllegalStateException("Window launch is already in progress");
        close();
        if (taskAnchor != null) throw new IllegalStateException("上次根任务回收尚未确认；未再次启动应用");
        long attempt = launchGate.begin();
        this.chromePackage = pkg; this.width = width; this.height = height; this.canvasDensity = density;
        this.chromeRoute = CHROME.contains(pkg);
        this.chromeNativeHome = chromeRoute && component != null;
        this.appComponent = chromeRoute ? null : component;
        launchSession = java.util.UUID.randomUUID().toString();
        try {
            Context context = displayContext();
            android.content.ComponentName tabbed = chromeRoute ? new android.content.ComponentName(chromePackage,
                    "org.chromium.chrome.browser.ChromeTabbedActivity") : appComponent;
            android.content.pm.ActivityInfo info = context.getPackageManager().getActivityInfo(tabbed, 0);
            targetUid = info.applicationInfo.uid;
            int resizeMode = info.getClass().getField("resizeMode").getInt(info);
            boolean resizable = (Boolean) info.getClass().getMethod("isResizeableMode", int.class).invoke(null, resizeMode);
            if (chromeRoute) {
                int major = EmbeddedBrowserVersion.major(context.getPackageManager().getPackageInfo(chromePackage, 0).versionName);
                ChromeLaunchPlan.requireBrowser(Build.VERSION.SDK_INT, major, info.launchMode, info.enabled && info.exported, resizable);
            } else appFlags = EmbeddedAppLaunchPlan.flags(Build.VERSION.SDK_INT, info.launchMode, info.documentLaunchMode,
                    info.enabled && info.exported, resizable);
            if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS))
                throw new IllegalStateException("Device does not support activities on secondary displays");
            // The API-only probe uses a private display. Authorized embedding must also
            // admit the user's IME UID; PUBLIC therefore needs explicit destroy-on-close.
            int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
            if (privileged) {
                int trusted = optionalFlag("VIRTUAL_DISPLAY_FLAG_TRUSTED");
                if (trusted == 0) throw new IllegalStateException("Trusted secondary display API is unavailable");
                int destroy = optionalFlag("VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL");
                if (destroy == 0) throw new IllegalStateException("Safe browser display removal API is unavailable");
                flags |= trusted | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC | destroy;
                // Preserve the remote focused editor while a physical touch first
                // passes through the host display on its way to this surface.
                flags |= optionalFlag("VIRTUAL_DISPLAY_FLAG_OWN_FOCUS");
            }
            surface = target;
            virtualDisplay = ((DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE)).createVirtualDisplay(
                    "GlobalMinusOne-Chrome-" + Process.myPid(), width, height, density, target, flags);
            if (virtualDisplay == null) throw new IllegalStateException("Android did not create the browser display");
            // Server links the owner's lifetime Binder. Time without a host
            // heartbeat is not permission loss and must not retire this task.
            openedAt = lastContact = SystemClock.elapsedRealtime();
            configureIme();
            taskAnchor = new ChromeTaskAnchor(virtualDisplay.getDisplay().getDisplayId());
            taskAnchor.configureCanvasBounds(width, height);
            capturePreexistingTasks(context);
            if (!launchGate.isCurrent(attempt)) throw new IllegalStateException("Window launch was cancelled");
            launch(checkedUrl(url), true);
            // A successful startActivity result does not prove display placement.
            // Do not expose a connected surface until the owned task is observable.
            long deadline = SystemClock.elapsedRealtime() + 8_000;
            ActivityManager.RunningTaskInfo task;
            while (true) {
                if (!launchGate.isCurrent(attempt)) throw new IllegalStateException("Window launch was cancelled");
                task = findOwnedTask(context);
                if (task != null || SystemClock.elapsedRealtime() >= deadline) break;
                if (hasEscapedLaunch(context)) throw new IllegalStateException("应用未进入面板显示区域，已结束本次窗口；没有外部启动回退");
                // Release this engine's monitor during polling. CLOSE/death can cancel
                // the attempt; after waking no stale attempt may touch a new session.
                wait(100);
            }
            if (task == null) throw new IllegalStateException(chromeRoute
                    ? "Chrome 未提供可用新窗口，可能达到其多实例上限（部分版本为20个窗口）。请检查 Chrome 自身窗口列表；未删除旧窗口或个人数据"
                    : "未确认应用小窗任务；已释放本次显示区域，请检查应用兼容性");
            lastError = "";
            launchGate.finish(attempt);
            return status();
        } catch (Exception failure) {
            String originalFailure = failureMessage(failure);
            if (launchGate.isCurrent(attempt)) { lastError = originalFailure; close(); }
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException(originalFailure, failure);
        } finally { launchGate.finish(attempt); }
    }
    private void capturePreexistingTasks(Context context) throws Exception {
        // Running tasks alone omit finished personal tasks retained in Recents.
        // Without a complete same-user baseline, membership in our new root is
        // not sufficient proof that a nested singleTask did not reuse one.
        Class<?> manager = Class.forName("android.app.ActivityTaskManager");
        Object service = manager.getMethod("getService").invoke(null);
        Method recent = service.getClass().getMethod("getRecentTasks", int.class, int.class, int.class);
        recent.setAccessible(true);
        Object slice = recent.invoke(service, 4096, ActivityManager.RECENT_WITH_EXCLUDED, 0);
        if (slice == null) throw new IllegalStateException("无法核实已有最近任务；未启动应用小窗");
        Method getList = slice.getClass().getMethod("getList"); getList.setAccessible(true);
        Object items = getList.invoke(slice);
        if (!(items instanceof java.util.List) || ((java.util.List<?>) items).size() >= 4096)
            throw new IllegalStateException("最近任务基线不完整；未启动应用小窗");
        for (Object item : (java.util.List<?>) items) {
            if (!(item instanceof ActivityManager.RecentTaskInfo))
                throw new IllegalStateException("最近任务格式无法核实；未启动应用小窗");
            preexistingTaskIds.add(EmbeddedTaskInfo.taskId((ActivityManager.RecentTaskInfo) item));
        }
        for (ActivityManager.RunningTaskInfo existing : ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getRunningTasks(4096))
            preexistingTaskIds.add(EmbeddedTaskInfo.taskId(existing));
    }
    private void launch(String url, boolean initial) throws Exception {
        if (virtualDisplay == null) throw new IllegalStateException("Chrome display is closed");
        Context context = displayContext();
        int displayId = virtualDisplay.getDisplay().getDisplayId();
        ActivityManager.RunningTaskInfo owned = initial ? null : findOwnedTask(context);
        boolean rootVerified = taskAnchor != null && taskAnchor.verified();
        ChromeLaunchPlan plan = null;
        if (chromeRoute) plan = chromeNativeHome
                ? ChromeLaunchPlan.forAppWindow(initial, displayId, owned == null ? -1 : EmbeddedTaskInfo.taskId(owned), rootVerified)
                : ChromeLaunchPlan.create(initial, displayId, owned == null ? -1 : EmbeddedTaskInfo.taskId(owned), rootVerified);
        else EmbeddedAppLaunchPlan.requireTargetDisplay(displayId, rootVerified);
        // Chromium's own new-window intent has no action or URL. ACTION_MAIN may
        // dispatch to an existing personal Chrome task before placement is checked.
        Intent intent = chromeRoute ? (plan.nativeHome ? new Intent() :
                new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(chromePackage)
                        .addCategory(Intent.CATEGORY_BROWSABLE))
                : new Intent(Intent.ACTION_MAIN).setComponent(appComponent);
        intent.putExtra(SESSION_EXTRA, launchSession);
        if (!chromeRoute) intent.setFlags(appFlags);
        if (privileged && chromeRoute) {
            android.content.ComponentName tabbed = new android.content.ComponentName(chromePackage,
                    "org.chromium.chrome.browser.ChromeTabbedActivity");
            android.content.pm.ActivityInfo info = context.getPackageManager().getActivityInfo(tabbed, 0);
            if (!info.enabled || !info.exported || info.launchMode != 4)
                throw new IllegalStateException("This Chrome version does not expose an isolated multi-instance tabbed activity");
            intent.setComponent(tabbed);
        }
        if (context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null)
            throw new IllegalStateException("The selected Chrome application cannot handle this URL");
        if (chromeRoute) {
            intent.setFlags(plan.flags);
            intent.putExtra(ChromeLaunchPlan.PREFER_NEW, plan.preferNew);
            intent.putExtra(ChromeLaunchPlan.CREATE_NEW_TAB, plan.createNewTab);
            intent.putExtra(android.provider.Browser.EXTRA_APPLICATION_ID, ownerContext.getPackageName());
            if (initial) intent.putExtra(ChromeLaunchPlan.WINDOW_ID, -1);
        }
        if (Build.VERSION.SDK_INT >= 29 && !((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).isActivityStartAllowedOnDisplay(context, displayId, intent))
            throw new SecurityException("Android/Chrome refused cross-app display embedding; no full-screen fallback was launched");
        ActivityOptions launchOptions = ActivityOptions.makeBasic().setLaunchDisplayId(displayId);
        taskAnchor.bind(launchOptions);
        if (privileged && chromeRoute && !initial) {
            // Never let an external VIEW intent pick the user's normal Chrome task.
            if (owned == null) throw new IllegalStateException("The owned Chrome task is no longer available");
            Method selectTask = ActivityOptions.class.getMethod("setLaunchTaskId", int.class);
            selectTask.setAccessible(true); selectTask.invoke(launchOptions, plan.taskId);
        }
        android.os.Bundle options = launchOptions.toBundle();
        startFromAuthorizedProcess(intent, options);
        lastContact = SystemClock.elapsedRealtime();
    }
    private ActivityManager.RunningTaskInfo findOwnedTask(Context context) throws Exception {
        if (virtualDisplay == null) return null;
        int displayId = virtualDisplay.getDisplay().getDisplayId();
        if (taskAnchor == null) return null;
        for (ActivityManager.RunningTaskInfo task : taskAnchor.children()) {
            int taskDisplay = task.getClass().getField("displayId").getInt(task);
            if (!preexistingTaskIds.contains(EmbeddedTaskInfo.taskId(task)) && taskDisplay == displayId) {
                // Root membership is stronger ownership evidence than an Intent extra:
                // Chrome may replace its base Intent while dispatching a new window.
                ownedTasks.observe(EmbeddedTaskInfo.taskId(task), false, true, false);
                if (task.numActivities > 0 && task.baseActivity != null && chromePackage.equals(task.baseActivity.getPackageName())) return task;
            }
        }
        return null;
    }
    private boolean hasEscapedLaunch(Context context) throws Exception {
        int expected = virtualDisplay.getDisplay().getDisplayId();
        boolean escaped = false;
        for (ActivityManager.RunningTaskInfo task : ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getRunningTasks(128)) {
            if (isMarkedTask(task)) {
                if (task.getClass().getField("displayId").getInt(task) != expected) escaped = true;
            }
        }
        return escaped;
    }
    private boolean isMarkedTask(ActivityManager.RunningTaskInfo task) {
        return ChromeTaskOwnership.belongsToSession(launchSession,
                EmbeddedTaskInfo.baseIntent(task) == null ? null : EmbeddedTaskInfo.baseIntent(task).getStringExtra(SESSION_EXTRA), preexistingTaskIds.contains(EmbeddedTaskInfo.taskId(task)),
                chromePackage, task.baseActivity == null ? null : task.baseActivity.getPackageName());
    }
    private void removeOwnedTasks() {
        if (!privileged || launchSession == null) return;
        try {
            Context context = displayContext();
            Class<?> manager = Class.forName(Build.VERSION.SDK_INT >= 29 ? "android.app.ActivityTaskManager" : "android.app.ActivityManager");
            Object service = manager.getMethod("getService").invoke(null);
            Method remove = service.getClass().getMethod("removeTask", int.class);
            remove.setAccessible(true);
            try { if (taskAnchor != null) for (ActivityManager.RunningTaskInfo task : taskAnchor.children()) {
                ownedTasks.observe(EmbeddedTaskInfo.taskId(task), preexistingTaskIds.contains(EmbeddedTaskInfo.taskId(task)), true, false);
            } } catch (Exception ignored) { /* Root may already be gone; retain recorded IDs. */ }
            try { for (ActivityManager.RunningTaskInfo task : ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getRunningTasks(128)) {
                if (ChromeTaskOwnership.belongsToSession(launchSession,
                        EmbeddedTaskInfo.baseIntent(task) == null ? null : EmbeddedTaskInfo.baseIntent(task).getStringExtra(SESSION_EXTRA), preexistingTaskIds.contains(EmbeddedTaskInfo.taskId(task)),
                        chromePackage, task.baseActivity == null ? null : task.baseActivity.getPackageName()))
                    ownedTasks.observe(EmbeddedTaskInfo.taskId(task), preexistingTaskIds.contains(EmbeddedTaskInfo.taskId(task)), false, true);
            } } catch (Exception ignored) { /* A failed snapshot must not skip known-owned cleanup. */ }
            // removeTask also removes a finished task's Recents entry. Delete these
            // known-owned children BEFORE deleting the root (which loses membership).
            Exception removalFailure = null;
            for (int id : ownedTasks.snapshot()) if (!preexistingTaskIds.contains(id)) {
                try {
                    if (!Boolean.TRUE.equals(remove.invoke(service, id)))
                        lastError = "任务 " + id + " 已结束或系统未确认移除；未确认其最近任务条目状态";
                } catch (Exception failure) { removalFailure = failure; }
            }
            if (removalFailure != null) throw removalFailure;
        } catch (Exception failure) {
            lastError = "本次 Chrome 窗口回收失败：" + failureMessage(failure);
        }
    }
    private void startFromAuthorizedProcess(Intent intent, android.os.Bundle options) throws Exception {
        // A UserService is not an ActivityManager-managed app process. Supplying its
        // synthetic ApplicationThread would be rejected; use the shell-style null
        // caller and the real shell package, with Android still enforcing this UID.
        Class<?> manager = Class.forName(Build.VERSION.SDK_INT >= 29
                ? "android.app.ActivityTaskManager" : "android.app.ActivityManager");
        Object service = manager.getMethod("getService").invoke(null);
        for (Method method : service.getClass().getMethods()) {
            Class<?>[] p = method.getParameterTypes();
            if (!method.getName().equals("startActivityAsUser") || (p.length != 11 && p.length != 12)) continue;
            int at = p.length == 12 ? 3 : 2;
            if (p[at] != Intent.class || p[p.length - 2] != android.os.Bundle.class) continue;
            Object[] values = new Object[p.length];
            values[1] = "com.android.shell";
            values[at] = intent;
            values[at + 4] = -1; // requestCode
            values[at + 5] = 0;  // startFlags
            values[at + 7] = options;
            values[at + 8] = 0;  // Same Android user as the verified owner; no cross-profile API.
            method.setAccessible(true);
            int result = (Integer) method.invoke(service, values);
            if (result < 0 || result >= 100) throw new IllegalStateException("Chrome start was rejected, code=" + result);
            return;
        }
        throw new IllegalStateException("Authorized secondary-display launch API is unavailable");
    }
    private void configureIme() throws Exception {
        if (!privileged || virtualDisplay == null) return;
        Object wm = Class.forName("android.view.WindowManagerGlobal").getMethod("getWindowManagerService").invoke(null);
        // The editor remains on the app display; Android renders the real system IME
        // on its fallback (primary) display at full screen width, never inside the card.
        Method method = wm.getClass().getMethod("setDisplayImePolicy", int.class, int.class);
        method.setAccessible(true);
        int fallback = android.view.WindowManager.class.getField("DISPLAY_IME_POLICY_FALLBACK_DISPLAY").getInt(null);
        int displayId = virtualDisplay.getDisplay().getDisplayId();
        method.invoke(wm, displayId, fallback);
        Method read = wm.getClass().getMethod("getDisplayImePolicy", int.class); read.setAccessible(true);
        if (!Integer.valueOf(fallback).equals(read.invoke(wm, displayId)))
            throw new IllegalStateException("ROM 未保留主显示器系统键盘策略；未启动应用");
    }
    /** Verify the original child, then hide its owned organizer root without moving the child. */
    private void setOwnTaskHidden(boolean hidden) throws Exception {
        ActivityManager.RunningTaskInfo task = findOwnedTask(displayContext());
        if (task == null) throw new IllegalStateException("本次应用任务已退出");
        try {
            taskAnchor.setPresentationHidden(hidden);
        } catch (Exception anchorFailure) {
            // ColorOS 16 / OEM task organizers may reject WindowContainerTransaction.setHidden
            // on non-globally-registered task roots; parking the VirtualDisplay output surface
            // still suspends visible presentation safely without failing minimize/dock.
            lastError = "根任务隐藏状态切换降级：" + failureMessage(anchorFailure);
        }
    }
    /** Change presentation only; never relaunches, reparents or removes the owned app task. */
    private void replaceOutput(Surface target, boolean hidden) throws Exception {
        EmbeddedOutputTransition.replace(target, hidden, new EmbeddedOutputTransition.Port<Surface>() {
            @Override public void setTaskHidden(boolean value) throws Exception {
                setOwnTaskHidden(value);
                outputSuspended = value;
            }
            @Override public void installOutput(Surface output) {
                virtualDisplay.setSurface(output);
                // Record the accepted output before unhide can fail. Even on an
                // operation error, close/retry still owns the actual VD Surface.
                Surface previous = surface;
                surface = hidden ? null : output;
                if (previous != null && previous != output) previous.release();
            }
        });
    }
    public synchronized String outputSurface(Surface target, int width, int height, int density) throws Exception {
        // Calling this method consumes the input, even when validation or a later
        // unhide fails. Only an output actually accepted by the VD stays owned.
        try (EmbeddedSurfaceInput<Surface> incoming = new EmbeddedSurfaceInput<>(target, Surface::release)) {
            try {
                if (virtualDisplay == null) throw new IllegalStateException("应用显示区域已关闭");
                if (target == null) {
                    if (parkingSurface != null && !parkingSurface.matchesSize(this.width, this.height)) {
                        try { parkingSurface.close(); } catch (Exception ignored) { }
                        parkingSurface = null;
                    }
                    if (parkingSurface == null) parkingSurface = new ChromeParkingSurface(this.width, this.height);
                    // Keep the same display powered at identical buffer dimensions while parked.
                    replaceOutput(parkingSurface.surface(), true);
                } else {
                    size(width, height, density);
                    if (geometryUncertain) restoreCommittedGeometry();
                    if (!target.isValid()) throw new IllegalArgumentException("恢复的 Surface 尚不可用");
                    if (width != this.width || height != this.height || density != canvasDensity)
                        throw new IllegalArgumentException("输出尺寸与已确认画布不同，请先完成画布调整");
                    replaceOutput(target, false);
                }
                lastContact = SystemClock.elapsedRealtime(); return status();
            } finally {
                // installOutput records this before unhide can throw. The Binder
                // receiver must not release that accepted Surface on an error reply.
                if (target != null && surface == target) incoming.transfer();
            }
        }
    }
    /** Same VD/task/token. Android delivers its ordinary size/configuration change. */
    public synchronized String resize(int width, int height, int density) throws Exception {
        size(width, height, density);
        if (virtualDisplay == null || taskAnchor == null || !taskAnchor.verified())
            throw new IllegalStateException("本次应用显示区域暂不可用，连接仍保留");
        if (geometryUncertain) restoreCommittedGeometry();
        final int oldWidth = this.width, oldHeight = this.height, oldDensity = canvasDensity;
        if (width == oldWidth && height == oldHeight && density == oldDensity) return geometryStatus();
        geometryUncertain = true;
        try {
            EmbeddedDisplayResizeTransaction.replace(
                    new EmbeddedDisplayResizeTransaction.Geometry(oldWidth, oldHeight, oldDensity),
                    new EmbeddedDisplayResizeTransaction.Geometry(width, height, density),
                    new EmbeddedDisplayResizeTransaction.Port() {
                        @Override public void resizeDisplay(EmbeddedDisplayResizeTransaction.Geometry geometry) {
                            virtualDisplay.resize(geometry.width, geometry.height, geometry.density);
                            awaitDisplayGeometry(geometry.width, geometry.height, geometry.density);
                        }
                        @Override public void resizeRoot(EmbeddedDisplayResizeTransaction.Geometry geometry) throws Exception {
                            taskAnchor.configureCanvasBounds(geometry.width, geometry.height);
                        }
                    });
            this.width = width; this.height = height; canvasDensity = density;
            geometryUncertain = false;
            lastContact = SystemClock.elapsedRealtime();
            return geometryStatus();
        } catch (EmbeddedDisplayResizeTransaction.Failure failure) {
            geometryUncertain = !failure.rollbackConfirmed;
            throw new IllegalStateException(geometryUncertain
                    ? "画布调整及回退暂未确认，已暂停输入并保留会话；请重试显示"
                    : "画布调整未完成，已恢复原尺寸并保留会话", failure);
        }
    }
    private String geometryStatus() {
        return "display=" + virtualDisplay.getDisplay().getDisplayId() + "; size=" + width + "x" + height + "; density=" + canvasDensity;
    }
    private void restoreCommittedGeometry() throws Exception {
        geometryUncertain = true;
        virtualDisplay.resize(width, height, canvasDensity);
        awaitDisplayGeometry(width, height, canvasDensity);
        taskAnchor.configureCanvasBounds(width, height);
        geometryUncertain = false;
    }
    @SuppressWarnings("deprecation")
    private void awaitDisplayGeometry(int width, int height, int density) {
        long deadline = SystemClock.uptimeMillis() + 800;
        android.util.DisplayMetrics actual = new android.util.DisplayMetrics();
        do {
            virtualDisplay.getDisplay().getRealMetrics(actual);
            if (actual.widthPixels == width && actual.heightPixels == height && actual.densityDpi == density) return;
            SystemClock.sleep(20);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new IllegalStateException("系统尚未确认新的显示器尺寸");
    }
    private void inject(InputEvent event) throws Exception {
        if (!privileged) throw new SecurityException("Direct input requires an explicitly authorized Shizuku or Root backend");
        if (virtualDisplay == null) throw new IllegalStateException("Chrome display is closed");
        event.getClass().getMethod("setDisplayId", int.class).invoke(event, virtualDisplay.getDisplay().getDisplayId());
        Class<?> type;
        try { type = Class.forName("android.hardware.input.InputManagerGlobal"); }
        catch (ClassNotFoundException ignored) { type = Class.forName("android.hardware.input.InputManager"); }
        Object manager = type.getMethod("getInstance").invoke(null);
        // WAIT_FOR_FINISH: asynchronous enqueue alone did not prove a usable input channel.
        boolean accepted = (Boolean) type.getMethod("injectInputEvent", InputEvent.class, int.class).invoke(manager, event, 2);
        if (!accepted) throw new IllegalStateException("Android rejected input for the owned Chrome display");
        lastContact = SystemClock.elapsedRealtime();
    }
    public synchronized String touch(MotionEvent event) throws Exception {
        if (geometryUncertain) throw new IllegalStateException("画布尺寸尚待确认，未向应用发送触摸");
        int action = event.getActionMasked();
        if (event.getPointerCount() < 1 || event.getPointerCount() > 5 || !(action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE ||
                action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL))
            throw new IllegalArgumentException("Unsupported browser touch event");
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (!Float.isFinite(event.getX(i)) || !Float.isFinite(event.getY(i)) || event.getX(i) < -1 || event.getY(i) < -1 || event.getX(i) > width + 1 || event.getY(i) > height + 1)
                throw new IllegalArgumentException("Touch is outside the owned browser surface");
        }
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN); inject(event); return "accepted";
    }
    public synchronized String back() throws Exception {
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0);
        down.setSource(InputDevice.SOURCE_KEYBOARD);
        KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0);
        up.setSource(InputDevice.SOURCE_KEYBOARD);
        inject(down);
        inject(up); return "accepted";
    }
    public synchronized String command(ChromeCommand command) throws Exception {
        if (!chromeRoute) throw new IllegalStateException("此应用仅支持原生触摸与返回，不发送 Chrome 快捷键");
        if (findOwnedTask(displayContext()) == null) throw new IllegalStateException("本次 Chrome 窗口已退出；未向其它任务发送按键");
        long now = SystemClock.uptimeMillis();
        inject(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, command.keyCode, 0, command.metaState));
        inject(new KeyEvent(now, now, KeyEvent.ACTION_UP, command.keyCode, 0, command.metaState));
        return "accepted";
    }
    public synchronized String navigate(String url) throws Exception {
        if (!chromeRoute) throw new IllegalStateException("通用应用窗口没有网址导航接口");
        launch(checkedUrl(url), false); return status();
    }
    public synchronized String status() {
        return status(false);
    }
    public synchronized String heartbeatStatus() { return status(true); }
    private String status(boolean heartbeat) {
        if (launchGate.isOpening()) return "opening";
        if (geometryUncertain && virtualDisplay != null)
            return "output_unavailable: 画布回退尚待确认，输入已暂停，会话仍保留；请重试显示";
        if (privileged && virtualDisplay != null && SystemClock.elapsedRealtime() - openedAt > 2_000) {
            try {
                boolean escaped = taskAnchor == null || !taskAnchor.verified() || hasEscapedLaunch(displayContext());
                if (escaped) {
                    return "output_unavailable: 暂不能确认原任务归属，已保留会话；请重试显示或明确关闭";
                }
                boolean missing = findOwnedTask(displayContext()) == null;
                long now = SystemClock.elapsedRealtime();
                // A resize can recreate Chrome's Activity while its owned root
                // remains valid. Require persistent absence before classing it as exit.
                if (taskPresence.observe(!missing, heartbeat, now)) {
                    return "output_unavailable: 暂未找到原应用画面，已保留会话；请重试显示或明确关闭";
                }
            } catch (Exception failure) {
                taskPresence.reset();
                // An IPC/snapshot failure is not proof that the task or grant died.
                // Keep the session; a later status read or explicit exit can resolve it.
                lastContact = SystemClock.elapsedRealtime();
                return "任务状态暂未确认，连接已保留：" + failureMessage(failure);
            }
        }
        if (privileged && virtualDisplay != null) {
            if (heartbeat && !outputSuspended)
                externalOverlays.sample(chromePackage, targetUid, virtualDisplay.getDisplay().getDisplayId());
            if (externalOverlays.hasPrimaryOverlay()) {
                lastContact = SystemClock.elapsedRealtime();
                return "external_overlay: 检测到当前应用在主屏另建了独立悬浮窗，无法无损移入小窗，也不会随小窗收纳";
            }
        }
        lastContact = SystemClock.elapsedRealtime();
        return virtualDisplay == null ? "closed" + (lastError.isEmpty() ? "" : ": " + lastError) :
                "display=" + virtualDisplay.getDisplay().getDisplayId() + "; uid=" + Process.myUid() + "; package=" + chromePackage + "; size=" + width + "x" + height;
    }
    /** Permanently prevent a late Binder OPEN from reviving a retired session. */
    public void retire() { launchGate.retire(); close(); }
    public synchronized void close() {
        launchGate.cancel(); notifyAll();
        externalOverlays.close(); targetUid = -1;
        outputSuspended = false;
        geometryUncertain = false;
        removeOwnedTasks();
        if (taskAnchor != null) {
            try { taskAnchor.close(); taskAnchor = null; }
            catch (Exception failure) { lastError = "本次 Chrome 根任务回收未确认：" + failureMessage(failure); }
        }
        VirtualDisplay ownedDisplay = virtualDisplay; virtualDisplay = null;
        Surface ownedSurface = surface; surface = null;
        preexistingTaskIds.clear(); ownedTasks.clear(); taskPresence.reset(); launchSession = null;
        chromePackage = null;
        appComponent = null;
        chromeNativeHome = false;
        try { if (ownedDisplay != null) ownedDisplay.release(); }
        catch (Exception failure) { lastError = "应用显示区域释放未确认：" + failureMessage(failure); }
        finally {
            // Destroy-on-removal may now have removed a root whose first delete
            // was temporarily rejected. Keep its token until removal is confirmed.
            if (taskAnchor != null) {
                try { taskAnchor.close(); taskAnchor = null; }
                catch (Exception failure) { lastError = "本次 Chrome 根任务回收仍未确认：" + failureMessage(failure); }
            }
            try { if (ownedSurface != null) ownedSurface.release(); }
            catch (Exception failure) { lastError = "应用 Surface 释放未确认：" + failureMessage(failure); }
            ChromeParkingSurface parked = parkingSurface; parkingSurface = null;
            try { if (parked != null) parked.close(); }
            catch (Exception failure) { lastError = "应用隐藏输出释放未确认：" + failureMessage(failure); }
        }
    }
    static String failureMessage(Throwable error) {
        while (error instanceof InvocationTargetException && error.getCause() != null) error = error.getCause();
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null ? "" : ": " + message.substring(0, Math.min(800, message.length())));
    }
}

package com.example.globalminusone.debug;

import android.os.Build;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Emulator-only test entry. Absent from Release; never changes system settings or application data. */
public final class UiGestureDriver {
    private static Object input;
    private static Method inject;

    public static void main(String[] args) throws Exception {
        if (Process.myUid() != 0 && Process.myUid() != 2000) throw new SecurityException("Emulator shell only");
        Class<?> properties = Class.forName("android.os.SystemProperties");
        if (!"1".equals(properties.getMethod("get", String.class).invoke(null, "ro.kernel.qemu")) || Build.VERSION.SDK_INT < 31)
            throw new SecurityException("Requires the verified temporary emulator");
        if (args.length != 7 || !("pinch".equals(args[0]) || "hold-drag".equals(args[0]) || "hold-drag-cancel".equals(args[0])))
            throw new IllegalArgumentException("pinch left top right bottom startFraction endFraction | hold-drag or hold-drag-cancel left top right bottom endX endY");
        boolean cancelling = "hold-drag-cancel".equals(args[0]);
        boolean dragging = "hold-drag".equals(args[0]) || cancelling;
        float[] values = new float[6];
        for (int i = 0; i < values.length; i++) {
            values[i] = Float.parseFloat(args[i + 1]);
            if (!Float.isFinite(values[i])) throw new IllegalArgumentException("Non-finite coordinate");
        }
        float left = values[0], top = values[1], right = values[2], bottom = values[3];
        float start = values[4], end = values[5];
        if (left < 0 || top < 0 || right <= left + (dragging ? 4 : 48) || bottom <= top + (dragging ? 4 : 48) || right > 4096 || bottom > 4096 ||
                (dragging ? start < 0 || start > 4096 || end < 0 || end > 4096 :
                    start < .1f || start > .8f || end < .1f || end > .8f || start == end))
            throw new IllegalArgumentException("Invalid visible browser region or span");
        assertHostFocused();
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper();
        Class<?> manager = Class.forName("android.hardware.input.InputManager");
        input = manager.getMethod("getInstance").invoke(null);
        inject = manager.getMethod("injectInputEvent", InputEvent.class, int.class);
        float cx = (left + right) / 2, cy = (top + bottom) / 2;
        if (dragging) {
            if (cancelling) holdDragCancel(cx, cy, start, end);
            else holdDrag(cx, cy, start, end);
            return;
        }
        float width = right - left;
        long down = SystemClock.uptimeMillis();
        float span = start * width;
        boolean finished = false;
        try {
            event(down, MotionEvent.ACTION_DOWN, 1, cx, cy, span);
            event(down, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, cx, cy, span);
            for (int frame = 1; frame <= 24; frame++) {
                SystemClock.sleep(32);
                span = (start + (end - start) * frame / 24f) * width;
                event(down, MotionEvent.ACTION_MOVE, 2, cx, cy, span);
            }
            event(down, MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, cx, cy, span);
            event(down, MotionEvent.ACTION_UP, 1, cx, cy, span);
            finished = true;
            System.out.println("PINCH_INJECTED; verify the browser scale separately");
        } finally {
            if (!finished) try { event(down, MotionEvent.ACTION_CANCEL, 2, cx, cy, span); } catch (Exception ignored) { }
        }
    }

    private static void holdDrag(float x, float y, float endX, float endY) throws Exception {
        long down = SystemClock.uptimeMillis();
        float currentX = x, currentY = y;
        boolean finished = false;
        try {
            event(down, MotionEvent.ACTION_DOWN, 1, x, y, 0);
            SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 200L);
            for (int frame = 1; frame <= 24; frame++) {
                SystemClock.sleep(32);
                currentX = x + (endX - x) * frame / 24f;
                currentY = y + (endY - y) * frame / 24f;
                event(down, MotionEvent.ACTION_MOVE, 1, currentX, currentY, 0);
            }
            // Keep holding at the edge so edge scrolling can be observed before release.
            SystemClock.sleep(700);
            event(down, MotionEvent.ACTION_UP, 1, currentX, currentY, 0);
            finished = true;
            System.out.println("HOLD_DRAG_INJECTED; verify the resulting layout separately");
        } finally {
            if (!finished) try { event(down, MotionEvent.ACTION_CANCEL, 1, currentX, currentY, 0); } catch (Exception ignored) { }
        }
    }

    // Prepared only: midpoint and edge images are captured without lifting the finger.
    private static void holdDragCancel(float x, float y, float endX, float endY) throws Exception {
        long down = SystemClock.uptimeMillis();
        float currentX = x, currentY = y;
        boolean cancelled = false;
        try {
            event(down, MotionEvent.ACTION_DOWN, 1, x, y, 0);
            SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 200L);
            for (int frame = 1; frame <= 24; frame++) {
                SystemClock.sleep(32);
                currentX = x + (endX - x) * frame / 24f;
                currentY = y + (endY - y) * frame / 24f;
                event(down, MotionEvent.ACTION_MOVE, 1, currentX, currentY, 0);
                if (frame == 12) captureCancelStage("mid", down, currentX, currentY);
            }
            captureCancelStage("edge-start", down, currentX, currentY);
            SystemClock.sleep(700);
            captureCancelStage("edge-held", down, currentX, currentY);
            event(down, MotionEvent.ACTION_CANCEL, 1, currentX, currentY, 0);
            cancelled = true;
            SystemClock.sleep(350);
            captureCancelStage("cancelled", down, currentX, currentY);
            System.out.println("HOLD_DRAG_CANCEL_INJECTED; verify frames and model separately");
        } finally {
            if (!cancelled) try { event(down, MotionEvent.ACTION_CANCEL, 1, currentX, currentY, 0); } catch (Exception ignored) { }
        }
    }

    private static void captureCancelStage(String stage, long down, float x, float y) throws Exception {
        long started = SystemClock.uptimeMillis();
        System.out.println("CANCEL_STAGE_BEGIN stage=" + stage + " uptimeMs=" + started +
            " gestureElapsedMs=" + (started - down) + " x=" + x + " y=" + y);
        // Focus-check and screenshot latency are additional stationary hold time, not just the 700ms sleep.
        assertHostFocused();
        java.lang.Process capture = new ProcessBuilder("/system/bin/screencap", "-p",
            "/sdcard/gmo-edit-cancel-" + stage + ".png").start();
        try {
            if (!capture.waitFor(4, TimeUnit.SECONDS) || capture.exitValue() != 0)
                throw new IllegalStateException("Cancel-stage screenshot failed: " + stage);
        } finally { capture.destroy(); }
        long ended = SystemClock.uptimeMillis();
        System.out.println("CANCEL_STAGE_END stage=" + stage + " uptimeMs=" + ended +
            " captureAndFocusMs=" + (ended - started) + " x=" + x + " y=" + y);
    }

    private static void assertHostFocused() throws Exception {
        java.lang.Process check = new ProcessBuilder("/system/bin/dumpsys", "-t", "3", "window").start();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = check.getInputStream().read(buffer)) != -1) {
                if (bytes.size() + count > 512 * 1024) throw new IllegalStateException("Window dump too large");
                bytes.write(buffer, 0, count);
            }
            if (!check.waitFor(4, TimeUnit.SECONDS) || check.exitValue() != 0 ||
                    !Pattern.compile("mCurrentFocus=Window\\{[^\\r\\n]*\\bu0\\s+com\\.example\\.globalminusone(?:[ /}])")
                        .matcher(bytes.toString("UTF-8")).find())
                throw new SecurityException("The tested application does not own the focused window");
        } finally { check.destroy(); }
    }

    private static void event(long down, int action, int count, float cx, float cy, float span) throws Exception {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < count; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = i; properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coordinates[i] = new MotionEvent.PointerCoords();
            coordinates[i].x = cx + (i == 0 ? -span / 2 : span / 2); coordinates[i].y = cy;
            coordinates[i].pressure = 1; coordinates[i].size = 1;
        }
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, count, properties, coordinates,
                0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        try {
            if (!Boolean.TRUE.equals(inject.invoke(input, event, 1))) throw new IllegalStateException("Input injection rejected");
        } finally { event.recycle(); }
    }
}

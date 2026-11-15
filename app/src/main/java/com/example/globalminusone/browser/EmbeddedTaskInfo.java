package com.example.globalminusone.browser;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.Build;

/** TaskInfo was introduced in Q; older snapshots expose IDs on each subtype.
 * RunningTaskInfo had no public baseIntent before Q. Absence must never count
 * as a session marker or authorize task removal.
 */
final class EmbeddedTaskInfo {
    private EmbeddedTaskInfo() {}

    @SuppressWarnings("deprecation")
    static int taskId(ActivityManager.RunningTaskInfo task) {
        return Build.VERSION.SDK_INT >= 29 ? task.taskId : task.id;
    }

    @SuppressWarnings("deprecation")
    static int taskId(ActivityManager.RecentTaskInfo task) {
        return Build.VERSION.SDK_INT >= 29 ? task.taskId : task.persistentId;
    }

    static Intent baseIntent(ActivityManager.RunningTaskInfo task) {
        return Build.VERSION.SDK_INT >= 29 ? task.baseIntent : null;
    }
}

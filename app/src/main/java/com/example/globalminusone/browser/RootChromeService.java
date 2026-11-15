package com.example.globalminusone.browser;

import android.content.Intent;
import android.os.IBinder;
import androidx.annotation.Keep;
import com.topjohnwu.superuser.ipc.RootService;

/** Non-daemon, caller-scoped root service. No file or general shell operations are exposed. */
@Keep
public final class RootChromeService extends RootService {
    private ChromeBridge.Server bridge;
    @Override public IBinder onBind(Intent intent) {
        if (bridge == null) bridge = new ChromeBridge.Server(this, true);
        return bridge;
    }
    @Override public void onDestroy() {
        if (bridge != null) bridge.destroyEngine();
        super.onDestroy();
    }
}

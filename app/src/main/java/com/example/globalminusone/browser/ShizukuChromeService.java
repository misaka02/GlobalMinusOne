package com.example.globalminusone.browser;

import android.content.Context;
import android.os.Binder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import androidx.annotation.Keep;

/** Shizuku creates this object in its shell/root UserService; it is not an exported app service. */
@Keep
public final class ShizukuChromeService extends ChromeBridge.Server {
    @Keep public ShizukuChromeService(Context context) { super(context, true); }
    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == 16777115) {
            int caller = Binder.getCallingUid();
            if (caller != ownerUid && caller != Process.myUid()) throw new SecurityException("Not this Chrome service owner");
            destroyEngine();
            System.exit(0);
            return true;
        }
        return super.onTransact(code, data, reply, flags);
    }
}

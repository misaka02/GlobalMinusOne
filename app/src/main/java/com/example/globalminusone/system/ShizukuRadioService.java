package com.example.globalminusone.system;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import androidx.annotation.Keep;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/** Shell identity, six fixed radio operations only; no caller-supplied commands. */
@Keep
public final class ShizukuRadioService extends Binder {
    static final String DESCRIPTOR = "com.example.globalminusone.Radio.v1";
    private final int ownerUid;
    @Keep public ShizukuRadioService(Context context) { ownerUid = context.getApplicationInfo().uid; }
    @Override protected synchronized boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == INTERFACE_TRANSACTION) { reply.writeString(DESCRIPTOR); return true; }
        if (Binder.getCallingUid() != ownerUid && Binder.getCallingUid() != Process.myUid())
            throw new SecurityException("Not this radio session owner");
        if (code == 16777115) { System.exit(0); return true; }
        if (code != IBinder.FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags);
        data.enforceInterface(DESCRIPTOR);
        int action = data.readInt();
        if (data.dataAvail() != 0) throw new SecurityException("Unexpected radio payload");
        String command;
        switch (action) {
            case 0: command = "svc wifi enable"; break;
            case 1: command = "svc wifi disable"; break;
            case 2: command = "svc bluetooth enable"; break;
            case 3: command = "svc bluetooth disable"; break;
            case 4: command = "cmd wifi status"; break;
            case 5: command = "dumpsys bluetooth_manager"; break;
            default: throw new SecurityException("Unknown radio operation");
        }
        java.lang.Process process = null;
        long identity = Binder.clearCallingIdentity();
        try {
            process = new ProcessBuilder("/system/bin/timeout", "-k", "1s", "5s", "/system/bin/sh", "-c", command)
                    .redirectErrorStream(true).start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream in = process.getInputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = in.read(buffer)) != -1) {
                    if (output.size() + count > 128 * 1024) throw new IllegalStateException("Radio response exceeds limit");
                    output.write(buffer, 0, count);
                }
            }
            if (!process.waitFor(1, TimeUnit.SECONDS)) throw new IllegalStateException("Radio command timed out");
            reply.writeNoException(); reply.writeInt(process.exitValue()); reply.writeString(output.toString("UTF-8"));
        } catch (Exception error) {
            reply.writeException(new IllegalStateException("Shizuku radio operation failed", error));
        } finally {
            if (process != null) process.destroy();
            Binder.restoreCallingIdentity(identity);
        }
        return true;
    }
}

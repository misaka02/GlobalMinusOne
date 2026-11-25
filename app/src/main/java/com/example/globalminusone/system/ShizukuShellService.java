package com.example.globalminusone.system;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import androidx.annotation.Keep;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Shizuku UserService (ADB shell UID) exclusively for explicit user-configured Shell Command cards.
 * Restricted strictly to ownerUid callers and bounded by timeout & output cap.
 */
@Keep
public final class ShizukuShellService extends Binder {
    static final String DESCRIPTOR = "com.example.globalminusone.ShellCard.v1";
    private static final int MAX_OUTPUT_BYTES = 64 * 1024;
    private final int ownerUid;

    @Keep
    public ShizukuShellService(Context context) {
        this.ownerUid = context.getApplicationInfo().uid;
    }

    @Override
    protected synchronized boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == INTERFACE_TRANSACTION) {
            reply.writeString(DESCRIPTOR);
            return true;
        }
        if (Binder.getCallingUid() != ownerUid && Binder.getCallingUid() != Process.myUid()) {
            throw new SecurityException("Not shell card session owner");
        }
        if (code == 16777115) {
            System.exit(0);
            return true;
        }
        if (code != IBinder.FIRST_CALL_TRANSACTION) {
            return super.onTransact(code, data, reply, flags);
        }

        data.enforceInterface(DESCRIPTOR);
        String command = data.readString();
        int rawTimeoutSec = data.readInt();
        if (data.dataAvail() != 0) {
            throw new SecurityException("Unexpected shell payload");
        }
        if (command == null || command.trim().isEmpty() || command.length() > 4096) {
            throw new IllegalArgumentException("Shell command must be 1..4096 chars");
        }
        int timeoutSec = Math.max(2, Math.min(60, rawTimeoutSec));

        java.lang.Process process = null;
        long identity = Binder.clearCallingIdentity();
        try {
            File timeoutBin = new File("/system/bin/timeout");
            ProcessBuilder pb = timeoutBin.canExecute()
                ? new ProcessBuilder("/system/bin/timeout", "-k", "1s", timeoutSec + "s", "/system/bin/sh", "-c", command)
                : new ProcessBuilder("/system/bin/sh", "-c", command);
            process = pb.redirectErrorStream(true).start();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            boolean truncated = false;
            try (InputStream in = process.getInputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    int remaining = MAX_OUTPUT_BYTES - output.size();
                    if (remaining <= 0) {
                        truncated = true;
                        break;
                    }
                    int toWrite = Math.min(count, remaining);
                    output.write(buffer, 0, toWrite);
                    if (toWrite < count) {
                        truncated = true;
                        break;
                    }
                }
            }

            boolean finished = process.waitFor(timeoutSec + 2L, TimeUnit.SECONDS);
            boolean timedOut = !finished;
            if (timedOut) {
                process.destroyForcibly();
            }
            int exitCode = timedOut ? -1 : process.exitValue();
            if (exitCode == 124 || exitCode == 137) {
                timedOut = true;
            }
            String text = output.toString("UTF-8") + (truncated ? "\n（输出已截断）" : "");
            reply.writeNoException();
            reply.writeInt(timedOut ? 1 : 0);
            reply.writeInt(exitCode);
            reply.writeString(text);
        } catch (Exception error) {
            reply.writeException(new IllegalStateException("Shizuku shell card failed: " + error.getMessage(), error));
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
            Binder.restoreCallingIdentity(identity);
        }
        return true;
    }
}

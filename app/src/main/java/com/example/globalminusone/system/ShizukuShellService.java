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
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
            final boolean[] truncated = new boolean[] { false };
            final AtomicBoolean closingStdout = new AtomicBoolean(false);
            final IOException[] readError = new IOException[] { null };
            final InputStream stdout = process.getInputStream();
            Thread reader = new Thread(() -> {
                try (InputStream in = stdout) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        synchronized (output) {
                            int remaining = MAX_OUTPUT_BYTES - output.size();
                            if (remaining <= 0) {
                                truncated[0] = true;
                                break;
                            }
                            int toWrite = Math.min(count, remaining);
                            output.write(buffer, 0, toWrite);
                            if (toWrite < count) {
                                truncated[0] = true;
                                break;
                            }
                        }
                    }
                } catch (IOException error) {
                    if (!closingStdout.get()) {
                        readError[0] = error;
                    }
                }
            }, "gmo-shizuku-shell-read");
            reader.setDaemon(true);
            reader.start();

            boolean finished = process.waitFor(timeoutSec + 2L, TimeUnit.SECONDS);
            boolean timedOut = !finished;
            if (timedOut) {
                process.destroyForcibly();
            }
            reader.join(500L);
            if (reader.isAlive()) {
                truncated[0] = true;
                closingStdout.set(true);
                reader.interrupt();
                try {
                    stdout.close();
                } catch (Exception ignored) {
                }
                reader.join(200L);
            }
            if (!timedOut && !truncated[0] && readError[0] != null) {
                throw readError[0];
            }
            int exitCode = timedOut ? -1 : process.exitValue();
            if (exitCode == 124 || exitCode == 137) {
                timedOut = true;
            }
            final String capturedText;
            synchronized (output) {
                capturedText = output.toString("UTF-8");
            }
            String text = capturedText + (truncated[0] ? "\n（输出已截断）" : "");
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

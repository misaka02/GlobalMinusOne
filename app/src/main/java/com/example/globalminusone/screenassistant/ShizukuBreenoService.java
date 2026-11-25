package com.example.globalminusone.screenassistant;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import androidx.annotation.Keep;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Shell-identity Shizuku UserService restricted exclusively to validated OEM Breeno commands.
 * No arbitrary shell syntax or unverified package/component targets are accepted.
 */
@Keep
public final class ShizukuBreenoService extends Binder {
    public static final String DESCRIPTOR = "com.example.globalminusone.Breeno.v1";
    public static final int OP_DIRECT_SERVICE = 0;
    public static final int OP_QS_TILE = 1;
    private static final int MAX_STREAM_BYTES = 16 * 1024;

    private final int ownerUid;

    @Keep
    public ShizukuBreenoService(Context context) {
        this.ownerUid = context.getApplicationInfo().uid;
    }

    @Override
    protected synchronized boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == INTERFACE_TRANSACTION) {
            reply.writeString(DESCRIPTOR);
            return true;
        }
        int callingUid = Binder.getCallingUid();
        if (callingUid != ownerUid && callingUid != Process.myUid()) {
            throw new SecurityException("Not this Breeno session owner");
        }
        if (code == 16777115) {
            System.exit(0);
            return true;
        }
        if (code != IBinder.FIRST_CALL_TRANSACTION) {
            return super.onTransact(code, data, reply, flags);
        }

        data.enforceInterface(DESCRIPTOR);
        int op = data.readInt();
        String component = data.readString();
        if (data.dataAvail() != 0 || component == null) {
            throw new SecurityException("Unexpected Breeno payload");
        }

        final String command;
        if (op == OP_DIRECT_SERVICE) {
            if (!BreenoSystemProtocol.component.equals(component)) {
                throw new SecurityException("Unsupported Breeno direct service");
            }
            command = BreenoSystemProtocol.INSTANCE.command(component);
        } else if (op == OP_QS_TILE) {
            if (!BreenoTilePolicy.INSTANCE.validComponent(component)) {
                throw new SecurityException("Unsupported Breeno tile component");
            }
            command = BreenoTilePolicy.INSTANCE.command(component);
        } else {
            throw new SecurityException("Unknown Breeno operation");
        }

        java.lang.Process process = null;
        long identity = Binder.clearCallingIdentity();
        try {
            process = new ProcessBuilder(
                "/system/bin/timeout", "-k", "1s", "4s", "/system/bin/sh", "-c", command
            ).start();

            final InputStream errStream = process.getErrorStream();
            final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
            final IOException[] errFailure = new IOException[1];
            Thread errReader = new Thread(() -> {
                try {
                    drainBounded(errStream, errBuf);
                } catch (IOException e) {
                    errFailure[0] = e;
                }
            }, "shizuku-breeno-stderr");
            errReader.start();

            ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
            try (InputStream outStream = process.getInputStream()) {
                drainBounded(outStream, outBuf);
            }
            errReader.join(1000L);
            if (errFailure[0] != null) {
                throw errFailure[0];
            }
            if (!process.waitFor(5L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Breeno command timed out");
            }
            reply.writeNoException();
            reply.writeInt(process.exitValue());
            reply.writeString(outBuf.toString("UTF-8"));
            reply.writeString(errBuf.toString("UTF-8"));
        } catch (Exception error) {
            reply.writeException(new IllegalStateException("Shizuku Breeno operation failed", error));
        } finally {
            if (process != null) {
                process.destroy();
            }
            Binder.restoreCallingIdentity(identity);
        }
        return true;
    }

    private static void drainBounded(InputStream in, ByteArrayOutputStream out) throws IOException {
        byte[] buffer = new byte[4096];
        int count;
        while ((count = in.read(buffer)) != -1) {
            if (out.size() + count > MAX_STREAM_BYTES) {
                throw new IllegalStateException("Breeno response exceeds limit");
            }
            out.write(buffer, 0, count);
        }
    }
}

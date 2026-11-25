package com.example.globalminusone.browser;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.view.MotionEvent;
import android.view.Surface;

/** Caller-checked display sessions: explicit components only; no shell, profile or file operations. */
public final class ChromeBridge {
    private ChromeBridge() {}
    public static final String DESCRIPTOR = "com.example.globalminusone.browser.ChromeBridge.v1";
    private static final int OPEN = IBinder.FIRST_CALL_TRANSACTION;
    private static final int RESIZE = OPEN + 1, TOUCH = OPEN + 2, BACK = OPEN + 3,
            NAVIGATE = OPEN + 4, CLOSE = OPEN + 5, STATUS = OPEN + 6, COMMAND = OPEN + 7,
            NEW_SESSION = OPEN + 8, OPEN_APP = OPEN + 9, OUTPUT_SURFACE = OPEN + 10;

    public static final class Client {
        private final IBinder binder;
        private IBinder lifetime;
        public Client(IBinder binder) { this.binder = binder; }
        public IBinder binder() { return binder; }
        public Client newSession() throws RemoteException {
            Parcel input = Parcel.obtain(), output = Parcel.obtain();
            IBinder life = new Binder();
            try {
                input.writeInterfaceToken(DESCRIPTOR); input.writeStrongBinder(life);
                if (!binder.transact(NEW_SESSION, input, output, 0)) throw new RemoteException("Session protocol unavailable");
                output.readException();
                IBinder child = output.readStrongBinder();
                if (child == null) throw new RemoteException("Missing window session");
                Client result = new Client(child); result.lifetime = life; return result;
            } finally { input.recycle(); output.recycle(); }
        }
        public String openApp(Surface surface, int width, int height, int density, android.content.ComponentName component) throws RemoteException {
            return call(OPEN_APP, p -> { p.writeTypedObject(surface, 0); p.writeInt(width); p.writeInt(height);
                p.writeInt(density); p.writeTypedObject(component, 0); });
        }
        private String call(int code, Writer writer) throws RemoteException {
            Parcel input = Parcel.obtain(), output = Parcel.obtain();
            try {
                input.writeInterfaceToken(DESCRIPTOR);
                writer.write(input);
                if (!binder.transact(code, input, output, 0)) throw new RemoteException("Chrome bridge unsupported operation");
                output.readException();
                String value = output.readString();
                return value == null ? "" : value;
            } finally { output.recycle(); input.recycle(); }
        }
        public String open(Surface surface, int width, int height, int density, String pkg, String url) throws RemoteException {
            return call(OPEN, p -> { p.writeTypedObject(surface, 0); p.writeInt(width); p.writeInt(height);
                p.writeInt(density); p.writeString(pkg); p.writeString(url); });
        }
        public String resize(int width, int height, int density) throws RemoteException {
            return call(RESIZE, p -> { p.writeInt(width); p.writeInt(height); p.writeInt(density); });
        }
        public String outputSurface(Surface surface, int width, int height, int density) throws RemoteException {
            return call(OUTPUT_SURFACE, p -> { p.writeTypedObject(surface, 0); p.writeInt(width); p.writeInt(height); p.writeInt(density); });
        }
        public String touch(MotionEvent event) throws RemoteException {
            return call(TOUCH, p -> p.writeTypedObject(event, 0));
        }
        public String back() throws RemoteException { return call(BACK, p -> {}); }
        public String navigate(String url) throws RemoteException { return call(NAVIGATE, p -> p.writeString(url)); }
        public String close() throws RemoteException { return call(CLOSE, p -> {}); }
        public String status() throws RemoteException { return call(STATUS, p -> {}); }
        public String command(ChromeCommand command) throws RemoteException { return call(COMMAND, p -> p.writeInt(command.ordinal())); }
    }
    private interface Writer { void write(Parcel parcel); }

    public static class Server extends Binder {
        protected final int ownerUid;
        private final PrivilegedChromeEngine engine;
        private final android.content.Context context;
        private final boolean privileged;
        private final Object sessionLock = new Object();
        private final java.util.Set<Server> sessions = java.util.concurrent.ConcurrentHashMap.newKeySet();
        private Server parent;
        private IBinder lifetime;
        private IBinder.DeathRecipient lifetimeDeath;
        private volatile boolean retired;
        public Server(android.content.Context context, boolean privileged) {
            this.context = context; this.privileged = privileged;
            ownerUid = context.getApplicationInfo().uid;
            engine = new PrivilegedChromeEngine(context, privileged);
            attachInterface(null, DESCRIPTOR);
        }
        public void destroyEngine() {
            Server[] children;
            IBinder oldLifetime;
            IBinder.DeathRecipient oldDeath;
            synchronized (sessionLock) {
                retired = true;
                children = sessions.toArray(new Server[0]); sessions.clear();
                oldLifetime = lifetime; oldDeath = lifetimeDeath;
                lifetime = null; lifetimeDeath = null;
            }
            // Never enter an engine or child while holding a parent/session lock.
            // A death callback can therefore cancel an opening window immediately.
            engine.retire();
            for (Server child : children) child.destroyEngine();
            if (parent != null) parent.sessions.remove(this);
            if (oldLifetime != null && oldDeath != null) {
                try { oldLifetime.unlinkToDeath(oldDeath, 0); } catch (java.util.NoSuchElementException ignored) { }
            }
        }
        private Server newSession(IBinder life) throws RemoteException {
            Server child;
            synchronized (sessionLock) {
                if (retired || parent != null || sessions.size() >= 8)
                    throw new IllegalStateException("Window session limit reached or factory ended");
                child = new Server(context, privileged); child.parent = this;
                sessions.add(child);
            }
            try {
                synchronized (child.sessionLock) {
                    if (child.retired) throw new IllegalStateException("Window session has ended");
                    child.lifetime = life;
                    child.lifetimeDeath = child::destroyEngine;
                    life.linkToDeath(child.lifetimeDeath, 0);
                }
                return child;
            } catch (RemoteException | RuntimeException failure) { child.destroyEngine(); throw failure; }
        }
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) { reply.writeString(DESCRIPTOR); return true; }
            if (Binder.getCallingUid() != ownerUid) throw new SecurityException("Chrome bridge caller does not own this session");
            data.enforceInterface(DESCRIPTOR);
            String result;
            long identity = Binder.clearCallingIdentity();
            try {
                if (retired) throw new IllegalStateException("Window session has ended");
                switch (code) {
                    case OUTPUT_SURFACE: {
                        Surface output = data.readTypedObject(Surface.CREATOR);
                        try (EmbeddedSurfaceInput<Surface> input = new EmbeddedSurfaceInput<>(output, Surface::release)) {
                            int width = data.readInt(), height = data.readInt(), density = data.readInt();
                            noExtraData(data);
                            // Parse failures remain ours; the engine consumes a
                            // transferred input even if a later display step fails.
                            result = engine.outputSurface(input.transfer(), width, height, density);
                        }
                        break;
                    }
                    case NEW_SESSION: {
                        IBinder life = data.readStrongBinder(); noExtraData(data);
                        if (life == null) throw new IllegalArgumentException("Missing owner lifetime");
                        Server child = newSession(life);
                        reply.writeNoException(); reply.writeStrongBinder(child); return true;
                    }
                    case OPEN_APP: {
                        Surface surface = data.readTypedObject(Surface.CREATOR);
                        int width = data.readInt(), height = data.readInt(), density = data.readInt();
                        android.content.ComponentName component = data.readTypedObject(android.content.ComponentName.CREATOR);
                        noExtraData(data);
                        try { result = engine.openApp(surface, width, height, density, component); }
                        catch (Exception failure) { if (surface != null) surface.release(); throw failure; }
                        break;
                    }
                    case OPEN: {
                        Surface surface = data.readTypedObject(Surface.CREATOR);
                        int width = data.readInt(), height = data.readInt(), density = data.readInt();
                        String pkg = data.readString(), url = data.readString();
                        noExtraData(data);
                        try { result = engine.open(surface, width, height, density, pkg, url); }
                        catch (Exception failure) { if (surface != null) surface.release(); throw failure; }
                        break;
                    }
                    case RESIZE: {
                        int width = data.readInt(), height = data.readInt(), density = data.readInt();
                        noExtraData(data); result = engine.resize(width, height, density); break;
                    }
                    case TOUCH: {
                        MotionEvent event = data.readTypedObject(MotionEvent.CREATOR);
                        noExtraData(data);
                        if (event == null) throw new IllegalArgumentException("Missing touch event");
                        try { result = engine.touch(event); } finally { event.recycle(); }
                        break;
                    }
                    case BACK: noExtraData(data); result = engine.back(); break;
                    case NAVIGATE: { String url = data.readString(); noExtraData(data); result = engine.navigate(url); break; }
                    case CLOSE: noExtraData(data); destroyEngine(); result = engine.status(); break;
                    case STATUS: noExtraData(data); result = engine.heartbeatStatus(); break;
                    case COMMAND: { ChromeCommand command = ChromeCommand.fromWire(data.readInt()); noExtraData(data); result = engine.command(command); break; }
                    default: return super.onTransact(code, data, reply, flags);
                }
                reply.writeNoException(); reply.writeString(result);
            } catch (Exception failure) {
                // Return an explicit failure, never fall back to a normal full-screen launch.
                reply.writeException(new IllegalStateException(PrivilegedChromeEngine.failureMessage(failure)));
            } finally { Binder.restoreCallingIdentity(identity); }
            return true;
        }
        private static void noExtraData(Parcel data) {
            if (data.dataAvail() != 0) throw new IllegalArgumentException("Unexpected Chrome bridge arguments");
        }
    }
}

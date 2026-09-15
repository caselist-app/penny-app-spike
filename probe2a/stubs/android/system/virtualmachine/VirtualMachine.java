package android.system.virtualmachine;

import android.os.ParcelFileDescriptor;

import java.util.concurrent.Executor;

/**
 * COMPILE-ONLY STUB. Never packaged into the APK — see probe2a/build.sh.
 *
 * A handle on one VM owned by this app. getCid() is the vsock address the
 * host uses to reach the guest; it is also what `vm list` prints, so it is
 * the number that ties what the app thinks it owns to what
 * VirtualizationService says exists.
 */
public class VirtualMachine implements AutoCloseable {

    public static final int STATUS_STOPPED = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_DELETED = 2;

    public void run() throws VirtualMachineException {
        throw new UnsupportedOperationException("stub");
    }

    public void stop() throws VirtualMachineException {
        throw new UnsupportedOperationException("stub");
    }

    public int getStatus() {
        throw new UnsupportedOperationException("stub");
    }

    public int getCid() throws VirtualMachineException {
        throw new UnsupportedOperationException("stub");
    }

    public String getName() {
        throw new UnsupportedOperationException("stub");
    }

    public VirtualMachineConfig getConfig() {
        throw new UnsupportedOperationException("stub");
    }

    public void setCallback(Executor executor, VirtualMachineCallback callback) {
        throw new UnsupportedOperationException("stub");
    }

    public void clearCallback() {
        throw new UnsupportedOperationException("stub");
    }


    /**
     * Rung 3c. The host end of the host/guest byte channel, and the only one
     * that is reachable from a sideloaded app.
     *
     * The signature is (J) — a long, not an int. That was read off the
     * device's own framework-virtualization.jar with dexdump rather than
     * recalled, together with the flag that matters:
     *
     *     connectVsock  (J)Landroid/os/ParcelFileDescriptor;  SDK,TEST-API
     *     getConsoleInput ()Ljava/io/OutputStream;             BLOCKED,TEST-API
     *
     * So the console is outbound only and this is the single remaining way in.
     * Getting the argument width wrong would present as NoSuchMethodError,
     * which on this device is also exactly how a hidden-API block presents —
     * see the trap in CLAUDE.md.
     */
    public ParcelFileDescriptor connectVsock(long port) throws VirtualMachineException {
        throw new UnsupportedOperationException("stub");
    }

    /** Ports outside MIN_VSOCK_PORT..MAX_VSOCK_PORT are rejected host-side,
     *  before anything reaches the guest. Values read off the device. */
    public static final long MIN_VSOCK_PORT = 1024L;
    public static final long MAX_VSOCK_PORT = 4294967295L;

    @Override
    public void close() {
        throw new UnsupportedOperationException("stub");
    }
}

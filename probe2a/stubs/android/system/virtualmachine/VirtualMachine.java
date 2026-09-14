package android.system.virtualmachine;

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

    @Override
    public void close() {
        throw new UnsupportedOperationException("stub");
    }
}

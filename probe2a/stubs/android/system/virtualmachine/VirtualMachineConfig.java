package android.system.virtualmachine;

import android.content.Context;

/**
 * COMPILE-ONLY STUB. Never packaged into the APK — see probe2a/build.sh.
 *
 * Only the members rung 2b actually calls are declared. The real class has
 * more; a stub does not have to be complete, it has to be *correct* for what
 * it declares, because the runtime resolves against the real class by exact
 * signature. A wrong signature here surfaces as NoSuchMethodError, which is
 * easy to misread as a platform refusal — the trap CLAUDE.md records.
 */
public class VirtualMachineConfig {

    public static final int DEBUG_LEVEL_NONE = 0;
    public static final int DEBUG_LEVEL_FULL = 1;

    public static final int CPU_TOPOLOGY_ONE_CPU = 0;
    public static final int CPU_TOPOLOGY_MATCH_HOST = 1;

    public String getApkPath() {
        throw new UnsupportedOperationException("stub");
    }

    public String getPayloadBinaryName() {
        throw new UnsupportedOperationException("stub");
    }

    public String getOs() {
        throw new UnsupportedOperationException("stub");
    }

    public boolean isProtectedVm() {
        throw new UnsupportedOperationException("stub");
    }

    public int getDebugLevel() {
        throw new UnsupportedOperationException("stub");
    }

    public long getMemoryBytes() {
        throw new UnsupportedOperationException("stub");
    }

    public static class Builder {

        public Builder(Context context) {
            throw new UnsupportedOperationException("stub");
        }

        public Builder setApkPath(String apkPath) {
            throw new UnsupportedOperationException("stub");
        }

        public Builder setPayloadBinaryName(String name) {
            throw new UnsupportedOperationException("stub");
        }

        public Builder setDebugLevel(int level) {
            throw new UnsupportedOperationException("stub");
        }

        public Builder setProtectedVm(boolean p) {
            throw new UnsupportedOperationException("stub");
        }

        public Builder setMemoryBytes(long bytes) {
            throw new UnsupportedOperationException("stub");
        }

        /** Rung 3e-iii. The one thing in this API that is not RAM: microdroid
         *  can be given a real, host-backed, encrypted disk. Read off the
         *  device's own dex as hiddenapi 0x0020 (SDK,TEST-API) BEFORE this
         *  stub was written — the same flag setApkPath and setMemoryBytes
         *  carry — so it is callable from an app in the `app` domain. Takes a
         *  long, like setMemoryBytes. */
        public Builder setEncryptedStorageBytes(long bytes) {
            throw new UnsupportedOperationException("stub");
        }

        public Builder setCpuTopology(int topology) {
            throw new UnsupportedOperationException("stub");
        }

        public Builder setVmOutputCaptured(boolean captured) {
            throw new UnsupportedOperationException("stub");
        }

        /** Rung 2c. The fork in the road: a config carrying one of these is a
         *  custom VM, which is what USE_CUSTOM_VIRTUAL_MACHINE guards. The
         *  real build() throws IllegalStateException if this is combined with
         *  setPayloadBinaryName or setPayloadConfigPath — they are the two
         *  mutually exclusive ways to say what the VM should run. */
        public Builder setCustomImageConfig(VirtualMachineCustomImageConfig config) {
            throw new UnsupportedOperationException("stub");
        }

        public VirtualMachineConfig build() {
            throw new UnsupportedOperationException("stub");
        }
    }
}

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

        public Builder setCpuTopology(int topology) {
            throw new UnsupportedOperationException("stub");
        }

        public Builder setVmOutputCaptured(boolean captured) {
            throw new UnsupportedOperationException("stub");
        }

        public VirtualMachineConfig build() {
            throw new UnsupportedOperationException("stub");
        }
    }
}

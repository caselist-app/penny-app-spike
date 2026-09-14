package android.system.virtualmachine;

import java.util.List;

/**
 * COMPILE-ONLY STUB. Never packaged into the APK — see probe2a/build.sh.
 *
 * Obtained at runtime with context.getSystemService(VirtualMachineManager.class).
 * There is NO getInstance(Context) — rung 2a proved that by enumeration, and
 * it is recorded as a trap in CLAUDE.md.
 */
public class VirtualMachineManager {

    public static final int CAPABILITY_PROTECTED_VM = 1;
    public static final int CAPABILITY_NON_PROTECTED_VM = 2;

    public int getCapabilities() {
        throw new UnsupportedOperationException("stub");
    }

    public VirtualMachine create(String name, VirtualMachineConfig config)
            throws VirtualMachineException {
        throw new UnsupportedOperationException("stub");
    }

    public VirtualMachine getOrCreate(String name, VirtualMachineConfig config)
            throws VirtualMachineException {
        throw new UnsupportedOperationException("stub");
    }

    public VirtualMachine get(String name) throws VirtualMachineException {
        throw new UnsupportedOperationException("stub");
    }

    public void delete(String name) throws VirtualMachineException {
        throw new UnsupportedOperationException("stub");
    }

    public List<String> getSupportedOSList() {
        throw new UnsupportedOperationException("stub");
    }
}

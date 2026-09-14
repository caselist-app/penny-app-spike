package android.system.virtualmachine;

/**
 * COMPILE-ONLY STUB. Never packaged into the APK — see probe2a/build.sh.
 *
 * The real class lives on the device, in the framework-virtualization boot
 * classpath jar inside the com.android.virt APEX. It is @SystemApi and so is
 * absent from the public android.jar, which is the only reason this file
 * exists: javac needs a shape to compile against. At runtime the real class
 * is loaded by BootClassLoader (measured in rung 2a) and this one is not
 * present at all.
 *
 * Signatures were read off the device's own dex, not from documentation.
 */
public class VirtualMachineException extends Exception {

    public static final int CODE_INTERNAL = 0;
    public static final int CODE_NAME_ALREADY_EXISTS = 0;
    public static final int CODE_VIRTUAL_MACHINE_RUNNING = 0;
    public static final int CODE_VIRTUAL_MACHINE_STOPPED = 0;
    public static final int CODE_VIRTUAL_MACHINE_DELETED = 0;
    public static final int CODE_CONFIG_INCOMPATIBLE = 0;

    public int getCode() {
        throw new UnsupportedOperationException("stub");
    }
}

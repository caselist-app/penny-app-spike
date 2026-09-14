package android.system.virtualmachine;

/**
 * COMPILE-ONLY STUB. Never packaged into the APK — see probe2a/build.sh.
 *
 * The real interface is what VirtualizationService calls back into as the
 * guest boots. onPayloadReady is the signal that microdroid actually came up
 * and started the payload; onError and onStopped carry the refusal codes that
 * a failed 2b would arrive as.
 */
public interface VirtualMachineCallback {

    int ERROR_UNKNOWN = 0;
    int ERROR_PAYLOAD_VERIFICATION_FAILED = 1;
    int ERROR_PAYLOAD_CHANGED = 2;
    int ERROR_PAYLOAD_INVALID_CONFIG = 3;

    void onPayloadStarted(VirtualMachine vm);

    void onPayloadReady(VirtualMachine vm);

    void onPayloadFinished(VirtualMachine vm, int exitCode);

    void onError(VirtualMachine vm, int errorCode, String message);

    void onStopped(VirtualMachine vm, int reason);
}

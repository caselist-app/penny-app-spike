package com.pennyspike.probe2a;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import android.system.virtualmachine.VirtualMachine;
import android.system.virtualmachine.VirtualMachineCallback;
import android.system.virtualmachine.VirtualMachineConfig;
import android.system.virtualmachine.VirtualMachineException;
import android.system.virtualmachine.VirtualMachineManager;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Rung 2d: does the sideloaded app's VM run OUR code?
 *
 * WHAT THIS IS NOT. It is not a retry of 2c. 2c asked for a custom guest — our
 * own kernel and root filesystem — and was refused inside our own process by
 * the hidden-API blocklist, four members out of four. That door is shut and is
 * not being knocked on again here.
 *
 * WHAT THIS IS. 2b booted Google's microdroid running Google's payload. The
 * two calls that chose it — setApkPath() and setPayloadBinaryName() — are both
 * on the SDK side of the blocklist and both already proven on this device;
 * setApkPath was the known-good control in the run where the four custom-VM
 * members were refused. So the question is only whether those two calls will
 * point at OUR APK and OUR binary instead of the APEX's. Same microdroid, same
 * kernel, same permissions. One thing varies: whose code is inside.
 *
 * WHY IT MATTERS MORE THAN IT LOOKS. Everything proven so far is about
 * ownership and timing — the app owns a VM, and the VM is up 14 seconds after
 * power-on with the phone locked. None of it does anything, because the guest
 * has always been an empty payload that exits. If this answers yes, the VM
 * stops being a demonstration and becomes somewhere to put software.
 *
 * DONE MEANS three independent signals, not one:
 *   onPayloadReady   fires only because our C called AVmPayload_notifyPayloadReady
 *   onPayloadFinished arrives with exitCode 42, which the stock payload cannot produce
 *   the guest console carries the PENNY2D lines
 *
 * Run it unlocked, in the foreground, over adb:
 *   adb shell am force-stop com.pennyspike.probe2a
 *   adb shell am start -n com.pennyspike.probe2a/.Probe2dActivity
 */
public class Probe2dActivity extends Activity {

    static final String TAG = "PENNY2D";

    private static final String VM_NAME = "penny2d";

    /** Must match the file built into lib/arm64-v8a/ by build.sh. */
    private static final String PAYLOAD = "PennyPayload.so";

    /** Held for the life of the process — the VM dies with the handle. */
    private VirtualMachine mVm;

    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Log.i(TAG, "==== rung 2d: our own payload ==== uid=" + Process.myUid()
                + " sinceBoot=" + SystemClock.elapsedRealtime() + "ms");

        mExecutor.execute(() -> {
            try {
                boot();
            } catch (Throwable t) {
                Log.e(TAG, "FAILED -> " + t.getClass().getName()
                        + ": " + t.getMessage(), t);
                Log.e(TAG, "VERDICT 2d: no VM came up with our payload");
            }
        });
    }

    private void boot() throws Exception {
        // Device-protected storage, exactly as VmService uses it. Not needed
        // while unlocked, but this code has to survive being moved into the
        // boot path later, and the VM directory is the one thing that breaks
        // there. Keep the two identical so nothing new is learned at 9am on
        // the day it moves.
        Context ctx = createDeviceProtectedStorageContext();

        Log.i(TAG, "STEP1 MANAGE_VIRTUAL_MACHINE = " + perm(
                "android.permission.MANAGE_VIRTUAL_MACHINE"));

        VirtualMachineManager vmm = ctx.getSystemService(VirtualMachineManager.class);
        Log.i(TAG, "STEP2 manager = " + vmm);
        if (vmm == null) {
            Log.e(TAG, "VERDICT 2d: no manager");
            return;
        }

        // OUR apk, not the APEX's. This is the single substantive change from
        // rung 2b and rung 3. Read from the package manager rather than
        // constructed, because the path carries an install-time random suffix
        // (/data/app/~~<random>/com.pennyspike.probe2a-<random>/base.apk).
        String apkPath = getApplicationInfo().sourceDir;
        File apk = new File(apkPath);
        Log.i(TAG, "STEP3 our apk = " + apkPath
                + " readable=" + apk.canRead() + " bytes=" + apk.length());

        VirtualMachineConfig config = new VirtualMachineConfig.Builder(ctx)
                .setApkPath(apkPath)
                .setPayloadBinaryName(PAYLOAD)
                .setDebugLevel(VirtualMachineConfig.DEBUG_LEVEL_FULL)
                .setProtectedVm(false)
                .setMemoryBytes(256L * 1024L * 1024L)
                .setCpuTopology(VirtualMachineConfig.CPU_TOPOLOGY_ONE_CPU)
                .build();
        Log.i(TAG, "STEP4 config built, payload=" + PAYLOAD);

        try {
            vmm.delete(VM_NAME);
            Log.i(TAG, "STEP5 deleted a pre-existing VM named " + VM_NAME);
        } catch (VirtualMachineException e) {
            Log.i(TAG, "STEP5 no pre-existing VM to delete (" + e.getMessage() + ")");
        }

        mVm = vmm.create(VM_NAME, config);
        Log.i(TAG, "STEP6 create() returned " + mVm + " name=" + mVm.getName());

        mVm.setCallback(mExecutor, new Callbacks());
        mVm.run();
        Log.i(TAG, "STEP7 run() returned, status=" + status(mVm)
                + " sinceBoot=" + SystemClock.elapsedRealtime() + "ms");
        Log.i(TAG, "STEP8 now waiting. Verdict is decided by the callbacks below,"
                + " not by run() returning.");
    }

    private String perm(String name) {
        return checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
                ? "GRANTED" : "DENIED";
    }

    private static String status(VirtualMachine vm) {
        int s = vm.getStatus();
        if (s == VirtualMachine.STATUS_RUNNING) return "RUNNING";
        if (s == VirtualMachine.STATUS_STOPPED) return "STOPPED";
        if (s == VirtualMachine.STATUS_DELETED) return "DELETED";
        return "UNKNOWN(" + s + ")";
    }

    private static class Callbacks implements VirtualMachineCallback {

        @Override
        public void onPayloadStarted(VirtualMachine vm) {
            Log.i(TAG, "CB onPayloadStarted sinceBoot="
                    + SystemClock.elapsedRealtime() + "ms");
        }

        @Override
        public void onPayloadReady(VirtualMachine vm) {
            // This one cannot happen by accident. Microdroid does not decide a
            // payload is ready; the payload says so, by calling
            // AVmPayload_notifyPayloadReady(). Reaching here means our C ran.
            Log.i(TAG, "CB onPayloadReady sinceBoot="
                    + SystemClock.elapsedRealtime() + "ms");
            Log.i(TAG, "SIGNAL 1 of 3: our payload called notifyPayloadReady");
        }

        @Override
        public void onPayloadFinished(VirtualMachine vm, int exitCode) {
            Log.i(TAG, "CB onPayloadFinished exitCode=" + exitCode);
            if (exitCode == 42) {
                Log.i(TAG, "SIGNAL 2 of 3: exit code 42 — ours, not the stock payload's");
                Log.i(TAG, "VERDICT 2d: YES, provided the guest console also"
                        + " carries the PENNY2D lines (signal 3).");
            } else {
                Log.w(TAG, "exit code is not 42 — do NOT call this a yes");
            }
        }

        @Override
        public void onError(VirtualMachine vm, int errorCode, String message) {
            Log.e(TAG, "CB onError code=" + errorCode + " message=" + message);
            Log.e(TAG, "VERDICT 2d: NO — read the code and message above before"
                    + " guessing. A verification failure and a missing file are"
                    + " different answers.");
        }

        @Override
        public void onStopped(VirtualMachine vm, int reason) {
            Log.w(TAG, "CB onStopped reason=" + reason);
        }
    }
}

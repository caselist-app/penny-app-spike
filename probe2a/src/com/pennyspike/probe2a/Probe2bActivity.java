package com.pennyspike.probe2a;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
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
 * Rung 2b: does a sideloaded app OWN a VM running Google's stock microdroid
 * payload?
 *
 * Rung 2a proved the app can hold a VirtualMachineManager and call it. That
 * proved nothing about creating anything. This creates one.
 *
 * Unlike 2a, nothing here is reflection. The android.system.virtualmachine
 * classes are named directly and compiled against hand-written stubs in
 * probe2a/stubs, which are NOT packaged into the APK — at runtime the real
 * classes load from BootClassLoader. The stub signatures were read off the
 * device's own dex, because a wrong signature fails as NoSuchMethodError and
 * that is indistinguishable, at a glance, from the platform refusing us.
 *
 * The payload is Google's, not ours. EmptyPayloadApp.apk ships inside the
 * com.android.virt APEX, is world-readable, and contains one native binary.
 * We point a VM at it. Running OUR OWN guest is rung 2c and needs a custom
 * config — a different question, deliberately not asked here.
 *
 * DONE MEANS, and this is checked from outside the app, not from this log:
 * `vm list` shows a VM whose requesterUid is this app's uid, and the Terminal
 * app knows nothing about it.
 */
public class Probe2bActivity extends Activity {

    private static final String TAG = "PENNY2B";
    private static final String VM_NAME = "penny2b";

    /** Held for the life of the activity. The VM belongs to this process; if
     *  the handle is collected or the process dies, the VM goes with it. */
    private VirtualMachine mVm;

    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "==== rung 2b probe start ====");
        Log.i(TAG, "uid=" + Process.myUid() + " pkg=" + getPackageName());

        try {
            runProbe();
        } catch (Throwable t) {
            Log.e(TAG, "FAILED -> " + t.getClass().getName() + ": " + t.getMessage(), t);
            Log.e(TAG, "VERDICT 2b: no VM created");
        }
    }

    private void runProbe() throws Exception {
        VirtualMachineManager vmm = getSystemService(VirtualMachineManager.class);
        Log.i(TAG, "STEP1 manager = " + vmm);
        if (vmm == null) {
            Log.e(TAG, "VERDICT 2b: no manager — 2a's result did not reproduce");
            return;
        }

        // STEP 2 — locate Google's stock payload. The APEX directory carries
        // the build ID in its name, so resolve it rather than hard-code it.
        String apkPath = findStockPayloadApk();
        Log.i(TAG, "STEP2 stock payload apk = " + apkPath);
        if (apkPath == null) {
            Log.e(TAG, "VERDICT 2b: stock payload APK not found");
            return;
        }

        // STEP 3 — build the config. Non-protected because this device
        // supports nothing else (measured in 2a: getCapabilities() = 2).
        // DEBUG_LEVEL_FULL so a refusal is legible; it also means the guest
        // boots with sample DICE values and attests nothing. That is fine
        // here — 2b is an ownership question, not an attestation one.
        VirtualMachineConfig config = new VirtualMachineConfig.Builder(this)
                .setApkPath(apkPath)
                .setPayloadBinaryName("MicrodroidEmptyPayloadJniLib.so")
                .setDebugLevel(VirtualMachineConfig.DEBUG_LEVEL_FULL)
                .setProtectedVm(false)
                .setMemoryBytes(256L * 1024L * 1024L)
                .setCpuTopology(VirtualMachineConfig.CPU_TOPOLOGY_ONE_CPU)
                .build();
        Log.i(TAG, "STEP3 config built"
                + " apk=" + config.getApkPath()
                + " payload=" + config.getPayloadBinaryName()
                + " protected=" + config.isProtectedVm()
                + " os=" + safeOs(config));

        // A VM of this name may survive from an earlier run with a different
        // config, which create() would reject. Delete first; absence is fine.
        try {
            vmm.delete(VM_NAME);
            Log.i(TAG, "STEP4 deleted a pre-existing VM named " + VM_NAME);
        } catch (VirtualMachineException e) {
            Log.i(TAG, "STEP4 no pre-existing VM to delete (" + e.getMessage() + ")");
        }

        // STEP 5 — the actual question. This is the first time this app has
        // asked VirtualizationService to CREATE something rather than read.
        mVm = vmm.create(VM_NAME, config);
        Log.i(TAG, "STEP5 create() returned " + mVm + " name=" + mVm.getName());

        mVm.setCallback(mExecutor, new Callbacks());

        // STEP 6 — boot it.
        mVm.run();
        Log.i(TAG, "STEP6 run() returned, status=" + status(mVm));

        // The CID is the guest's vsock address and is what `vm list` prints,
        // so it is the number that ties this app's handle to what
        // VirtualizationService believes exists.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                Log.i(TAG, "STEP7 after 5s: status=" + status(mVm) + " cid=" + mVm.getCid());
                Log.i(TAG, "VERDICT 2b: VM created and running — now confirm"
                        + " requesterUid with `vm list` from the host");
            } catch (Throwable t) {
                Log.e(TAG, "STEP7 failed -> " + t, t);
            }
        }, 5000);

        Log.i(TAG, "==== rung 2b probe handed off to callbacks ====");
    }

    /** The APEX path carries the OS build ID, e.g. EmptyPayloadApp@CP2A.260705.006. */
    private static String findStockPayloadApk() {
        File dir = new File("/apex/com.android.virt/app");
        File[] children = dir.listFiles();
        if (children == null) {
            Log.w(TAG, "cannot list " + dir + " — app may lack access");
            return null;
        }
        for (File c : children) {
            if (c.getName().startsWith("EmptyPayloadApp")) {
                File apk = new File(c, "EmptyPayloadApp.apk");
                if (apk.canRead()) return apk.getAbsolutePath();
                Log.w(TAG, "found " + apk + " but cannot read it");
            }
        }
        return null;
    }

    private static String safeOs(VirtualMachineConfig c) {
        try {
            return c.getOs();
        } catch (Throwable t) {
            return "<unreadable: " + t + ">";
        }
    }

    private static String status(VirtualMachine vm) {
        int s = vm.getStatus();
        if (s == VirtualMachine.STATUS_RUNNING) return "RUNNING";
        if (s == VirtualMachine.STATUS_STOPPED) return "STOPPED";
        if (s == VirtualMachine.STATUS_DELETED) return "DELETED";
        return "UNKNOWN(" + s + ")";
    }

    /** The guest reports progress here. onPayloadReady is the boot signal. */
    private static class Callbacks implements VirtualMachineCallback {

        @Override
        public void onPayloadStarted(VirtualMachine vm) {
            Log.i(TAG, "CB onPayloadStarted");
        }

        @Override
        public void onPayloadReady(VirtualMachine vm) {
            Log.i(TAG, "CB onPayloadReady — microdroid booted the stock payload");
        }

        @Override
        public void onPayloadFinished(VirtualMachine vm, int exitCode) {
            Log.i(TAG, "CB onPayloadFinished exitCode=" + exitCode);
        }

        @Override
        public void onError(VirtualMachine vm, int errorCode, String message) {
            Log.e(TAG, "CB onError code=" + errorCode + " message=" + message);
        }

        @Override
        public void onStopped(VirtualMachine vm, int reason) {
            Log.w(TAG, "CB onStopped reason=" + reason);
        }
    }
}

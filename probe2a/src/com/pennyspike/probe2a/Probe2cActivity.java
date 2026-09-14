package com.pennyspike.probe2a;

import android.app.Activity;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import android.system.virtualmachine.VirtualMachine;
import android.system.virtualmachine.VirtualMachineCallback;
import android.system.virtualmachine.VirtualMachineConfig;
import android.system.virtualmachine.VirtualMachineCustomImageConfig;
import android.system.virtualmachine.VirtualMachineException;
import android.system.virtualmachine.VirtualMachineManager;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Rung 2c: does a sideloaded app own a VM running a CUSTOM guest — a kernel
 * and a root filesystem of its own choosing — rather than the stock payload
 * 2b booted inside Google's microdroid?
 *
 * 2b and 2c differ by ONE thing: which config object gets built. 2b called
 * setPayloadBinaryName(); 2c calls setCustomImageConfig(). Same app, same
 * uid, same create() call. That is the whole experiment.
 *
 * The guest here is still Google's — microdroid_kernel and microdroid.img out
 * of the virt APEX, world-readable, assembled exactly as
 * /apex/com.android.virt/etc/microdroid.json assembles them. Borrowing their
 * image keeps this cheap and isolates the variable: if this is refused, it is
 * refused for being a custom VM, not for the image being ours.
 *
 * THERE ARE TWO GATES AND THEY ARE IN SERIES. Static reading of the device's
 * own dex says every member of the custom-image API is marked BLOCKED in the
 * hidden-API metadata, while the 2b members that worked are marked SDK. If
 * that is right, this app is stopped by the language-level hidden-API gate
 * before VirtualizationService ever gets asked about the permission. So each
 * stage below is isolated in its own try block: the point is to learn WHICH
 * gate fires, not merely that something failed.
 *
 *   NoSuchMethodError / NoSuchFieldError -> hidden-API gate (in our process)
 *   SecurityException from create()      -> permission gate (in the service)
 *   a booted VM                          -> neither; 2c is YES
 */
public class Probe2cActivity extends Activity {

    private static final String TAG = "PENNY2C";
    private static final String VM_NAME = "penny2c";

    private static final String FS = "/apex/com.android.virt/etc/fs/";
    private static final String KERNEL = FS + "microdroid_kernel";
    private static final String SYSTEM_IMG = FS + "microdroid.img";
    private static final String VBMETA_IMG = FS + "microdroid_vbmeta.img";

    private VirtualMachine mVm;

    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "==== rung 2c probe start ====");
        Log.i(TAG, "uid=" + Process.myUid() + " pkg=" + getPackageName());

        VirtualMachineManager vmm = getSystemService(VirtualMachineManager.class);
        Log.i(TAG, "STEP1 manager = " + vmm);
        if (vmm == null) {
            Log.e(TAG, "VERDICT 2c: no manager — 2a's result did not reproduce");
            return;
        }

        // STEP 2 — the files. If these are unreadable the rest is meaningless,
        // and "cannot open image" would masquerade as a refusal.
        Log.i(TAG, "STEP2 kernel readable=" + readable(KERNEL)
                + " system=" + readable(SYSTEM_IMG)
                + " vbmeta=" + readable(VBMETA_IMG));

        // STEP 3 — CONTROL. A member 2b proved callable, called again here, so
        // that a failure below cannot be blamed on this build being broken.
        try {
            new VirtualMachineConfig.Builder(this).setApkPath("/nonexistent.apk");
            Log.i(TAG, "STEP3 control OK — setApkPath (SDK member) is callable");
        } catch (Throwable t) {
            Log.e(TAG, "STEP3 CONTROL FAILED -> " + describe(t));
            Log.e(TAG, "VERDICT 2c: inconclusive — the build itself is wrong");
            return;
        }

        // STEP 4 — is setCustomImageConfig itself reachable? Passing null asks
        // only about the METHOD, not about anything it would receive, so this
        // separates the gate on the method from the gate on the config class.
        boolean methodReachable;
        try {
            new VirtualMachineConfig.Builder(this).setCustomImageConfig(null);
            methodReachable = true;
            Log.i(TAG, "STEP4 setCustomImageConfig(null) accepted — method is reachable");
        } catch (Throwable t) {
            methodReachable = false;
            Log.e(TAG, "STEP4 setCustomImageConfig BLOCKED -> " + describe(t));
        }

        // STEP 5 — is the config class constructible? Independent of STEP 4.
        boolean builderReachable;
        try {
            new VirtualMachineCustomImageConfig.Builder();
            builderReachable = true;
            Log.i(TAG, "STEP5 CustomImageConfig.Builder() constructed — reachable");
        } catch (Throwable t) {
            builderReachable = false;
            Log.e(TAG, "STEP5 CustomImageConfig.Builder BLOCKED -> " + describe(t));
        }

        // STEP 6 — the two value types the recipe needs, each on its own.
        try {
            new VirtualMachineCustomImageConfig.Partition("system_a", SYSTEM_IMG, false, null);
            Log.i(TAG, "STEP6a Partition constructed — reachable");
        } catch (Throwable t) {
            Log.e(TAG, "STEP6a Partition BLOCKED -> " + describe(t));
        }

        try {
            VirtualMachineCustomImageConfig.Disk.RODisk(null);
            Log.i(TAG, "STEP6b Disk.RODisk constructed — reachable");
        } catch (Throwable t) {
            Log.e(TAG, "STEP6b Disk.RODisk BLOCKED -> " + describe(t));
        }

        if (!methodReachable || !builderReachable) {
            Log.e(TAG, "VERDICT 2c: NO — refused by the hidden-API gate inside our"
                    + " own process. VirtualizationService was never asked, so"
                    + " USE_CUSTOM_VIRTUAL_MACHINE is NOT what stopped us.");
            return;
        }

        // STEP 7 — everything above was reachable, so ask the real question.
        try {
            createAndRun(vmm);
        } catch (Throwable t) {
            Log.e(TAG, "STEP7 FAILED -> " + describe(t), t);
            Log.e(TAG, "VERDICT 2c: no custom VM created — see the exception type"
                    + " above to tell the permission gate from anything else");
        }
    }

    /** Mirrors /apex/com.android.virt/etc/microdroid.json: one kernel, one
     *  read-only disk carrying two partitions, 256 MiB. Nothing invented. */
    private void createAndRun(VirtualMachineManager vmm) throws Exception {
        VirtualMachineCustomImageConfig custom =
                new VirtualMachineCustomImageConfig.Builder()
                        .setKernelPath(KERNEL)
                        .addDisk(VirtualMachineCustomImageConfig.Disk.RODisk(null)
                                .addPartition(new VirtualMachineCustomImageConfig.Partition(
                                        "vbmeta_a", VBMETA_IMG, false, null))
                                .addPartition(new VirtualMachineCustomImageConfig.Partition(
                                        "system_a", SYSTEM_IMG, false, null)))
                        .build();
        Log.i(TAG, "STEP7a custom image config built, kernel=" + KERNEL);

        // No setPayloadBinaryName here — build() rejects a config that carries
        // both. That exclusivity is what makes this a custom VM.
        VirtualMachineConfig config = new VirtualMachineConfig.Builder(this)
                .setCustomImageConfig(custom)
                .setDebugLevel(VirtualMachineConfig.DEBUG_LEVEL_FULL)
                .setProtectedVm(false)
                .setMemoryBytes(256L * 1024L * 1024L)
                .setCpuTopology(VirtualMachineConfig.CPU_TOPOLOGY_ONE_CPU)
                .build();
        Log.i(TAG, "STEP7b VirtualMachineConfig built");

        try {
            vmm.delete(VM_NAME);
            Log.i(TAG, "STEP7c deleted a pre-existing VM named " + VM_NAME);
        } catch (VirtualMachineException e) {
            Log.i(TAG, "STEP7c no pre-existing VM to delete (" + e.getMessage() + ")");
        }

        // The moment of truth. If USE_CUSTOM_VIRTUAL_MACHINE is enforced
        // against a sideloaded app, it is enforced HERE, across the binder.
        mVm = vmm.create(VM_NAME, config);
        Log.i(TAG, "STEP7d create() returned " + mVm + " name=" + mVm.getName());

        mVm.setCallback(mExecutor, new Callbacks());
        mVm.run();
        Log.i(TAG, "STEP7e run() returned, status=" + mVm.getStatus());
        Log.i(TAG, "VERDICT 2c: custom VM created — confirm requesterUid"
                + " with `vm list` from the host before believing it");
    }

    private static boolean readable(String path) {
        return new File(path).canRead();
    }

    /** The exception type is the finding, so never lose it to a null message. */
    private static String describe(Throwable t) {
        return t.getClass().getName() + ": " + t.getMessage();
    }

    private static class Callbacks implements VirtualMachineCallback {

        @Override
        public void onPayloadStarted(VirtualMachine vm) {
            Log.i(TAG, "CB onPayloadStarted");
        }

        @Override
        public void onPayloadReady(VirtualMachine vm) {
            Log.i(TAG, "CB onPayloadReady — the custom guest booted");
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

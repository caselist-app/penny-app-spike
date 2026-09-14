package com.pennyspike.probe2a;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserManager;
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
 * Rung 3: does the app bring its VM up with nobody touching the phone?
 *
 * Rung 2b booted a VM from an Activity — i.e. because a human tapped
 * something. Penny ships as a headless appliance in a drawer, so that route
 * does not exist in the product. This service is the thing that has to work.
 *
 * WHY A FOREGROUND SERVICE AND NOT JUST THE RECEIVER. The VM belongs to the
 * process that created it; when the process dies the VM goes with it (2b).
 * A BroadcastReceiver's process is killable the instant onReceive returns, so
 * a VM created in a receiver would be shot within seconds. A foreground
 * service is the only ordinary-app mechanism that keeps a process alive
 * indefinitely, which is exactly what holding a VM requires.
 *
 * The payload is Google's stock microdroid payload again, unchanged from 2b.
 * Rung 2c closed off running our own guest from a sideloaded app, so there is
 * nothing to gain by varying it here. Rung 3 varies ONE thing: who starts it.
 *
 * DONE MEANS, checked from the host and not from this log: reboot the device,
 * touch nothing, and `vm list` shows a VM with requesterUid 10192.
 */
public class VmService extends Service {

    private static final String TAG = "PENNY3";
    private static final String VM_NAME = "penny3";
    private static final String CHANNEL = "penny3";
    private static final int NOTIFICATION_ID = 1;

    /** Held for the life of the process. Drop this and the VM dies. */
    private VirtualMachine mVm;

    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    /** onStartCommand can be delivered more than once (START_STICKY restart,
     *  LOCKED_BOOT_COMPLETED then BOOT_COMPLETED). Boot the VM only once. */
    private boolean mStarted;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "==== service onCreate ==== uid=" + Process.myUid()
                + " sinceBoot=" + SystemClock.elapsedRealtime() + "ms");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String why = (intent == null) ? "RESTARTED-BY-SYSTEM (intent is null)"
                : intent.getStringExtra("why");
        Log.i(TAG, "onStartCommand why=" + why + " flags=" + flags
                + " startId=" + startId
                + " sinceBoot=" + SystemClock.elapsedRealtime() + "ms");

        // A null intent means the system restarted us on its own after a kill.
        // That IS the "restart after a kill" half of rung 3, so say so loudly.
        if (intent == null) {
            Log.w(TAG, "RESTART CONFIRMED: the system brought this service back"
                    + " with no app, no user and no broadcast involved");
        }

        // startForeground must happen quickly and before anything slow, or the
        // system kills us for taking too long. Do it first, then work.
        try {
            goForeground();
            Log.i(TAG, "STEP1 startForeground OK — process is now hard to kill");
        } catch (Throwable t) {
            Log.e(TAG, "STEP1 startForeground REFUSED -> " + describe(t), t);
            Log.e(TAG, "VERDICT 3: the service could not stay alive."
                    + " If this is ForegroundServiceStartNotAllowedException the"
                    + " block is the Android 15 boot-start rule, not the VM.");
            stopSelf();
            return START_NOT_STICKY;
        }

        // Did `pm grant` survive the reboot? If not, any VM failure below is
        // explained by that and rung 3 has not tested what we think it has.
        Log.i(TAG, "STEP2 MANAGE_VIRTUAL_MACHINE = " + perm(
                "android.permission.MANAGE_VIRTUAL_MACHINE"));

        // Before first unlock the app's credential-encrypted data directory
        // does not exist yet, and that is where VM state lives. Record it, so
        // a failure at that moment is read as "too early" and not as a refusal.
        UserManager um = getSystemService(UserManager.class);
        Log.i(TAG, "STEP3 userUnlocked=" + (um == null ? "?" : um.isUserUnlocked()));

        if (mStarted) {
            Log.i(TAG, "VM already booted by an earlier start — nothing to do");
            return START_STICKY;
        }
        mStarted = true;

        mExecutor.execute(() -> {
            try {
                boot();
            } catch (Throwable t) {
                mStarted = false;
                Log.e(TAG, "BOOT FAILED -> " + describe(t), t);
                Log.e(TAG, "VERDICT 3: service ran but no VM came up");
            }
        });

        // START_STICKY is the second half of rung 3: if this process is killed
        // for memory, the system recreates the service with a null intent.
        return START_STICKY;
    }

    private void boot() throws Exception {
        // Android gives every app TWO data directories:
        //
        //   /data/user/0/<pkg>     credential-encrypted (CE). Unreadable until
        //                          somebody types the PIN once after a boot.
        //                          This is where the VM tried to live, and why
        //                          the first attempt died with
        //                          "Required key not available".
        //   /data/user_de/0/<pkg>  device-encrypted (DE). Unlocked as soon as
        //                          the OS is up, before any human is involved.
        //                          Only directBootAware components may use it.
        //
        // The VM directory is created relative to the Context the API is handed,
        // so handing it a device-protected Context is the one cheap way to find
        // out whether a VM can exist before first unlock. If it can, Penny is
        // genuinely headless. If it cannot, every power cut needs a person.
        Context ctx = createDeviceProtectedStorageContext();
        Log.i(TAG, "STEP3b using device-protected storage, dataDir=" + ctx.getDataDir());

        VirtualMachineManager vmm = ctx.getSystemService(VirtualMachineManager.class);
        Log.i(TAG, "STEP4 manager = " + vmm);
        if (vmm == null) {
            Log.e(TAG, "VERDICT 3: no manager");
            return;
        }

        String apkPath = findStockPayloadApk();
        Log.i(TAG, "STEP5 stock payload apk = " + apkPath);
        if (apkPath == null) {
            Log.e(TAG, "VERDICT 3: stock payload APK not found");
            return;
        }

        VirtualMachineConfig config = new VirtualMachineConfig.Builder(ctx)
                .setApkPath(apkPath)
                .setPayloadBinaryName("MicrodroidEmptyPayloadJniLib.so")
                .setDebugLevel(VirtualMachineConfig.DEBUG_LEVEL_FULL)
                .setProtectedVm(false)
                .setMemoryBytes(256L * 1024L * 1024L)
                .setCpuTopology(VirtualMachineConfig.CPU_TOPOLOGY_ONE_CPU)
                .build();
        Log.i(TAG, "STEP6 config built");

        // A VM of this name survives reboots on disk. Delete and recreate so
        // every run starts from the same place.
        try {
            vmm.delete(VM_NAME);
            Log.i(TAG, "STEP7 deleted a pre-existing VM named " + VM_NAME);
        } catch (VirtualMachineException e) {
            Log.i(TAG, "STEP7 no pre-existing VM to delete (" + e.getMessage() + ")");
        }

        mVm = vmm.create(VM_NAME, config);
        Log.i(TAG, "STEP8 create() returned " + mVm + " name=" + mVm.getName());

        mVm.setCallback(mExecutor, new Callbacks());
        mVm.run();
        Log.i(TAG, "STEP9 run() returned, status=" + status(mVm)
                + " sinceBoot=" + SystemClock.elapsedRealtime() + "ms");
    }

    /** The notification is also the visible proof: if it is on the lock screen
     *  after a reboot nobody touched, the appliance woke itself. */
    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Penny VM", NotificationManager.IMPORTANCE_LOW));

        // android.R keeps this resource-free: aapt2 links no resources of our
        // own, so the only icons available are the platform's.
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Penny VM")
                .setContentText("holding " + VM_NAME)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    }

    private String perm(String name) {
        return checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
                ? "GRANTED" : "DENIED";
    }

    /** The APEX path carries the OS build ID, e.g. EmptyPayloadApp@CP2A.260705.006. */
    private static String findStockPayloadApk() {
        File[] children = new File("/apex/com.android.virt/app").listFiles();
        if (children == null) return null;
        for (File c : children) {
            if (c.getName().startsWith("EmptyPayloadApp")) {
                File apk = new File(c, "EmptyPayloadApp.apk");
                if (apk.canRead()) return apk.getAbsolutePath();
            }
        }
        return null;
    }

    private static String status(VirtualMachine vm) {
        int s = vm.getStatus();
        if (s == VirtualMachine.STATUS_RUNNING) return "RUNNING";
        if (s == VirtualMachine.STATUS_STOPPED) return "STOPPED";
        if (s == VirtualMachine.STATUS_DELETED) return "DELETED";
        return "UNKNOWN(" + s + ")";
    }

    private static String describe(Throwable t) {
        return t.getClass().getName() + ": " + t.getMessage();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.w(TAG, "==== service onDestroy — the VM dies with this process ====");
        super.onDestroy();
    }

    private static class Callbacks implements VirtualMachineCallback {

        @Override
        public void onPayloadStarted(VirtualMachine vm) {
            Log.i(TAG, "CB onPayloadStarted sinceBoot="
                    + SystemClock.elapsedRealtime() + "ms");
        }

        @Override
        public void onPayloadReady(VirtualMachine vm) {
            Log.i(TAG, "CB onPayloadReady — guest booted, sinceBoot="
                    + SystemClock.elapsedRealtime() + "ms");
            Log.i(TAG, "VERDICT 3: VM is up. Confirm requesterUid with"
                    + " `vm list` from the host before believing it.");
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

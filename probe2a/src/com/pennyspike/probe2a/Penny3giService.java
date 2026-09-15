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
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserManager;
import android.util.Log;

import android.system.virtualmachine.VirtualMachine;
import android.system.virtualmachine.VirtualMachineCallback;
import android.system.virtualmachine.VirtualMachineConfig;
import android.system.virtualmachine.VirtualMachineException;
import android.system.virtualmachine.VirtualMachineManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Rung 3g-i: will a 2048MB VM start at boot, locked, with nobody in the room?
 *
 * EVERY 2GB FIGURE IN THIS REPO WAS TAKEN ON AN IDLE, UNLOCKED, FOREGROUND
 * PHONE, over adb, with the Terminal app's Debian VM deliberately shut down.
 * 3e-i was handed 2048MB and 8 vCPUs; 3e-ii wrote and held 1792MB of it twice;
 * 3e-iii put a 1.5GB file beside it on a real disk. Meanwhile rung 3d proved
 * 256MB VMs come up unattended at boot. Those two results have never been in
 * the same room, and a VM too small to hold a model waking unattended is not
 * the story this spike is telling.
 *
 * A COPY, NOT AN EDIT. VmService (rung 3, seven reboots) and Penny3dService
 * (rung 3d, three) are untouched. This starts from the same broadcast, owns its
 * own VM names, and none of the three can take another down.
 *
 * ── WHAT THE 20-SECOND WINDOW ACTUALLY GOVERNS, BECAUSE IT WAS MISFRAMED ───
 *
 * The rung was defined as "can a 2GB VM make the 20-second foreground-service
 * exemption, given it takes ~4s longer to reach ready than a 256MB one". That
 * is the wrong reading of the window and it would have produced a reassuring
 * non-answer.
 *
 * `Background started FGS: Allowed ... duration:20000` governs startForeground()
 * and nothing after it. Every service in this app — rung 3's included — calls
 * startForeground as the FIRST statement of onStartCommand and only then hands
 * VM work to another thread. Rung 3 reached it in about 5ms. A VM that takes
 * four seconds longer to reach onPayloadReady cannot miss a window it was never
 * racing, and this service logs the timestamp of that call so the claim is on
 * the record rather than assumed.
 *
 * The real risks, which are what is measured here:
 *   - is 2048MB GRANTED at all at boot, when the system is still starting
 *     services and memory is tighter than on the idle phone every figure so
 *     far came from;
 *   - does the low-memory killer take OUR APP, which would take penny3 and
 *     every VM handle in this process with it;
 *   - how long does it actually take to reach the payload at boot.
 *
 * ── THE CONTROL, AND WHY IT RUNS FIRST ─────────────────────────────────────
 *
 * The same service, on the same boot, from the same broadcast, brings up a
 * 256MB VM before the 2048MB one. Exactly one variable differs between them —
 * the memory figure. Everything else, CPU topology included, is identical, so
 * "2048MB is too much at boot" and "this service is shaped wrong" cannot be
 * confused.
 *
 * Control FIRST, deliberately. At 4096MB the low-memory killer reached our own
 * foreground TOP process and took the probe with it before its watchdog could
 * log anything; at 2048MB on a BUSY phone nobody has measured. If that happens
 * here, the control is already banked in the log. Running it second would risk
 * losing the only thing that makes the failure readable. The control's VM is
 * told to say goodbye and is waited out, so its memory is genuinely back before
 * the 2048MB attempt starts.
 *
 * ── AND WHY IT WAITS FOR RUNG 3e-v ─────────────────────────────────────────
 *
 * 3e-v runs on the same boots and is the result that can close the whole plan
 * down. Booting a 2048MB VM drives the low-memory killer EVERY time — the first
 * 3e-ii run killed thirteen processes — so this one holds off until 3e-v has
 * written its pre-unlock verdict to the log, or 45 seconds have passed,
 * whichever comes first. The dependency is one-way and cannot deadlock: 3e-v
 * never reads anything of ours, it sets the flag from a finally block and from
 * its watchdog, and the timeout covers the case where its process never got
 * that far.
 */
public class Penny3giService extends Service {

    private static final String TAG = "PENNY3GI";
    private static final String CHANNEL = "penny3gi";
    private static final int NOTIFICATION_ID = 6;

    /** Already in the APK and NOT rebuilt. CMD_INFO makes the guest dump its
     *  own /proc/cpuinfo, /proc/mounts and /proc/meminfo to the console, which
     *  is corroboration this process does not write: the guest's own MemTotal
     *  is what says 2048MB was really delivered rather than merely accepted. */
    private static final String PAYLOAD = "Penny3fPayload.so";

    private static final long PORT = 5555L;
    private static final int CMD_INFO = 1;

    /** Both attempts, in order. ONLY the memory figure differs. */
    private static final String CONTROL_VM = "penny3gic";
    private static final int CONTROL_MB = 256;
    private static final String TEST_VM = "penny3gi";
    private static final int TEST_MB = 2048;

    private static final long READY_TIMEOUT_MS = 90_000L;
    private static final long STOP_TIMEOUT_MS = 20_000L;
    private static final long EV_WAIT_MS = 45_000L;

    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    /** Held for the life of an attempt. Drop it and the VM dies. */
    private VirtualMachine mVm;

    private volatile boolean mReady;
    private volatile boolean mStopped;
    private volatile long mReadyAt;

    /** Read at the moment the TEST VM became ready, so the verdict quotes a
     *  measurement rather than an assumption about which boot this was. */
    private volatile boolean mUnlockedAtReady;
    private volatile long mControlMs;
    private volatile long mTestMs;

    private boolean mStarted;

    /** See Penny3evService's field of the same purpose: the boot window's
     *  logcat buffer is at its default size, `adb logcat -G 64M` does not
     *  survive a reboot, adb cannot reach a locked GrapheneOS phone to set it
     *  first, and `setprop persist.logd.size` is refused by SELinux here. So
     *  the verdict is re-emitted as one line at a quiet moment too. */
    private final StringBuilder mSummary = new StringBuilder();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "==== rung 3g-i service onCreate ==== uid=" + Process.myUid()
                + " sinceBoot=" + SystemClock.elapsedRealtime() + "ms");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String why = (intent == null) ? "RESTARTED-BY-SYSTEM (intent is null)"
                : intent.getStringExtra("why");
        long t = SystemClock.elapsedRealtime();
        Log.i(TAG, "onStartCommand why=" + why + " flags=" + flags
                + " startId=" + startId + " sinceBoot=" + t + "ms");

        // FIRST, and this is the whole of what the 20-second exemption governs.
        try {
            goForeground();
            note("startForeground(SPECIAL_USE) OK at sinceBoot="
                    + SystemClock.elapsedRealtime() + "ms, "
                    + (SystemClock.elapsedRealtime() - t)
                    + "ms into onStartCommand — the exemption window is"
                    + " 20000ms and no VM work has happened yet");
        } catch (Throwable th) {
            Log.e(TAG, "startForeground REFUSED -> " + describe(th), th);
            Log.e(TAG, "VERDICT 3g-i: NOT TESTED — refused before any VM was"
                    + " asked for, so this says nothing about 2048MB.");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (mStarted) {
            Log.i(TAG, "already started by an earlier delivery — nothing to do");
            return START_STICKY;
        }
        mStarted = true;

        // ITS OWN THREAD, never mExecutor. mExecutor is what setCallback() is
        // handed, and runBoth() blocks for the whole of both attempts — so on
        // the shared single thread it would sit on the very callback it is
        // waiting for, and a perfectly healthy VM would be reported as one
        // that never became ready. That is the trap recorded in CLAUDE.md; it
        // cost a run on 3e-i and it cost the first smoke test of this file.
        new Thread(this::runBoth, "penny3gi-main").start();
        return START_STICKY;
    }

    private void runBoth() {
        // Hold off until 3e-v has its pre-unlock verdict in the log. One-way,
        // timeout-guarded, cannot deadlock — see the class comment.
        long deadline = SystemClock.elapsedRealtime() + EV_WAIT_MS;
        while (!Penny3evService.PHASE1_DONE
                && SystemClock.elapsedRealtime() < deadline) {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        }
        note("released at sinceBoot=" + SystemClock.elapsedRealtime()
                + "ms (3e-v done=" + Penny3evService.PHASE1_DONE + ")");
        note("HOST before: " + hostMem());

        boolean control = attempt("CONTROL", CONTROL_VM, CONTROL_MB);
        note("HOST between: " + hostMem());
        boolean test = attempt("TEST", TEST_VM, TEST_MB);
        note("HOST after: " + hostMem());

        if (control && test) {
            // The lock state is READ, never assumed. An unattended-boot claim
            // made by a template rather than by a measurement is exactly the
            // kind of line that gets quoted later and turns out to be wrong.
            Log.i(TAG, "VERDICT 3g-i: " + (mUnlockedAtReady ? "NOT AN ANSWER —"
                    + " the phone was ALREADY UNLOCKED when the " + TEST_MB
                    + "MB VM became ready, so this is a foreground baseline and"
                    + " not the unattended boot case. It" : "YES — a " + TEST_MB
                    + "MB VM reached its payload at boot with the phone still"
                    + " locked, on the same boot and from the same service as a "
                    + CONTROL_MB + "MB control. It")
                    + " came up in " + mTestMs + "ms against the control's "
                    + mControlMs + "ms. Read the lmkd kill log (see CLAUDE.md)"
                    + " for who paid for it.");
        } else if (control) {
            Log.e(TAG, "VERDICT 3g-i: NO — the " + CONTROL_MB + "MB control"
                    + " came up at boot from this exact service and the "
                    + TEST_MB + "MB attempt did not. The service shape is"
                    + " therefore ruled OUT and the memory figure is the cause."
                    + " Check the lmkd kill log (see CLAUDE.md): a low-memory kill"
                    + " and 3e-ii's silent guest live-lock present identically"
                    + " from here, and only that line tells them apart.");
        } else if (test) {
            Log.w(TAG, "VERDICT 3g-i: the " + TEST_MB + "MB VM came up but the"
                    + " control did not, which is backwards. Do not read this"
                    + " as a result — something is wrong with the run.");
        } else {
            Log.e(TAG, "VERDICT 3g-i: NOT ANSWERED — neither size came up at"
                    + " boot, so the fault is this service or the boot context,"
                    + " NOT the 2048MB figure. Rung 3d's 256MB VM on this same"
                    + " boot is the thing to compare against.");
        }
        dumpSummary();
    }

    /**
     * One VM, start to finish: create, run, wait for the payload, ask it one
     * question, say goodbye, wait for it to actually stop so the memory is
     * back before the next attempt.
     */
    private boolean attempt(String label, String name, int memMb) {
        mReady = false;
        mStopped = false;
        mReadyAt = 0;
        long t0 = SystemClock.elapsedRealtime();
        boolean unlockedAtStart = isUnlocked();

        note(label + " " + memMb + "MB start sinceBoot=" + t0
                + "ms userUnlocked=" + unlockedAtStart);
        if (unlockedAtStart) {
            Log.w(TAG, label + ": the phone is ALREADY unlocked, so this is not"
                    + " a measurement of the unattended boot case.");
        }

        try {
            Context ctx = createDeviceProtectedStorageContext();
            VirtualMachineManager vmm =
                    ctx.getSystemService(VirtualMachineManager.class);
            if (vmm == null) {
                note(label + " no VirtualMachineManager");
                return false;
            }

            String apkPath = getApplicationInfo().sourceDir;
            VirtualMachineConfig config = new VirtualMachineConfig.Builder(ctx)
                    .setApkPath(apkPath)
                    .setPayloadBinaryName(PAYLOAD)
                    .setDebugLevel(VirtualMachineConfig.DEBUG_LEVEL_FULL)
                    .setProtectedVm(false)
                    .setMemoryBytes((long) memMb * 1024L * 1024L)
                    .setCpuTopology(VirtualMachineConfig.CPU_TOPOLOGY_MATCH_HOST)
                    .build();

            // Neither of these VMs owns an encrypted store, so deleting costs
            // nothing and there is nothing here to strand. It also guarantees
            // the size asked for is the size used: getOrCreate would silently
            // reuse a STORED config and measure the previous run instead.
            try {
                vmm.delete(name);
            } catch (VirtualMachineException ignored) {
            }

            mVm = vmm.create(name, config);
            mVm.setCallback(mExecutor, new Callbacks(label));
            long r0 = SystemClock.elapsedRealtime();
            mVm.run();
            note(label + " run() returned in "
                    + (SystemClock.elapsedRealtime() - r0) + "ms status="
                    + status(mVm) + " — accepted, NOT yet a boot. Nothing has"
                    + " ever refused a memory figure here, 6GB included.");
        } catch (Throwable th) {
            note(label + " REFUSED at create/run -> " + describe(th));
            Log.e(TAG, label + " failed", th);
            return false;
        }

        long deadline = SystemClock.elapsedRealtime() + READY_TIMEOUT_MS;
        while (!mReady && !mStopped && SystemClock.elapsedRealtime() < deadline) {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }
        if (!mReady) {
            note(label + " NEVER READY after " + (READY_TIMEOUT_MS / 1000)
                    + "s, userUnlocked=" + isUnlocked()
                    + ", host " + hostMem());
            return false;
        }

        long took = mReadyAt - t0;
        boolean unlockedAtReady = isUnlocked();
        if (memMb == TEST_MB) {
            mUnlockedAtReady = unlockedAtReady;
            mTestMs = took;
        } else {
            mControlMs = took;
        }
        note(label + " READY at sinceBoot=" + mReadyAt + "ms (" + took
                + "ms after this attempt began) userUnlocked=" + unlockedAtReady);

        // One question, so that "ready" means a guest that answers rather than
        // a callback that fired. CMD_INFO also makes the guest print its own
        // MemTotal, which is the independent reading of what it was given.
        boolean answered = ask(label);

        long s0 = SystemClock.elapsedRealtime();
        while (!mStopped && SystemClock.elapsedRealtime() - s0 < STOP_TIMEOUT_MS) {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }
        note(label + " stopped=" + mStopped + " after "
                + (SystemClock.elapsedRealtime() - s0) + "ms");
        mVm = null;
        return answered;
    }

    /** One CMD_INFO down one vsock connection. The payload accepts exactly one
     *  connection and then loops until a zero-length frame, so the goodbye is
     *  what makes it exit 46 and the VM release its memory. */
    private boolean ask(String label) {
        ParcelFileDescriptor pfd = null;
        try {
            pfd = mVm.connectVsock(PORT);
            FileDescriptor fd = pfd.getFileDescriptor();
            OutputStream os = new FileOutputStream(fd);
            InputStream is = new FileInputStream(fd);

            byte[] req = new byte[16];
            putBe32(req, 0, 12);
            putBe32(req, 4, CMD_INFO);
            putBe32(req, 8, 0);
            putBe32(req, 12, 0);
            os.write(req);
            os.flush();

            byte[] rhdr = readFully(is, 4);
            if (rhdr == null) {
                note(label + " the guest closed before replying to CMD_INFO");
                return false;
            }
            byte[] body = readFully(is, getBe32(rhdr, 0));
            if (body == null || body.length < 44) {
                note(label + " truncated CMD_INFO reply");
                return false;
            }
            int st = getBe32(body, 0);
            long freeBef = getBe32(body, 4) & 0xffffffffL;
            note(label + " guest answered CMD_INFO status=" + st
                    + " guestMemFree=" + freeBef + " kB — its own /proc/meminfo"
                    + " is in the guest console and is the reading that was not"
                    + " written by this process");

            byte[] bye = new byte[4];
            putBe32(bye, 0, 0);
            os.write(bye);
            os.flush();
            return st == 0;
        } catch (Throwable th) {
            note(label + " CMD_INFO threw " + describe(th));
            return false;
        } finally {
            if (pfd != null) {
                try { pfd.close(); } catch (Throwable ignored) {}
            }
        }
    }

    // -------------------------------------------------------------- plumbing

    private boolean isUnlocked() {
        UserManager um = getSystemService(UserManager.class);
        return um != null && um.isUserUnlocked();
    }

    /** Android's own memory, not the guest's. Rung 3e-i misread lazy
     *  allocation here because it sampled twice; crosvm in fact takes the whole
     *  figure at VM creation. */
    private static String hostMem() {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("MemFree:") || line.startsWith("MemTotal:")
                        || line.startsWith("MemAvailable:")
                        || line.startsWith("Cached:")) {
                    sb.append(line.trim()).append("  ");
                }
            }
        } catch (Throwable t) {
            sb.append("unreadable: ").append(t);
        }
        return sb.toString();
    }

    private void note(String s) {
        Log.i(TAG, s);
        synchronized (mSummary) {
            if (mSummary.length() > 0) {
                mSummary.append(" | ");
            }
            mSummary.append(s);
        }
    }

    private void dumpSummary() {
        synchronized (mSummary) {
            Log.i(TAG, "SUMMARY 3g-i :: " + mSummary);
        }
    }

    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Penny 3g-i", NotificationManager.IMPORTANCE_LOW));

        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Penny 3g-i")
                .setContentText("2GB VM at boot")
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
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

    private static byte[] readFully(InputStream is, int n) throws Exception {
        byte[] b = new byte[n];
        int got = 0;
        while (got < n) {
            int r = is.read(b, got, n - got);
            if (r < 0) return null;
            got += r;
        }
        return b;
    }

    private static void putBe32(byte[] b, int off, int v) {
        b[off]     = (byte) (v >>> 24);
        b[off + 1] = (byte) (v >>> 16);
        b[off + 2] = (byte) (v >>> 8);
        b[off + 3] = (byte) v;
    }

    private static int getBe32(byte[] b, int off) {
        return ((b[off] & 0xff) << 24) | ((b[off + 1] & 0xff) << 16)
             | ((b[off + 2] & 0xff) << 8) | (b[off + 3] & 0xff);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.w(TAG, "==== rung 3g-i service onDestroy sinceBoot="
                + SystemClock.elapsedRealtime() + "ms");
        super.onDestroy();
    }

    private class Callbacks implements VirtualMachineCallback {

        private final String mLabel;

        Callbacks(String label) {
            mLabel = label;
        }

        @Override
        public void onPayloadStarted(VirtualMachine vm) {
            Log.i(TAG, mLabel + " CB onPayloadStarted sinceBoot="
                    + SystemClock.elapsedRealtime() + "ms");
        }

        @Override
        public void onPayloadReady(VirtualMachine vm) {
            mReadyAt = SystemClock.elapsedRealtime();
            mReady = true;
            Log.i(TAG, mLabel + " CB onPayloadReady sinceBoot=" + mReadyAt
                    + "ms — the payload called notifyPayloadReady itself, so"
                    + " this is a booted guest and not a registered VM");
        }

        @Override
        public void onPayloadFinished(VirtualMachine vm, int exitCode) {
            note(mLabel + " CB onPayloadFinished exitCode=" + exitCode);
        }

        @Override
        public void onError(VirtualMachine vm, int errorCode, String message) {
            note(mLabel + " CB onError code=" + errorCode + " message=" + message);
        }

        @Override
        public void onStopped(VirtualMachine vm, int reason) {
            mStopped = true;
            note(mLabel + " CB onStopped reason=" + reason
                    + " sinceBoot=" + SystemClock.elapsedRealtime() + "ms");
        }
    }
}

package com.pennyspike.probe2a;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.BatteryManager;
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
import java.io.FileReader;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * ENDURANCE, and the store-creation case, answered by one service.
 *
 * A COPY of Penny3evService, not an edit. That service holds rung 3e-v's
 * committed result across TWO reproductions (the pre-unlock read and the
 * after-unlock control down the same socket) and is byte-for-byte untouched.
 * The "eight" this comment used to claim is rung 3's VM-wake count, not
 * 3e-v's. Also byte-for-byte untouched are VmService, Penny3dService,
 * MicFgsService, Penny3giService and every Probe*Activity. This owns its OWN VM name, its OWN encrypted store and its
 * OWN notification id, and nothing here can take any of those down.
 *
 * ── WHY THE TWO QUESTIONS ARE ONE SERVICE ──────────────────────────────────
 *
 * The handover listed them separately and they are the same VM.
 *
 * 1. CAN A NEW ENCRYPTED STORE BE CREATED BEFORE FIRST UNLOCK? 3e-v answered
 *    the RE-OPEN case twice over — 67,108,864 bytes, ck64 0x757b795dd5138044,
 *    14.4s after power-on with userUnlocked=false. It never answered the
 *    CREATE case: its boot 1 died on the getOrCreate stale-config trap, and
 *    the file it eventually read had been written on an UNLOCKED phone during
 *    a diagnostic run. That is the first-boot-after-factory-reset case, and it
 *    needs nothing but a VM name that has never existed, so that getOrCreate
 *    has no prior directory of any kind. `pennysoak` is that name.
 *
 * 2. ENDURANCE. There is none anywhere in this repo. Nothing has ever run
 *    longer than about twenty seconds. Rung 3's 95 unattended minutes were a
 *    LOST CONNECTION and not a designed soak, and must never be cited as one.
 *
 * The first answer lands in the first twenty seconds of boot and is in the log
 * before the soak begins, so a soak that dies at hour three cannot take the
 * store-creation result with it.
 *
 * ── THE SHAPE, WHICH WAS FORCED ────────────────────────────────────────────
 *
 * penny3f_payload.c calls accept4() exactly ONCE and then loops until a
 * zero-length frame arrives. A second connectVsock would never be accepted.
 * So one held connection with a periodic command down it is not the convenient
 * design, it is the only one available — and it is also the harsher test,
 * because it asks whether a vsock connection survives hours of idleness rather
 * than merely whether a VM does.
 *
 * NO NEW GUEST C. The fifth payload already in the APK does everything needed:
 * CMD_STREAM writes a file and returns a checksum, CMD_VERIFY reads one back
 * and returns a checksum. The payload budget for this handset stays spent.
 *
 * ── WHAT A RESULT LOOKS LIKE ───────────────────────────────────────────────
 *
 * Every sample logs elapsed time, the checksum, host /proc/meminfo, the
 * guest's own MemFree and the battery. A failure logs the elapsed time it
 * failed at, which is the number the whole exercise exists to produce. "It
 * survived N hours" and "it died at N hours" are both results; only silence
 * is not.
 */
public class PennySoakService extends Service {

    private static final String TAG = "PENNYSOAK";
    /** The DEFAULT, overridable with `--es vm <name>` so that a smoke test can
     *  be run by hand without touching the real store. That matters more here
     *  than anywhere else in this app: the question boot A answers is "can a
     *  store be CREATED before first unlock", and a smoke test on an unlocked
     *  phone under this name would create it first and silently turn boot A
     *  into the re-open case 3e-v already answered. Smoke test with
     *  `--es vm pennysoaksmoke --ei interval 20`. */
    private static final String VM_NAME = "pennysoak";
    private static final String CHANNEL = "pennysoak";
    private static final int NOTIFICATION_ID = 7;

    /** The fifth payload, already in the APK and NOT rebuilt for this rung.
     *  CMD_STREAM writes a file and returns a checksum; CMD_VERIFY reads one
     *  back and returns a checksum. That is exactly write-then-verify, so
     *  3e-v needs no new guest C at all. */
    private static final String PAYLOAD = "Penny3fPayload.so";

    /** Must match PORT in penny3f_payload.c. */
    private static final long PORT = 5555L;

    private static final int CMD_STREAM = 6;
    private static final int CMD_VERIFY = 7;

    private static final int ST_OK = 0;
    private static final int ST_OPEN = 3;
    private static final int ENOENT = 2;

    /** 2048MB, the size a model needs and the size 3g-i proved wakes at boot.
     *  Matt's call, 15 Sept: the 256MB soak would prove the harness works and
     *  nothing else. 3g-ii measured what CREATING one costs on a busy phone —
     *  the whole cached band plus the keyboard at adj 201 — but every VM in
     *  that rung lived about seven seconds. HOLDING one is what is untested. */
    private static final long VM_MB = 2048L;

    /** 3e-iii's one line of config: a real ext4 on dm-crypt at
     *  /mnt/encryptedstore. Without it a microdroid guest has no writable
     *  filesystem at all. 256MB is ample for a 64MB file plus ext4's own
     *  metadata, and small enough not to be a memory experiment in disguise. */
    private static final long STORE_MB = 256L;

    private static final String FILE_DIR = "/mnt/encryptedstore/";

    /** 64MB. The question is about the KEY, not about the size — 3e-iii and 3h
     *  already took 1.5GB through this store twice. 64MB writes in about a
     *  second and a half at boot (3h measured 43 MB/s at that size) and reads
     *  back in well under one, which keeps the boot window uncluttered. */
    private static final int FILE_MB = 64;
    private static final long FILE_BYTES = (long) FILE_MB * 1024L * 1024L;

    private static final int CHUNK = 1024 * 1024;

    /** FNV-1a over 64-bit LITTLE-ENDIAN words, as in penny3f_payload.c's
     *  ck_update. NOT 3c's byte-at-a-time hash; these numbers must never be
     *  compared with 3c's or 3d's. */
    private static final long FNV_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /** The xorshift64* seed Probe3fActivity uses. The generator advances per
     *  8-byte word and is never reset at a chunk boundary, so for a given
     *  total length the byte stream — and therefore the checksum — is fixed. */
    private static final long SEED = 0x2545F4914F6CDD1DL;

    private static final long READY_TIMEOUT_MS = 90_000L;

    // ── THE SOAK, AND THE CHOICES MADE BEFORE THE RUN ──────────────────────
    //
    // Written down here rather than worked out from the log afterwards,
    // because a log cannot tell you what was decided and what merely happened.
    //
    // INTERVAL 5 minutes. logcat's buffer is at its DEFAULT size through the
    // boot window — `-G 64M` dies on reboot, adb cannot reach a locked phone
    // to set it first, and `setprop persist.logd.size` is refused by SELinux
    // on this build. A tighter interval floods it against four DEBUG_LEVEL_FULL
    // guest consoles. Five minutes is 12 samples an hour, which is ample to
    // see a trend and cheap enough to survive the buffer.
    //
    // THE CHECKSUM IS RE-VERIFIED EVERY SAMPLE, not once at the end. A store
    // that rots silently is then caught at the sample where it rots rather
    // than discovered hours later with no way to date it. The expected value
    // is DERIVED from the fixed-seed generator each time, never read back from
    // an earlier log line, so this cannot agree with itself by accident.
    //
    // NO WAKELOCK, DELIBERATELY. Whether Android's doze interferes with a
    // long-held VM is part of the question, and a wakelock would mask it.
    // Samples may therefore arrive late; every one carries its own elapsed
    // time, measured, so lateness is data rather than error.
    private static final long SOAK_INTERVAL_MS = 5L * 60_000L;

    /** Set from the intent; see VM_NAME. Written once in onStartCommand before
     *  any thread that reads them is started. */
    private volatile String mVmName = VM_NAME;
    private volatile String mFile = FILE_DIR + VM_NAME + ".bin";
    private volatile long mIntervalMs = SOAK_INTERVAL_MS;

    /** A cap, not a plan. The run ends when the phone is rebooted or the
     *  service is stopped; this only stops it running for ever if forgotten. */
    private static final long SOAK_MAX_MS = 24L * 60L * 60_000L;
    private static final long UNLOCK_TIMEOUT_MS = 45L * 60_000L;

    /**
     * VESTIGIAL, and kept deliberately rather than deleted. In Penny3evService
     * this releases Penny3giService's 2048MB VM; here it is a separate static
     * that NOTHING reads — Penny3giService names Penny3evService.PHASE1_DONE
     * explicitly, so this copy cannot release or delay it either way. It is
     * left in place so that the two files stay diffable line for line, which
     * is what makes "a copy, not an edit" checkable rather than asserted.
     */
    static volatile boolean PHASE1_DONE = false;

    private VirtualMachine mVm;

    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    private volatile boolean mReady = false;
    private volatile boolean mFinished = false;
    private boolean mStarted;

    /** Re-emitted as one line at the end. logcat's buffer is at its default
     *  size during the boot window — `adb logcat -G 64M` dies on reboot, adb
     *  cannot reach a locked GrapheneOS phone to set it beforehand, and
     *  `setprop persist.logd.size` is refused by SELinux on this build. Four
     *  DEBUG_LEVEL_FULL guest consoles on one boot can evict a run's own
     *  lines, so the verdict is repeated at a quiet moment as well. */
    private final StringBuilder mSummary = new StringBuilder();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "==== endurance + store-create service onCreate ==== uid=" + Process.myUid()
                + " sinceBoot=" + SystemClock.elapsedRealtime() + "ms");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String why = (intent == null) ? "RESTARTED-BY-SYSTEM (intent is null)"
                : intent.getStringExtra("why");
        Log.i(TAG, "onStartCommand why=" + why + " flags=" + flags
                + " startId=" + startId
                + " sinceBoot=" + SystemClock.elapsedRealtime() + "ms");

        // First, and fast. The boot broadcast's exemption governs THIS call and
        // nothing below it — rung 3 reached it in ~5ms, and no amount of VM
        // work afterwards can miss a window it was never racing.
        try {
            goForeground();
            Log.i(TAG, "STEP1 startForeground(SPECIAL_USE) OK sinceBoot="
                    + SystemClock.elapsedRealtime() + "ms");
        } catch (Throwable t) {
            Log.e(TAG, "STEP1 startForeground REFUSED -> " + describe(t), t);
            Log.e(TAG, "VERDICT store-create: NOT TESTED — the service could not"
                    + " stay alive, so the store was never asked anything,"
                    + " and the soak never began.");
            PHASE1_DONE = true;
            stopSelf();
            return START_NOT_STICKY;
        }

        UserManager um = getSystemService(UserManager.class);
        Log.i(TAG, "STEP2 MANAGE_VIRTUAL_MACHINE="
                + perm("android.permission.MANAGE_VIRTUAL_MACHINE")
                + " userUnlocked=" + (um == null ? "?" : um.isUserUnlocked()));

        if (mStarted) {
            Log.i(TAG, "already started by an earlier delivery — nothing to do");
            return START_STICKY;
        }
        mStarted = true;

        // Overrides, for the hand-run smoke test only. A boot delivery carries
        // neither, so the real run cannot be altered by accident.
        if (intent != null) {
            String vm = intent.getStringExtra("vm");
            if (vm != null && !vm.isEmpty()) {
                mVmName = vm;
                mFile = FILE_DIR + vm + ".bin";
            }
            int iv = intent.getIntExtra("interval", 0);
            if (iv > 0) {
                mIntervalMs = iv * 1000L;
            }
        }
        Log.i(TAG, "STEP2b vm=" + mVmName + " file=" + mFile
                + " interval=" + (mIntervalMs / 1000) + "s"
                + (mVmName.equals(VM_NAME) ? "" : "  *** SMOKE TEST — this is"
                        + " NOT the store the store-create question is about"
                        + " ***"));

        // Its own thread, never mExecutor: mExecutor is what setCallback() is
        // handed, and a watchdog sleeping on it blocks the very callback it is
        // waiting for. That cost a run on 3e-i.
        new Thread(this::watchdog, "pennysoak-watchdog").start();

        mExecutor.execute(() -> {
            try {
                bringUp("pre-unlock attempt");
            } catch (Throwable t) {
                Log.e(TAG, "BRING-UP FAILED -> " + describe(t), t);
                Log.e(TAG, "VERDICT store-create: the VM could not be created or run"
                        + " before first unlock. If the message names the"
                        + " encrypted store, that IS the store-create answer and it is"
                        + " NO. If it names the VM directory or the APK path it"
                        + " is not — see the getOrCreate trap. The watchdog will"
                        + " retry the identical call after an unlock, which is"
                        + " what tells those two apart.");
            }
        });

        return START_STICKY;
    }

    /** The whole bring-up, written once so the after-unlock retry on the
     *  failure path is provably the same call and not a second design. */
    private void bringUp(String label) throws Exception {
        long t0 = SystemClock.elapsedRealtime();

        // Device-protected storage. Before first unlock /data/user/0/<pkg>
        // does not exist and VirtualMachine.createVmDir builds the VM
        // directory relative to whatever Context it is handed. This one line
        // is what makes a VM possible at all before the PIN — rung 3's finding,
        // and it says nothing about the ENCRYPTED STORE's key, which is the
        // separate thing this rung measures.
        Context ctx = createDeviceProtectedStorageContext();
        Log.i(TAG, "[" + label + "] STEP3 device-protected dataDir="
                + ctx.getDataDir());

        VirtualMachineManager vmm = ctx.getSystemService(VirtualMachineManager.class);
        if (vmm == null) {
            Log.e(TAG, "VERDICT store-create: NOT TESTED — no VirtualMachineManager");
            return;
        }

        String apkPath = getApplicationInfo().sourceDir;
        Log.i(TAG, "[" + label + "] STEP4 apk=" + apkPath
                + " bytes=" + new File(apkPath).length());

        VirtualMachineConfig config = new VirtualMachineConfig.Builder(ctx)
                .setApkPath(apkPath)
                .setPayloadBinaryName(PAYLOAD)
                .setDebugLevel(VirtualMachineConfig.DEBUG_LEVEL_FULL)
                .setProtectedVm(false)
                .setMemoryBytes(VM_MB * 1024L * 1024L)
                .setCpuTopology(VirtualMachineConfig.CPU_TOPOLOGY_MATCH_HOST)
                .setEncryptedStorageBytes(STORE_MB * 1024L * 1024L)
                .build();
        Log.i(TAG, "[" + label + "] STEP5 config built: " + VM_MB + "MB VM, "
                + STORE_MB + "MB encrypted store, payload=" + PAYLOAD);

        // NEVER delete on the normal path. The store IS the experiment and
        // delete() takes it with the VM. getOrCreate reuses the STORED config,
        // which is why this APK must not be reinstalled between the two boots:
        // the stored config names a /data/app/~~<hash>/base.apk that a
        // reinstall replaces, and the failure reads as a missing VM when it is
        // a stranded one.
        mVm = vmm.getOrCreate(mVmName, config);
        Log.i(TAG, "[" + label + "] STEP6 getOrCreate -> " + mVm.getName()
                + " status=" + status(mVm));

        mVm.setCallback(mExecutor, new Callbacks());

        try {
            mVm.run();
        } catch (VirtualMachineException e) {
            // THE RECOVERY, AND IT IS ALSO THE DIAGNOSTIC.
            //
            // Boot 1 of this rung died here with "Failed to open APK" caused
            // by ENOENT, on an APK that this same method had just stat'ed
            // successfully two lines earlier — and it died identically after
            // the unlock, which is what ruled the user's credential out. The
            // obvious reading was CLAUDE.md's getOrCreate trap, and that was
            // WRONG: the stored config had been written minutes earlier by
            // this very install, and Probe3fActivity does getOrCreate over an
            // encrypted store on the same APK in the same process and works.
            //
            // So the VM directory's own contents are dumped before anything is
            // destroyed. Whatever ENOENT refers to is in there, and a guess is
            // not worth a reboot.
            Log.e(TAG, "[" + label + "] run() REFUSED -> " + describe(e), e);
            dumpVmDir(ctx);

            // Then recover, because a poisoned VM directory otherwise makes
            // every later boot fail the same way and no amount of rebooting
            // answers anything. Deleting is safe HERE and only here: a VM that
            // cannot run has no readable store to lose.
            note("RESETTING " + mVmName + ": delete + create fresh");
            try {
                vmm.delete(mVmName);
            } catch (VirtualMachineException ignored) {
            }
            mVm = vmm.create(mVmName, config);
            mVm.setCallback(mExecutor, new Callbacks());
            mVm.run();

            // Loud, because it changes what this run can and cannot answer.
            Log.w(TAG, "STORE WAS RESET. Anything this run writes is NEW, so"
                    + " this boot can only answer 'can a store be created and"
                    + " written before first unlock'. The re-open question"
                    + " needs the NEXT boot, with no reinstall in between.");
        }

        Log.i(TAG, "[" + label + "] STEP7 run() returned in "
                + (SystemClock.elapsedRealtime() - t0) + "ms status="
                + status(mVm) + " sinceBoot=" + SystemClock.elapsedRealtime()
                + "ms — accepted, NOT yet a boot.");
    }

    /**
     * Everything the VM directory holds, listed and — for anything small and
     * textual — printed. This exists because boot 1 threw FileNotFoundException
     * from inside the framework without ever naming the file, and the three
     * candidate explanations could not be told apart from outside the app's own
     * data directory, which adb shell cannot read on a user build.
     */
    private void dumpVmDir(Context ctx) {
        try {
            File dir = new File(ctx.getDataDir(), "vm/" + mVmName);
            Log.i(TAG, "VMDIR " + dir + " exists=" + dir.exists());
            File[] kids = dir.listFiles();
            if (kids == null) {
                Log.i(TAG, "VMDIR unreadable or empty");
                return;
            }
            for (File f : kids) {
                Log.i(TAG, "VMDIR   " + f.getName() + "  " + f.length()
                        + " bytes  readable=" + f.canRead());
                if (f.length() > 0 && f.length() < 8192) {
                    byte[] b = new byte[(int) f.length()];
                    try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                        int n = in.read(b);
                        // Printable characters only: the config is XML but the
                        // instance image is not, and a binary blob in logcat
                        // costs a line-wrapped mess for nothing.
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < n; i++) {
                            char c = (char) (b[i] & 0xff);
                            sb.append((c >= 32 && c < 127) ? c : '.');
                        }
                        Log.i(TAG, "VMDIR   " + f.getName() + " >> " + sb);
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "VMDIR dump failed: " + t);
        }
    }

    /**
     * If the VM never becomes ready before first unlock, that is itself a
     * candidate 3e-v answer — but only if the identical call succeeds once the
     * phone IS unlocked. So this waits, reports, releases 3g-i, and then runs
     * the control by repeating the whole bring-up.
     */
    private void watchdog() {
        long deadline = SystemClock.elapsedRealtime() + READY_TIMEOUT_MS;
        while (!mReady && !mFinished && SystemClock.elapsedRealtime() < deadline) {
            try { Thread.sleep(250); } catch (InterruptedException ignored) {}
        }
        if (mReady || mFinished) {
            return;
        }

        boolean unlocked = isUnlocked();
        note("PRE-UNLOCK BRING-UP FAILED: no onPayloadReady within "
                + (READY_TIMEOUT_MS / 1000) + "s, userUnlocked=" + unlocked);
        Log.e(TAG, "VERDICT store-create so far: the VM did not reach its payload"
                + " before first unlock. Check the lmkd kill log (see CLAUDE.md) —"
                + " a low-memory kill and a store that refused to open look"
                + " identical from here. The control below is what separates"
                + " them.");
        PHASE1_DONE = true;

        if (!awaitUnlock()) {
            Log.e(TAG, "VERDICT store-create: INCONCLUSIVE — nobody unlocked the phone,"
                    + " so the control never ran.");
            dumpSummary();
            return;
        }

        note("CONTROL: repeating the identical bring-up now the phone is"
                + " unlocked, sinceBoot=" + SystemClock.elapsedRealtime() + "ms");
        try {
            bringUp("after-unlock control");
        } catch (Throwable t) {
            note("CONTROL bring-up ALSO FAILED -> " + describe(t));
            Log.e(TAG, "VERDICT store-create: the same call fails unlocked too, so the"
                    + " fault is NOT the user's credential. Something else is"
                    + " wrong with this service or this VM and store-create is NOT"
                    + " ANSWERED by this run.", t);
            dumpSummary();
            return;
        }

        long d2 = SystemClock.elapsedRealtime() + READY_TIMEOUT_MS;
        while (!mReady && !mFinished && SystemClock.elapsedRealtime() < d2) {
            try { Thread.sleep(250); } catch (InterruptedException ignored) {}
        }
        if (!mReady) {
            note("CONTROL never reached onPayloadReady either");
            Log.e(TAG, "VERDICT store-create: NOT ANSWERED — it fails both locked and"
                    + " unlocked, so the credential is ruled OUT as the cause"
                    + " and this service shape is the suspect.");
            dumpSummary();
        }
    }

    // --------------------------------------------------------------- the run

    /**
     * Everything that happens down one vsock connection, in the order it has
     * to happen in. Runs on its own thread: every step blocks, and the VM's
     * callbacks must be free to arrive while they do.
     */
    private void runChain() {
        boolean unlockedAtStart = isUnlocked();
        long t = SystemClock.elapsedRealtime();

        note("CHAIN start sinceBoot=" + t + "ms userUnlocked=" + unlockedAtStart);
        if (unlockedAtStart) {
            Log.w(TAG, "CHAIN: the user is ALREADY unlocked. Whatever this run"
                    + " reports it is the CONTROL, not the pre-unlock answer."
                    + " If this is boot 2 and nobody typed a PIN, something"
                    + " else unlocked the phone and the run is void.");
        }

        ParcelFileDescriptor pfd = null;
        try {
            pfd = mVm.connectVsock(PORT);
            FileDescriptor fd = pfd.getFileDescriptor();

            // One fd, two streams, and deliberately NOT the AutoClose
            // variants: those each own the descriptor and whichever closed
            // first would take the other down with it.
            OutputStream os = new FileOutputStream(fd);
            InputStream is = new FileInputStream(fd);
            note("connected to the guest after "
                    + (SystemClock.elapsedRealtime() - t) + "ms");

            // ---- the question itself -------------------------------------
            String phase = unlockedAtStart ? "AFTER-UNLOCK" : "PRE-UNLOCK";
            long[] r = command(os, is, phase + " verify", CMD_VERIFY, 0, 0, mFile);

            if (r == null) {
                note(phase + " verify: NO REPLY");
            } else if (r[0] == ST_OK) {
                judge(phase, r);
            } else if (r[0] == ST_OPEN && r[5] == ENOENT) {
                // First boot on a fresh store. The store OPENED — the guest
                // could reach /mnt/encryptedstore — there is simply no file in
                // it yet. Write one.
                note(phase + " verify: the store is open but empty (ENOENT)."
                        + " This is the FIRST boot on this store.");
                Log.i(TAG, "VERDICT store-create: a NEW encrypted store — a VM"
                        + " name that had never existed, so getOrCreate had no"
                        + " prior directory of any kind — was created and"
                        + " mounted with userUnlocked=" + unlockedAtStart
                        + ". If that reads false, THIS IS THE ANSWER 3e-v could"
                        + " not give: the user's credential is not needed to"
                        + " CREATE a store, only 3e-v's re-open case was ever"
                        + " proven, and this is the first-boot-after-factory-"
                        + "reset case. If it reads true the phone was already"
                        + " unlocked and this run is a control, not the answer.");

                long[] w = command(os, is, phase + " stream", CMD_STREAM,
                        FILE_MB, 0, mFile);
                if (w == null || w[0] != ST_OK) {
                    note(phase + " write FAILED status="
                            + (w == null ? "no reply" : w[0]));
                } else {
                    note(phase + " wrote " + w[3] + " bytes ck64=" + hex(w[2])
                            + " in " + w[1] + "ms");
                    long[] v = command(os, is, phase + " re-verify",
                            CMD_VERIFY, 0, 0, mFile);
                    if (v != null && v[0] == ST_OK) {
                        judge(phase + " (fresh write)", v);
                    } else {
                        note(phase + " re-verify FAILED");
                    }
                }
            } else {
                // The interesting failure: the store did not open.
                note(phase + " verify REFUSED status=" + r[0]
                        + " errno=" + r[5]);
                Log.e(TAG, "VERDICT store-create: the guest could not open " + mFile
                        + " with userUnlocked=" + unlockedAtStart
                        + " (status " + r[0] + ", errno " + r[5] + "). If the"
                        + " after-unlock control below succeeds on the same"
                        + " store, the store is CREDENTIAL-LOCKED and no model"
                        + " can be read at boot.");
            }

            PHASE1_DONE = true;

            // The store-creation answer, whichever way it went, is now in the
            // log. Everything below is the soak and cannot retract it.
            dumpSummary();

            // ---- the soak ------------------------------------------------
            soak(os, is);

            byte[] bye = new byte[4];
            putBe32(bye, 0, 0);
            os.write(bye);
            os.flush();
            note("goodbye sent; the guest should exit 46");

        } catch (Throwable th) {
            note("CHAIN threw " + describe(th));
            Log.e(TAG, "chain failed", th);
        } finally {
            PHASE1_DONE = true;
            if (pfd != null) {
                try { pfd.close(); } catch (Throwable ignored) {}
            }
            dumpSummary();
        }
    }

    /**
     * The soak. One command every SOAK_INTERVAL_MS down the connection that is
     * already open, until something breaks or the phone is rebooted.
     *
     * Each sample re-reads the whole file and re-derives the checksum it ought
     * to have, so the run is self-verifying at every step rather than at the
     * end. Anything other than a clean, correct read ENDS the soak and logs the
     * elapsed time it ended at — that figure is the result.
     */
    private void soak(OutputStream os, InputStream is) {
        final long t0 = SystemClock.elapsedRealtime();
        final long expect = expectedChecksum(FILE_BYTES);
        long deadline = t0 + SOAK_MAX_MS;
        int sample = 0;
        int ok = 0;

        Log.i(TAG, "SOAK START sinceBoot=" + t0 + "ms vm=" + VM_MB + "MB"
                + " interval=" + (mIntervalMs / 1000) + "s"
                + " expectedCk64=" + hex(expect)
                + " — every sample re-reads " + FILE_BYTES + " bytes and"
                + " re-checks them against a value DERIVED here, never copied"
                + " from a log.");
        Log.i(TAG, "SOAK t=0 " + envLine());

        while (SystemClock.elapsedRealtime() < deadline) {
            long sleptFrom = SystemClock.elapsedRealtime();
            try {
                Thread.sleep(mIntervalMs);
            } catch (InterruptedException ignored) {
                // Nothing interrupts this thread on the normal path; if
                // something does, take the sample rather than exit silently.
            }
            long slept = SystemClock.elapsedRealtime() - sleptFrom;
            sample++;

            long elapsed = SystemClock.elapsedRealtime() - t0;
            if (mFinished) {
                Log.e(TAG, "SOAK ENDED at " + fmt(elapsed) + " (sample "
                        + sample + "): the VM stopped or the payload exited"
                        + " while this thread was asleep. Read the callback"
                        + " lines above and the lmkd kill log — a low-memory"
                        + " kill and a guest that died on its own present"
                        + " identically from here.");
                break;
            }

            long[] r = command(os, is, "SOAK " + sample, CMD_VERIFY, 0, 0, mFile);

            if (r == null) {
                Log.e(TAG, "SOAK ENDED at " + fmt(elapsed) + " (sample "
                        + sample + " of which " + ok + " were clean): NO REPLY"
                        + " on a connection that had been open since boot."
                        + " That is the channel or the guest, not the store."
                        + " " + envLine());
                break;
            }
            if (r[0] != ST_OK) {
                Log.e(TAG, "SOAK ENDED at " + fmt(elapsed) + " (sample "
                        + sample + "): verify returned status=" + r[0]
                        + " errno=" + r[5] + ". The guest is alive and"
                        + " answering, so this is the STORE. " + envLine());
                break;
            }
            if (r[3] != FILE_BYTES || r[2] != expect) {
                Log.e(TAG, "SOAK ENDED at " + fmt(elapsed) + " (sample "
                        + sample + "): the file came back " + r[3] + " bytes"
                        + " ck64=" + hex(r[2]) + ", expected " + FILE_BYTES
                        + " / " + hex(expect) + ". The store ROTTED, which is"
                        + " a worse finding than a VM that dies and is exactly"
                        + " what checking every sample exists to catch. "
                        + envLine());
                break;
            }

            ok++;
            Log.i(TAG, "SOAK t=" + fmt(elapsed) + " sample=" + sample
                    + " OK ck64=" + hex(r[2]) + " " + r[3] + " bytes in "
                    + r[1] + "ms  slept=" + slept + "ms"
                    + (Math.abs(slept - mIntervalMs) > mIntervalMs / 2
                        ? " [LATE — doze is moving this thread]" : "")
                    + "  " + envLine());

            // One compact line an hour that survives buffer eviction on its
            // own, for the same reason dumpSummary() exists.
            if (ok % 12 == 0) {
                Log.i(TAG, "SOAK MILESTONE: " + fmt(elapsed) + " elapsed, "
                        + ok + " clean verifies of " + FILE_BYTES + " bytes,"
                        + " checksum unchanged at " + hex(expect)
                        + ", VM " + VM_MB + "MB still up. " + envLine());
            }
        }

        long total = SystemClock.elapsedRealtime() - t0;
        Log.i(TAG, "SOAK STOPPED after " + fmt(total) + " with " + ok
                + " clean verifies out of " + sample + " samples. "
                + (ok == sample && sample > 0
                    ? "Nothing failed: that is an endurance figure, not a"
                      + " reliability one, and it is one run on one phone."
                    : "Read the SOAK ENDED line above for the cause."));
    }

    /** Host memory, guest-side battery and lock state on one line. Read every
     *  sample because a soak that ends with no trend is much less useful than
     *  one that shows memory walking in a direction for six hours first. */
    private String envLine() {
        return "host[" + hostMem() + "] batt[" + battery()
                + "] userUnlocked=" + isUnlocked();
    }

    private String hostMem() {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("MemFree:")
                        || line.startsWith("MemAvailable:")
                        || line.startsWith("Cached:")
                        || line.startsWith("SwapFree:")) {
                    sb.append(line.replaceAll("\\s+", " ")).append(' ');
                }
            }
        } catch (Throwable t) {
            sb.append("unreadable: ").append(t);
        }
        return sb.toString().trim();
    }

    /** Level and charging state. Matt chose MAINS for this run, so a level
     *  that is not pinned near 100 means the cable came out and the run is a
     *  different experiment from the one that was designed.
     *
     *  Via BatteryManager and NOT /sys/class/power_supply, which the smoke test
     *  proved unreadable from this app — SELinux — and which returned a silent
     *  "?%  ?" rather than an error. A soak whose battery column is a question
     *  mark for ten hours answers nothing about battery. */
    private String battery() {
        try {
            BatteryManager bm = getSystemService(BatteryManager.class);
            if (bm == null) {
                return "no BatteryManager";
            }
            int cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            long uah = bm.getLongProperty(
                    BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            return cap + "% " + (bm.isCharging() ? "CHARGING" : "ON BATTERY")
                    + " " + uah + "uAh";
        } catch (Throwable t) {
            return "unreadable: " + t;
        }
    }

    private static String fmt(long ms) {
        long s = ms / 1000;
        return String.format("%dh%02dm%02ds", s / 3600, (s % 3600) / 60, s % 60);
    }

    /**
     * The whole of the reading, done here rather than left to be worked out
     * later from two numbers in a log. 3e-iv could say a file was the right
     * SIZE and not what was in it; this says both, against a checksum that was
     * never read out of an earlier run's log.
     */
    private void judge(String phase, long[] r) {
        long size = r[3];
        long ck = r[2];
        long expect = expectedChecksum(FILE_BYTES);
        boolean sizeOk = (size == FILE_BYTES);
        boolean ckOk = (ck == expect);

        note(phase + " READ " + size + " bytes ck64=" + hex(ck)
                + " expected " + FILE_BYTES + " / " + hex(expect)
                + " in " + r[1] + "ms"
                + " -> SIZE " + (sizeOk ? "MATCH" : "MISMATCH")
                + ", CONTENT " + (ckOk ? "MATCH" : "MISMATCH"));

        if (sizeOk && ckOk) {
            Log.i(TAG, "VERDICT store " + phase + ": YES — the encrypted store"
                    + " opened and handed back " + size + " bytes whose"
                    + " checksum is the one a file of this length must have."
                    + " Not merely the right size, which is all 3e-iv could"
                    + " say: the right BYTES.");
        } else if (sizeOk) {
            Log.e(TAG, "VERDICT store " + phase + ": the store opened and the"
                    + " file is the right length but the CONTENT is wrong."
                    + " That is a different and worse finding than a store that"
                    + " refuses to open, and it is exactly the failure 3e-iv"
                    + " could not have detected.");
        } else {
            Log.e(TAG, "VERDICT store " + phase + ": the store opened but the"
                    + " file is " + size + " bytes, not " + FILE_BYTES
                    + ". Truncation, not corruption.");
        }
    }

    /**
     * One command down the open socket. Returns
     * { status, guestMs, ck64, sizeBytes, aux, extra } or null if the guest
     * stopped replying. Wire format is penny3f_payload.c's:
     *
     *   host -> guest   BE32 length L, BE32 cmd, BE32 arg, BE32 arg2,
     *                   L-12 bytes of path
     *   guest -> host   BE32 44, then eleven BE32 fields
     *
     * CMD_STREAM nests its own chunk stream between the two.
     */
    private long[] command(OutputStream os, InputStream is, String label,
                           int cmd, int arg, int arg2, String path) {
        long t0 = SystemClock.elapsedRealtime();
        try {
            byte[] pb = path.getBytes("UTF-8");
            byte[] req = new byte[4 + 12 + pb.length];
            putBe32(req, 0, 12 + pb.length);
            putBe32(req, 4, cmd);
            putBe32(req, 8, arg);
            putBe32(req, 12, arg2);
            System.arraycopy(pb, 0, req, 16, pb.length);
            os.write(req);
            os.flush();
            Log.i(TAG, "[" + label + "] sent cmd=" + cmd + " arg=" + arg
                    + " path='" + path + "'");

            if (cmd == CMD_STREAM) {
                long sent = generate(os, FILE_BYTES);
                Log.i(TAG, "[" + label + "] pushed " + FILE_BYTES
                        + " bytes, our ck64=" + hex(sent));
            }

            byte[] rhdr = readFully(is, 4);
            if (rhdr == null) {
                return null;
            }
            int rlen = getBe32(rhdr, 0);
            byte[] body = readFully(is, rlen);
            if (body == null || rlen < 44) {
                return null;
            }

            int st       = getBe32(body, 0);
            long freeBef = getBe32(body, 4)  & 0xffffffffL;
            long freeAft = getBe32(body, 8)  & 0xffffffffL;
            int guestMs  = getBe32(body, 16);
            int extra    = getBe32(body, 20);
            long ck      = ((getBe32(body, 24) & 0xffffffffL) << 32)
                         | (getBe32(body, 28) & 0xffffffffL);
            long size    = ((getBe32(body, 32) & 0xffffffffL) << 32)
                         | (getBe32(body, 36) & 0xffffffffL);
            int aux      = getBe32(body, 40);

            Log.i(TAG, "[" + label + "] status=" + st + " guestMs=" + guestMs
                    + " extra=" + extra + " ck64=" + hex(ck) + " size=" + size
                    + " aux=" + aux + " guestMemFree " + freeBef + " -> "
                    + freeAft + " kB hostWallMs="
                    + (SystemClock.elapsedRealtime() - t0));

            return new long[] { st, guestMs, ck, size, aux, extra };
        } catch (Throwable th) {
            Log.e(TAG, "[" + label + "] threw " + describe(th), th);
            return null;
        }
    }

    /**
     * Probe3fActivity's generator, byte for byte, because the checksum only
     * means anything if both ends produce the same stream. Writes the chunks
     * to os if it is non-null and returns the FNV-1a of what was produced;
     * with a null os it produces nothing and only computes, which is how the
     * expected value for a re-read is obtained without consulting an earlier
     * run's log.
     *
     * The bytes MUST NOT compress: microdroid gives every guest a zram swap
     * device sized to its whole RAM, and a stream of zeros would prove nothing
     * about a disk. Every word comes from a running xorshift64*.
     */
    private long generate(OutputStream os, long totalBytes) throws Exception {
        byte[] buf = new byte[CHUNK];
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(buf)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        byte[] lhdr = new byte[4];
        long h = FNV_BASIS;
        long rng = SEED;
        long sent = 0;

        while (sent < totalBytes) {
            int len = (int) Math.min((long) CHUNK, totalBytes - sent);
            bb.clear();
            int whole = len / 8;
            for (int i = 0; i < whole; i++) {
                rng ^= rng >>> 12;
                rng ^= rng << 25;
                rng ^= rng >>> 27;
                long w = rng * 2685821657736338717L;
                bb.putLong(w);
                h ^= w;
                h *= FNV_PRIME;
            }
            for (int i = whole * 8; i < len; i++) {
                rng ^= rng >>> 12;
                rng ^= rng << 25;
                rng ^= rng >>> 27;
                byte v = (byte) (rng * 2685821657736338717L);
                buf[i] = v;
                h ^= (v & 0xffL);
                h *= FNV_PRIME;
            }
            if (os != null) {
                putBe32(lhdr, 0, len);
                os.write(lhdr);
                os.write(buf, 0, len);
            }
            sent += len;
        }
        if (os != null) {
            putBe32(lhdr, 0, 0);
            os.write(lhdr);
            os.flush();
        }
        return h;
    }

    /** The checksum a correct file of this length must have. Computed, never
     *  copied out of a log — which is what makes boot 2 self-verifying. */
    private long expectedChecksum(long bytes) {
        try {
            return generate(null, bytes);
        } catch (Exception e) {
            return 0L;
        }
    }

    // -------------------------------------------------------------- plumbing

    private boolean isUnlocked() {
        UserManager um = getSystemService(UserManager.class);
        return um != null && um.isUserUnlocked();
    }

    /** Polls rather than registering a receiver. ACTION_USER_UNLOCKED would do
     *  the same job with one more moving part that could itself be the reason
     *  a run produced nothing. */
    private boolean awaitUnlock() {
        long deadline = SystemClock.elapsedRealtime() + UNLOCK_TIMEOUT_MS;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (isUnlocked()) {
                return true;
            }
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        }
        return isUnlocked();
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

    /** See the field comment: the boot window's logcat buffer is at its default
     *  size and four guest consoles can evict a run's own lines. */
    private void dumpSummary() {
        synchronized (mSummary) {
            Log.i(TAG, "SUMMARY soak :: " + mSummary);
        }
    }

    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Penny soak", NotificationManager.IMPORTANCE_LOW));

        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Penny soak")
                .setContentText("2GB VM held open")
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
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

    private static String describe(Throwable t) {
        return t.getClass().getName() + ": " + t.getMessage();
    }

    private static String hex(long v) {
        return String.format("0x%016x", v);
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
        Log.w(TAG, "==== soak service onDestroy — the VM dies with this"
                + " process, sinceBoot=" + SystemClock.elapsedRealtime() + "ms");
        PHASE1_DONE = true;
        super.onDestroy();
    }

    private class Callbacks implements VirtualMachineCallback {

        @Override
        public void onPayloadStarted(VirtualMachine vm) {
            Log.i(TAG, "CB onPayloadStarted sinceBoot="
                    + SystemClock.elapsedRealtime() + "ms");
        }

        @Override
        public void onPayloadReady(VirtualMachine vm) {
            // Cannot happen by accident: microdroid does not decide a payload
            // is ready, the payload says so by calling
            // AVmPayload_notifyPayloadReady() — and penny3f_payload.c calls it
            // only after listen() has succeeded.
            if (mReady) {
                return;
            }
            mReady = true;
            note("onPayloadReady sinceBoot=" + SystemClock.elapsedRealtime()
                    + "ms userUnlocked=" + isUnlocked()
                    + " — the guest is up WITH its encrypted store mounted");
            new Thread(PennySoakService.this::runChain, "pennysoak-chain").start();
        }

        @Override
        public void onPayloadFinished(VirtualMachine vm, int exitCode) {
            mFinished = true;
            note("onPayloadFinished exitCode=" + exitCode
                    + (exitCode == 46 ? " (clean — the guest said goodbye"
                            + " itself)" : " (a failure path)"));
        }

        @Override
        public void onError(VirtualMachine vm, int errorCode, String message) {
            note("onError code=" + errorCode + " message=" + message);
            Log.e(TAG, "CB onError — if this names the encrypted store or a"
                    + " key, it IS the store-create answer. Quote it verbatim.");
        }

        @Override
        public void onStopped(VirtualMachine vm, int reason) {
            mFinished = true;
            note("onStopped reason=" + reason);
        }
    }
}

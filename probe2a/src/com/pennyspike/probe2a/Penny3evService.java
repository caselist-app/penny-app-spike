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

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Rung 3e-v: is the encrypted store readable BEFORE first unlock?
 *
 * THE ONE THAT CAN STILL CLOSE THE PLAN DOWN. Every unattended result in this
 * repo — rung 3's wake, 3b's microphone, 3d's whole voice path — was measured
 * before first unlock and is genuinely unattended. The model story is not, and
 * has never been tested: 3e-iii put a 1.5GB file on a real encrypted ext4 disk
 * and 3e-iv proved it survives a power cycle, but BOTH of those reads happened
 * on an unlocked phone. If the store's dm-crypt key turns out to be tied to the
 * user's credential, then no model can be read at boot, and everything this
 * repo has proven about waking unattended applies only to a phone somebody has
 * already unlocked once.
 *
 * THIS IS A COPY OF Penny3dService, NOT AN EDIT OF IT. Penny3dService carries
 * rung 3d's result over three reboots and VmService carries rung 3's over
 * seven; neither is touched. This service starts from the same broadcast, owns
 * its OWN VM name, its OWN encrypted store and its OWN written file, and can
 * neither be taken down by them nor take them down.
 *
 * ── WHY IT CAN ONLY BE ASKED THIS WAY ──────────────────────────────────────
 *
 * GrapheneOS keeps the USB port charging-only while the phone is locked. That
 * was measured twice — once in rung 3, once in 3e-iv where it cost the whole
 * second half of the rung. `adb shell am start`, `am broadcast` and `dumpsys`
 * cannot reach the phone at the lock screen at all. So a pre-unlock question
 * can ONLY be asked by a directBootAware component that starts ITSELF from
 * LOCKED_BOOT_COMPLETED and writes its answer to logcat, read back after an
 * unlock. That is why this is a service and not an activity, and it was forced
 * rather than chosen.
 *
 * ── THE SCRIPT, WHICH IS THE SAME ON EVERY BOOT ────────────────────────────
 *
 * There is deliberately no "which boot is this" flag, no intent extra and no
 * saved state. The service asks the store what it contains and acts on the
 * answer, which makes boot 1 and boot 2 the same code and removes a whole
 * class of "the probe was configured wrong" doubt:
 *
 *   1  getOrCreate the VM, run it, wait for the payload
 *   2  CMD_VERIFY the file — logging userUnlocked AT THAT INSTANT
 *   3  if it came back ENOENT, this is the first boot: CMD_STREAM writes it,
 *      then CMD_VERIFY confirms what landed
 *   4  release rung 3g-i, which is waiting on us
 *   5  hold the socket open, wait for the human, CMD_VERIFY once more
 *
 * BOOT 1 takes path 3. The store does not exist, so it is created and written
 * with the phone still locked — which is already half an answer, because a
 * store whose key needed the credential could not be created here either.
 * BOOT 2 stops at step 2. The store is RE-OPENED pre-unlock, and that is the
 * answer to 3e-v.
 *
 * ── THE CONTROL, AND WHY IT RIDES THE SAME SOCKET ──────────────────────────
 *
 * If the pre-unlock read fails, "the store is credential-locked" and "this
 * service is shaped wrong" must stay separable, so the same service reads the
 * same store again after an unlock on the same boot.
 *
 * It cannot do that on a second connection. penny3f_payload.c calls accept4()
 * exactly ONCE and then loops on commands until a zero-length frame; a second
 * connectVsock would never be accepted. So the socket is held open across the
 * unlock wait and the control command goes down the same one. That is forced
 * by the payload — and it happens to be the strongest form the control could
 * take, because between the two reads literally nothing changes but the PIN.
 *
 * The cost is that a 256MB VM stays resident while rung 3g-i brings up a
 * 2048MB one. That was weighed and accepted: our own app has never been killed
 * at 2048MB, and step 4 above means 3e-v's pre-unlock verdict is already in
 * the log before 3g-i is allowed to start. If the low-memory killer does take
 * us, what is lost is the control, not the answer.
 *
 * ── THE CHECKSUM IS CHECKED BY THE MACHINE, NOT BY READING TWO LOGS ────────
 *
 * 3e-iv verified a stored file's SIZE across a reboot and could not verify its
 * CONTENT — a store handing back 1610612736 bytes of zeroes would have logged
 * identically — and its own file is now stranded, so that gap was still open.
 *
 * Probe3fActivity's byte generator is a fixed-seed xorshift64*, so a file of a
 * given length has exactly ONE correct ck64. expectedChecksum() below runs the
 * identical generator without sending anything and compares. Boot 2 therefore
 * logs CONTENT MATCH or CONTENT MISMATCH outright, and the loose end 3e-iv
 * left — does a stored file survive a reboot with its content intact — is
 * answered here for free, and answered BEFORE first unlock, which is more than
 * that loose end asked for.
 *
 * ── DONE MEANS ─────────────────────────────────────────────────────────────
 *
 *   1  yes/no the store opens with userUnlocked=false, at a timestamp that
 *      precedes LockSettingsService's "unlockUser started"
 *   2  the size AND the checksum of what came back
 *   3  the after-unlock control on the same boot
 */
public class Penny3evService extends Service {

    private static final String TAG = "PENNY3EV";
    private static final String VM_NAME = "penny3ev";
    private static final String CHANNEL = "penny3ev";
    private static final int NOTIFICATION_ID = 5;

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

    private static final long VM_MB = 256L;

    /** 3e-iii's one line of config: a real ext4 on dm-crypt at
     *  /mnt/encryptedstore. Without it a microdroid guest has no writable
     *  filesystem at all. 256MB is ample for a 64MB file plus ext4's own
     *  metadata, and small enough not to be a memory experiment in disguise. */
    private static final long STORE_MB = 256L;

    private static final String FILE = "/mnt/encryptedstore/penny3ev.bin";

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
    private static final long UNLOCK_TIMEOUT_MS = 45L * 60_000L;

    /**
     * Rung 3g-i waits on this before bringing up its 2048MB VM, so that the
     * result which can close the plan down is banked in the log before the
     * low-memory killer is deliberately provoked.
     *
     * The dependency is ONE-WAY and cannot deadlock: Penny3giService also
     * gives up waiting after its own timeout, and nothing here ever reads
     * anything of 3g-i's. Set in a finally block, and set by the watchdog too,
     * so a failure on this side still releases that one.
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
        Log.i(TAG, "==== rung 3e-v service onCreate ==== uid=" + Process.myUid()
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
            Log.e(TAG, "VERDICT 3e-v: NOT TESTED — the service could not stay"
                    + " alive, so the store was never asked anything.");
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

        // Its own thread, never mExecutor: mExecutor is what setCallback() is
        // handed, and a watchdog sleeping on it blocks the very callback it is
        // waiting for. That cost a run on 3e-i.
        new Thread(this::watchdog, "penny3ev-watchdog").start();

        mExecutor.execute(() -> {
            try {
                bringUp("pre-unlock attempt");
            } catch (Throwable t) {
                Log.e(TAG, "BRING-UP FAILED -> " + describe(t), t);
                Log.e(TAG, "VERDICT 3e-v: the VM could not be created or run"
                        + " before first unlock. If the message names the"
                        + " encrypted store, that IS the 3e-v answer and it is"
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
            Log.e(TAG, "VERDICT 3e-v: NOT TESTED — no VirtualMachineManager");
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
                .setCpuTopology(VirtualMachineConfig.CPU_TOPOLOGY_ONE_CPU)
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
        mVm = vmm.getOrCreate(VM_NAME, config);
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
            note("RESETTING " + VM_NAME + ": delete + create fresh");
            try {
                vmm.delete(VM_NAME);
            } catch (VirtualMachineException ignored) {
            }
            mVm = vmm.create(VM_NAME, config);
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
            File dir = new File(ctx.getDataDir(), "vm/" + VM_NAME);
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
        Log.e(TAG, "VERDICT 3e-v so far: the VM did not reach its payload"
                + " before first unlock. Check the lmkd kill log (see CLAUDE.md) —"
                + " a low-memory kill and a store that refused to open look"
                + " identical from here. The control below is what separates"
                + " them.");
        PHASE1_DONE = true;

        if (!awaitUnlock()) {
            Log.e(TAG, "VERDICT 3e-v: INCONCLUSIVE — nobody unlocked the phone,"
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
            Log.e(TAG, "VERDICT 3e-v: the same call fails unlocked too, so the"
                    + " fault is NOT the user's credential. Something else is"
                    + " wrong with this service or this VM and 3e-v is NOT"
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
            Log.e(TAG, "VERDICT 3e-v: NOT ANSWERED — it fails both locked and"
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
            long[] r = command(os, is, phase + " verify", CMD_VERIFY, 0, 0, FILE);

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
                Log.i(TAG, "VERDICT 3e-v (first boot, partial): a NEW encrypted"
                        + " store was created and mounted with userUnlocked="
                        + unlockedAtStart + ". That rules out the credential"
                        + " being needed to CREATE one. Whether it can be"
                        + " RE-OPENED before first unlock is the next boot.");

                long[] w = command(os, is, phase + " stream", CMD_STREAM,
                        FILE_MB, 0, FILE);
                if (w == null || w[0] != ST_OK) {
                    note(phase + " write FAILED status="
                            + (w == null ? "no reply" : w[0]));
                } else {
                    note(phase + " wrote " + w[3] + " bytes ck64=" + hex(w[2])
                            + " in " + w[1] + "ms");
                    long[] v = command(os, is, phase + " re-verify",
                            CMD_VERIFY, 0, 0, FILE);
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
                Log.e(TAG, "VERDICT 3e-v: the guest could not open " + FILE
                        + " with userUnlocked=" + unlockedAtStart
                        + " (status " + r[0] + ", errno " + r[5] + "). If the"
                        + " after-unlock control below succeeds on the same"
                        + " store, the store is CREDENTIAL-LOCKED and no model"
                        + " can be read at boot.");
            }

            // ---- release rung 3g-i ---------------------------------------
            // Before the unlock wait, and in the same order every boot: the
            // answer is in the log, so 3g-i may now provoke the low-memory
            // killer with a 2048MB VM.
            PHASE1_DONE = true;
            Log.i(TAG, "3g-i released at sinceBoot="
                    + SystemClock.elapsedRealtime() + "ms");

            // ---- the control ---------------------------------------------
            if (!unlockedAtStart) {
                Log.i(TAG, "holding the socket open and waiting for a human to"
                        + " type the PIN. The payload accepts exactly one"
                        + " connection, so the control MUST ride this one.");
                if (awaitUnlock()) {
                    note("unlock seen at sinceBoot="
                            + SystemClock.elapsedRealtime() + "ms");
                    long[] c = command(os, is, "AFTER-UNLOCK CONTROL verify",
                            CMD_VERIFY, 0, 0, FILE);
                    if (c == null) {
                        note("CONTROL: no reply — the VM did not survive the"
                                + " wait. Check the lmkd kill log (see CLAUDE.md).");
                    } else if (c[0] == ST_OK) {
                        judge("AFTER-UNLOCK CONTROL", c);
                    } else {
                        note("CONTROL verify status=" + c[0] + " errno=" + c[5]);
                    }
                } else {
                    note("CONTROL: nobody unlocked the phone within "
                            + (UNLOCK_TIMEOUT_MS / 60000) + " minutes");
                }
            }

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
            Log.i(TAG, "VERDICT 3e-v " + phase + ": YES — the encrypted store"
                    + " opened and handed back " + size + " bytes whose"
                    + " checksum is the one a file of this length must have."
                    + " Not merely the right size, which is all 3e-iv could"
                    + " say: the right BYTES.");
        } else if (sizeOk) {
            Log.e(TAG, "VERDICT 3e-v " + phase + ": the store opened and the"
                    + " file is the right length but the CONTENT is wrong."
                    + " That is a different and worse finding than a store that"
                    + " refuses to open, and it is exactly the failure 3e-iv"
                    + " could not have detected.");
        } else {
            Log.e(TAG, "VERDICT 3e-v " + phase + ": the store opened but the"
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
            Log.i(TAG, "SUMMARY 3e-v :: " + mSummary);
        }
    }

    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Penny 3e-v", NotificationManager.IMPORTANCE_LOW));

        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Penny 3e-v")
                .setContentText("encrypted store before first unlock")
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
        Log.w(TAG, "==== rung 3e-v service onDestroy — the VM dies with this"
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
            new Thread(Penny3evService.this::runChain, "penny3ev-chain").start();
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
                    + " key, it IS the 3e-v answer. Quote it verbatim.");
        }

        @Override
        public void onStopped(VirtualMachine vm, int reason) {
            mFinished = true;
            note("onStopped reason=" + reason);
        }
    }
}

package com.pennyspike.probe2a;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
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
 * Rung 3d: the join. Boot, locked, nobody in the room.
 *
 * WHAT IS BEING JOINED, AND WHY IT HAS NEVER BEEN DONE IN ONE PLACE.
 *   3    the app owns a VM and it is up 14-16s after power-on, locked   (VmService)
 *   3b   the app holds a live microphone 9.5s after power-on, locked    (MicFgsService)
 *   2d   the app runs its own compiled code inside that VM             (Probe2dActivity)
 *   3c   real captured audio crosses vsock into that code              (Probe3cActivity)
 * Every one of those is proven on this same APK. The last two ran UNLOCKED, in
 * the foreground, driven by hand over adb. This service is the first thing
 * that attempts all four in a single boot with no human involved.
 *
 * THIS IS A COPY OF VmService, NOT AN EDIT OF IT. VmService carries the rung 3
 * wake result, reproduced over four reboots, and it is the one file this spike
 * has never touched. 2d and 3c were each kept in their own component for the
 * same reason. Both services start from the same boot broadcast and neither
 * can take the other down: if this one is refused at startForeground, or dies
 * on the VM, rung 3 still reproduces on the same reboot.
 *
 * THE ORDERING DECISION, MADE DELIBERATELY AND WRITTEN DOWN BECAUSE A LOG
 * CANNOT DISTINGUISH IT AFTERWARDS. The microphone is available at ~9.5s; the
 * VM is not ready until ~14s. So the audio would exist before there is
 * anywhere to send it. Two options: buffer an early capture, or capture when
 * the guest says it is listening. THIS TAKES THE SECOND. Reasons, in order:
 *   - MicFgsService is already capturing at ~12.3s on this same boot. A second
 *     AudioRecord in the same app at the same moment risks contention, and a
 *     refusal caused by our own other service would read exactly like the OS
 *     suppressing background audio. Capturing at ~14s steps clear of it.
 *   - It keeps the failure modes separable. "No audio" and "audio but no
 *     crossing" are then two different log lines, not one ambiguous one.
 *   - It is the harder case for the exemption: it asks for the microphone
 *     LATER in boot, not earlier, so a yes here covers the easy case too.
 * If this produces audio at 3c's quality but no crossing, the next run buffers
 * instead. That is a second experiment, not a retry.
 *
 * THE SILENCE TRAP IS THE WHOLE RISK OF THIS RUNG. Android 17 suppresses
 * background audio without throwing — AudioRecord initialises, reports
 * RECORDSTATE_RECORDING, and hands back zeros. 32,000 zero bytes hash
 * perfectly at both ends, so a silent capture would sail through the checksum
 * comparison and look exactly like a pass. capture() therefore judges peak,
 * RMS and the non-zero proportion and returns null rather than let that
 * happen. A yes on 3d that is not also a yes on real samples is not a yes.
 *
 * DONE MEANS all six, and they are checked in the log by hand afterwards:
 *   1  userUnlocked=false AT THE MOMENT OF THE EXCHANGE, not merely at boot
 *   2  the audio is real on the samples, not merely present
 *   3  the guest console reports the same FNV-1a we computed here
 *   4  guest exit code 43
 *   5  every sinceBoot= above precedes the first unlock
 *   6  a second reboot does the same
 */
public class Penny3dService extends Service {

    private static final String TAG = "PENNY3D";
    private static final String VM_NAME = "penny3d";
    private static final String CHANNEL = "penny3d";
    private static final int NOTIFICATION_ID = 3;

    /** 3c's payload, unchanged and not rebuilt. The wire protocol is the same
     *  question; recompiling it would add a variable for nothing. */
    private static final String PAYLOAD = "Penny3cPayload.so";

    /** Must match PORT in penny3c_payload.c. */
    private static final long PORT = 5555L;

    private static final int SAMPLE_RATE = 16000;

    /** Held for the life of the process. Drop this and the VM dies. */
    private VirtualMachine mVm;

    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    /** LOCKED_BOOT_COMPLETED and then BOOT_COMPLETED both arrive, and
     *  START_STICKY can bring us back. Boot the VM once. */
    private boolean mStarted;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "==== rung 3d service onCreate ==== uid=" + Process.myUid()
                + " sinceBoot=" + SystemClock.elapsedRealtime() + "ms");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String why = (intent == null) ? "RESTARTED-BY-SYSTEM (intent is null)"
                : intent.getStringExtra("why");
        Log.i(TAG, "onStartCommand why=" + why + " flags=" + flags
                + " startId=" + startId
                + " sinceBoot=" + SystemClock.elapsedRealtime() + "ms");

        // First, and fast. The boot broadcast's exemption is 20 seconds and
        // everything below it is slower than this.
        try {
            goForeground();
            Log.i(TAG, "STEP1 startForeground(SPECIAL_USE|MICROPHONE) OK");
        } catch (Throwable t) {
            Log.e(TAG, "STEP1 startForeground REFUSED -> " + describe(t), t);
            Log.e(TAG, "VERDICT 3d: NOT TESTED — the service could not stay"
                    + " alive. If this is SecurityException about the"
                    + " foreground-only permission, the assistant role is not"
                    + " held and the preconditions were not met. Check"
                    + " voice_recognition_service before re-running.");
            stopSelf();
            return START_NOT_STICKY;
        }

        // Every precondition, logged before any of them can matter. A run
        // missing any of these is void, and the only way to know that
        // afterwards is to have written them down at the time.
        UserManager um = getSystemService(UserManager.class);
        Log.i(TAG, "STEP2 MANAGE_VIRTUAL_MACHINE=" + perm(
                        "android.permission.MANAGE_VIRTUAL_MACHINE")
                + " RECORD_AUDIO=" + perm(android.Manifest.permission.RECORD_AUDIO)
                + " userUnlocked=" + (um == null ? "?" : um.isUserUnlocked()));

        if (mStarted) {
            Log.i(TAG, "already started by an earlier delivery — nothing to do");
            return START_STICKY;
        }
        mStarted = true;

        mExecutor.execute(() -> {
            try {
                boot();
            } catch (Throwable t) {
                mStarted = false;
                Log.e(TAG, "BOOT FAILED -> " + describe(t), t);
                Log.e(TAG, "VERDICT 3d: NO — no VM came up, so the audio was"
                        + " never given anywhere to go. This is a rung 3"
                        + " regression, not a rung 3d answer.");
            }
        });

        return START_STICKY;
    }

    private void boot() throws Exception {
        // Device-protected storage. Before first unlock /data/user/0/<pkg>
        // does not exist, and the VM directory is built relative to whichever
        // Context the API is handed. This single line is what makes a VM
        // possible at all before the PIN.
        Context ctx = createDeviceProtectedStorageContext();
        Log.i(TAG, "STEP3 device-protected dataDir=" + ctx.getDataDir());

        VirtualMachineManager vmm = ctx.getSystemService(VirtualMachineManager.class);
        Log.i(TAG, "STEP4 manager = " + vmm);
        if (vmm == null) {
            Log.e(TAG, "VERDICT 3d: NO — no manager");
            return;
        }

        // OUR apk and OUR payload, as 2d and 3c did — not the APEX's. This is
        // the difference between VmService (which boots Google's empty
        // payload) and this one.
        String apkPath = getApplicationInfo().sourceDir;
        File apk = new File(apkPath);
        Log.i(TAG, "STEP5 our apk = " + apkPath
                + " readable=" + apk.canRead() + " bytes=" + apk.length());

        VirtualMachineConfig config = new VirtualMachineConfig.Builder(ctx)
                .setApkPath(apkPath)
                .setPayloadBinaryName(PAYLOAD)
                .setDebugLevel(VirtualMachineConfig.DEBUG_LEVEL_FULL)
                .setProtectedVm(false)
                .setMemoryBytes(256L * 1024L * 1024L)
                .setCpuTopology(VirtualMachineConfig.CPU_TOPOLOGY_ONE_CPU)
                .build();
        Log.i(TAG, "STEP6 config built, payload=" + PAYLOAD);

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
        Log.i(TAG, "STEP10 waiting for onPayloadReady. The guest calls that"
                + " only AFTER it is listening on vsock " + PORT + ", so the"
                + " connect below cannot race it.");
    }

    // ------------------------------------------------------------- the chain

    /**
     * Everything that has to happen while the phone is still locked, in the
     * order it has to happen in. Runs on its own thread: every step below
     * blocks, and onPayloadFinished must be free to arrive while they do.
     */
    private void runChain() {
        UserManager um = getSystemService(UserManager.class);
        boolean unlocked = um != null && um.isUserUnlocked();
        long t = SystemClock.elapsedRealtime();

        // Condition 1 of the six, and the one that decides whether anything
        // below is worth reading. Logged HERE, at the moment of the exchange,
        // not at boot — a phone unlocked in between would invalidate the run
        // and nothing else in the log would say so.
        Log.i(TAG, "CHAIN start sinceBoot=" + t + "ms userUnlocked=" + unlocked);
        if (unlocked) {
            Log.w(TAG, "CHAIN WARNING: the user is already unlocked. Whatever"
                    + " happens below, it is NOT an answer to rung 3d. Reboot"
                    + " and do not touch the phone.");
        }

        // The control leg first, exactly as 2d and 3c did. ASCII the guest
        // cannot have produced on its own, through the same channel, in the
        // same VM, moments before the audio. If this fails, the fault is the
        // channel and the microphone is not implicated at all.
        String ctl = "PENNY3D-CONTROL " + SystemClock.elapsedRealtime();
        boolean control = exchange("control",
                ctl.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Log.i(TAG, "CHAIN control leg ok=" + control);
        if (!control) {
            Log.e(TAG, "VERDICT 3d: NO — the vsock channel did not work at"
                    + " boot. 3c proved it works unlocked in the foreground, so"
                    + " the difference is the boot context, not the mechanism."
                    + " The microphone was never asked and is not implicated.");
            sayGoodbye();
            return;
        }

        byte[] pcm = capture();
        if (pcm == null) {
            Log.e(TAG, "VERDICT 3d: PARTIAL — the VM woke, our code ran inside"
                    + " it and the channel carried bytes at boot with the phone"
                    + " locked, but there was no usable audio to send. That is"
                    + " a MICROPHONE result, not a channel result. Do not write"
                    + " it up as a 3d NO without saying which half failed.");
            sayGoodbye();
            return;
        }

        boolean ok = exchange("audio", pcm);
        if (ok) {
            Log.i(TAG, "VERDICT 3d: YES on the host's own evidence — "
                    + pcm.length + " bytes of real captured PCM crossed into"
                    + " the guest at " + SystemClock.elapsedRealtime() + "ms"
                    + " after power-on with userUnlocked=" + unlocked + "."
                    + " NOT FINAL: confirm the guest console carries the same"
                    + " fnv1a, confirm exit code 43, and reproduce on a second"
                    + " reboot. Four of six is not six.");
        } else {
            Log.e(TAG, "VERDICT 3d: NO — real audio was captured and the"
                    + " control leg crossed, but the audio did not survive the"
                    + " trip. Compare the two fnv1a values: equal hashes with a"
                    + " failed echo is a different fault from unequal hashes.");
        }
        sayGoodbye();
    }

    /**
     * One request/response over one vsock connection, wire-compatible with
     * penny3c_payload.c:
     *   out   4 bytes big-endian length L, then L bytes
     *   in    4 bytes big-endian L, 4 bytes big-endian FNV-1a, then L bytes
     *
     * Both the hash AND the echo must match. The hash alone would not catch a
     * return leg that dropped data; the echo alone would not catch the guest
     * hashing something other than what it echoed.
     */
    private boolean exchange(String label, byte[] out) {
        long t0 = SystemClock.elapsedRealtime();
        ParcelFileDescriptor pfd = null;
        try {
            pfd = mVm.connectVsock(PORT);
            Log.i(TAG, "[" + label + "] connectVsock(" + PORT + ") -> " + pfd
                    + " after " + (SystemClock.elapsedRealtime() - t0) + "ms");

            // One fd, two streams, and deliberately NOT the AutoClose
            // variants: those each own the descriptor and whichever closed
            // first would take the other down with it.
            FileDescriptor fd = pfd.getFileDescriptor();
            OutputStream os = new FileOutputStream(fd);
            InputStream is = new FileInputStream(fd);

            byte[] hdr = new byte[4];
            putBe32(hdr, 0, out.length);
            os.write(hdr);
            os.write(out);
            os.flush();
            Log.i(TAG, "[" + label + "] sent " + out.length + " bytes,"
                    + " our fnv1a=" + hex32(fnv1a(out, out.length)));

            byte[] rhdr = readFully(is, 8);
            if (rhdr == null) {
                Log.e(TAG, "[" + label + "] the guest closed before replying");
                return false;
            }
            int rlen = getBe32(rhdr, 0);
            int rhash = getBe32(rhdr, 4);
            Log.i(TAG, "[" + label + "] guest replied len=" + rlen
                    + " fnv1a=" + hex32(rhash));

            if (rlen != out.length) {
                Log.e(TAG, "[" + label + "] the guest saw " + rlen
                        + " bytes but we sent " + out.length);
                return false;
            }

            byte[] back = readFully(is, rlen);
            if (back == null) {
                Log.e(TAG, "[" + label + "] the echo was truncated");
                return false;
            }

            int ours = fnv1a(out, out.length);
            boolean hashOk = (ours == rhash);
            boolean echoOk = true;
            int firstBad = -1;
            for (int i = 0; i < out.length; i++) {
                if (out[i] != back[i]) { echoOk = false; firstBad = i; break; }
            }

            Log.i(TAG, "[" + label + "] hashMatch=" + hashOk
                    + " echoMatch=" + echoOk
                    + (echoOk ? "" : " firstMismatchAt=" + firstBad)
                    + " roundTrip=" + (SystemClock.elapsedRealtime() - t0) + "ms");
            return hashOk && echoOk;

        } catch (Throwable th) {
            Log.e(TAG, "[" + label + "] threw " + th.getClass().getName()
                    + ": " + th.getMessage(), th);
            return false;
        } finally {
            if (pfd != null) {
                try { pfd.close(); } catch (Throwable ignored) {}
            }
        }
    }

    /** Length 0 tells the guest to stop accepting and exit 43. */
    private void sayGoodbye() {
        ParcelFileDescriptor pfd = null;
        try {
            pfd = mVm.connectVsock(PORT);
            OutputStream os = new FileOutputStream(pfd.getFileDescriptor());
            os.write(new byte[] { 0, 0, 0, 0 });
            os.flush();
            Log.i(TAG, "sent the goodbye (length 0)");
        } catch (Throwable th) {
            Log.w(TAG, "goodbye failed: " + th);
        } finally {
            if (pfd != null) {
                try { pfd.close(); } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * One real second from the microphone, as little-endian 16-bit PCM — and
     * null rather than silence. See the class comment: a checksum over zeros
     * matches trivially, so this is the only thing standing between a
     * suppressed microphone and a confident false YES.
     */
    private byte[] capture() {
        long t = SystemClock.elapsedRealtime();
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "AUDIO: RECORD_AUDIO is not granted — silence would"
                    + " prove nothing. Grant it and re-run.");
            return null;
        }

        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuf <= 0) {
            Log.e(TAG, "AUDIO: getMinBufferSize returned " + minBuf);
            return null;
        }

        AudioRecord rec = null;
        try {
            rec = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minBuf * 2, SAMPLE_RATE * 2));
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AUDIO: AudioRecord failed to initialise (state="
                        + rec.getState() + "). That is a HARD refusal, which is"
                        + " a different finding from suppression.");
                return null;
            }
            rec.startRecording();
            if (rec.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "AUDIO: refused to enter the recording state");
                return null;
            }

            short[] buf = new short[SAMPLE_RATE];      // one second
            int total = 0, peak = 0, nonZero = 0;
            long sumSquares = 0;
            long deadline = SystemClock.elapsedRealtime() + 2500;
            while (total < buf.length && SystemClock.elapsedRealtime() < deadline) {
                int n = rec.read(buf, total, buf.length - total);
                if (n <= 0) break;
                total += n;
            }
            for (int i = 0; i < total; i++) {
                int v = Math.abs(buf[i]);
                if (v != 0) nonZero++;
                if (v > peak) peak = v;
                sumSquares += (long) buf[i] * buf[i];
            }
            double rms = total > 0 ? Math.sqrt((double) sumSquares / total) : 0.0;
            double pct = total > 0 ? (100.0 * nonZero / total) : 0.0;
            Log.i(TAG, "AUDIO: startedAt=" + t + "ms samples=" + total
                    + " peak=" + peak
                    + " rms=" + String.format("%.2f", rms)
                    + " nonZero=" + nonZero
                    + " (" + String.format("%.1f", pct) + "%)");

            if (total == 0 || nonZero == 0 || peak < 4 || pct < 5.0) {
                Log.e(TAG, "AUDIO: silence or near-silence. Sending it would"
                        + " produce a matching checksum that means nothing."
                        + " Refusing, so 3d cannot be misread as a pass."
                        + " NOTE: a quiet room and a suppressed microphone look"
                        + " the same here — make noise near the phone and"
                        + " reboot before calling this a NO.");
                return null;
            }

            byte[] pcm = new byte[total * 2];
            for (int i = 0; i < total; i++) {
                pcm[i * 2]     = (byte) (buf[i] & 0xff);
                pcm[i * 2 + 1] = (byte) ((buf[i] >> 8) & 0xff);
            }
            return pcm;

        } catch (Throwable th) {
            Log.e(TAG, "AUDIO: threw " + th.getClass().getName()
                    + ": " + th.getMessage(), th);
            return null;
        } finally {
            if (rec != null) {
                try { rec.stop(); } catch (Throwable ignored) {}
                rec.release();
            }
        }
    }

    // -------------------------------------------------------------- plumbing

    /**
     * Two types at once, and both are needed for different halves of the rung.
     * SPECIAL_USE is what honestly describes holding a VM and is what rung 3
     * proved may start at boot. MICROPHONE is what lets the service hold the
     * microphone at all. Whether ONE service may be both — and whether the
     * assistant-role exemption reaches it — is part of what 3d asks.
     */
    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Penny 3d", NotificationManager.IMPORTANCE_LOW));

        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Penny 3d")
                .setContentText("boot chain: mic -> VM -> guest")
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
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

    /** The identical function penny3c_payload.c computes in the guest. */
    private static int fnv1a(byte[] p, int n) {
        int h = (int) 2166136261L;
        for (int i = 0; i < n; i++) {
            h ^= (p[i] & 0xff);
            h *= 16777619;
        }
        return h;
    }

    private static String hex32(int v) {
        return String.format("%08x", v);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.w(TAG, "==== rung 3d service onDestroy — the VM dies with this"
                + " process, sinceBoot=" + SystemClock.elapsedRealtime() + "ms");
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
            // AVmPayload_notifyPayloadReady() — and ours calls it only after
            // listen() has succeeded.
            Log.i(TAG, "CB onPayloadReady sinceBoot="
                    + SystemClock.elapsedRealtime() + "ms — our code is running"
                    + " in the guest and is listening on vsock " + PORT);
            new Thread(Penny3dService.this::runChain, "penny3d-chain").start();
        }

        @Override
        public void onPayloadFinished(VirtualMachine vm, int exitCode) {
            Log.i(TAG, "CB onPayloadFinished exitCode=" + exitCode);
            if (exitCode == 43) {
                Log.i(TAG, "condition 4 of 6: the guest exited 43 — it finished"
                        + " its exchanges and said so itself, independently of"
                        + " the host's verdict.");
            } else {
                Log.w(TAG, "guest exit " + exitCode + " is a failure path:"
                        + " 10 socket, 11 bind, 12 listen, 13 accept,"
                        + " 14/16 short read, 15 too big, 17/18 short write.");
            }
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

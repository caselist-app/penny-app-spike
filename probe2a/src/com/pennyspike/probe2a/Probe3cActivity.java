package com.pennyspike.probe2a;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
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
 * Rung 3c: can anything get INTO the guest, and can audio make the trip?
 *
 * WHAT IS ALREADY PROVEN, SEPARATELY, AND HAS NEVER BEEN JOINED.
 *   3b   the app holds a live microphone 9.5s after power-on, phone locked
 *   3    the app owns a VM and wakes it unattended
 *   2d   the app runs its own compiled code inside that VM
 * Nothing has ever carried a byte from the host INTO the guest. Everything
 * that has crossed went outwards and was one-way: console strings and an exit
 * code. This is the join.
 *
 * TWO QUESTIONS, ANSWERED IN THIS ORDER, AND THE ORDER IS THE METHOD.
 *
 *   3c-i   mode=echo. Sends a short ASCII string the guest cannot have
 *          guessed, and requires it back byte for byte. No audio anywhere
 *          near it. If the channel is shut this says so on its own terms —
 *          a microphone in the picture would only give a second candidate
 *          explanation for the same silence.
 *
 *   3c-ii  mode=audio. Records one real second from the microphone, sends
 *          the PCM, and compares the guest's FNV-1a hash against a hash
 *          computed here over the identical bytes. A hash, not a waveform:
 *          the question is whether the guest saw what the host recorded, and
 *          that is an equality, not a judgement call.
 *
 * This is the same shape 2d used and for the same reason: a control first,
 * with every variable but one nailed down, so that when the interesting run
 * fails there is only one place the fault can be.
 *
 * THE SILENCE TRAP APPLIES HERE TOO. Android hands back a buffer of zeros
 * rather than throwing when it suppresses background audio, and 32,000 zero
 * bytes hash perfectly well. A matching checksum over silence would look
 * exactly like success and mean nothing. So mode=audio measures peak, RMS and
 * the non-zero proportion BEFORE sending, and refuses to call a hash match a
 * yes if what it sent was digital silence.
 *
 * DELIBERATELY NOT TOUCHED: VmService. It holds the rung 3 wake result, proven
 * over four reboots, and 2d was kept in its own Activity for exactly this
 * reason. Same again. This activity owns a VM called penny3c; VmService's
 * penny3 is not involved and is not at risk.
 *
 * Run it unlocked, in the foreground, over adb:
 *   adb shell am force-stop com.pennyspike.probe2a
 *   adb shell am start -n com.pennyspike.probe2a/.Probe3cActivity
 *   adb shell am start -n com.pennyspike.probe2a/.Probe3cActivity --es mode audio
 */
public class Probe3cActivity extends Activity {

    static final String TAG = "PENNY3C";

    private static final String VM_NAME = "penny3c";

    /** Built into lib/arm64-v8a/ by build.sh from payload/penny3c_payload.c.
     *  Deliberately NOT the same file as 2d's PennyPayload.so — that one stays
     *  in the APK untouched so the 2d result remains reproducible. */
    private static final String PAYLOAD = "Penny3cPayload.so";

    /** Must match PORT in penny3c_payload.c, and must sit inside
     *  MIN_VSOCK_PORT..MAX_VSOCK_PORT (1024..4294967295, read off the device). */
    private static final long PORT = 5555L;

    private static final int SAMPLE_RATE = 16000;

    /** Held for the life of the process — the VM dies with the handle. */
    private VirtualMachine mVm;

    private String mMode = "echo";

    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        String m = getIntent() != null ? getIntent().getStringExtra("mode") : null;
        if (m != null && !m.isEmpty()) {
            mMode = m;
        }

        Log.i(TAG, "==== rung 3c: bytes into the guest ==== mode=" + mMode
                + " uid=" + Process.myUid()
                + " sinceBoot=" + SystemClock.elapsedRealtime() + "ms");

        mExecutor.execute(() -> {
            try {
                boot();
            } catch (Throwable t) {
                Log.e(TAG, "FAILED -> " + t.getClass().getName()
                        + ": " + t.getMessage(), t);
                Log.e(TAG, "VERDICT 3c: no VM came up, so the channel was never tested");
            }
        });
    }

    private void boot() throws Exception {
        // Device-protected storage, identical to VmService and to 2d. Not
        // needed while unlocked, but keeping the three the same means nothing
        // new is learned on the day this moves into the boot path.
        Context ctx = createDeviceProtectedStorageContext();

        Log.i(TAG, "STEP1 MANAGE_VIRTUAL_MACHINE = " + perm(
                "android.permission.MANAGE_VIRTUAL_MACHINE"));

        VirtualMachineManager vmm = ctx.getSystemService(VirtualMachineManager.class);
        Log.i(TAG, "STEP2 manager = " + vmm);
        if (vmm == null) {
            Log.e(TAG, "VERDICT 3c: no manager");
            return;
        }

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
        Log.i(TAG, "STEP8 waiting for onPayloadReady. The guest calls that only"
                + " AFTER it is listening on vsock " + PORT + ", so connecting"
                + " from here cannot race it.");
    }

    // ---------------------------------------------------------------- channel

    /**
     * One request/response over one vsock connection.
     *
     * Wire format, matching penny3c_payload.c:
     *   out   4 bytes big-endian length L, then L bytes
     *   in    4 bytes big-endian L, 4 bytes big-endian FNV-1a, then L bytes
     *
     * @return true if the guest's hash matched ours AND the echo came back
     *         byte for byte. Both are required: the hash alone would not
     *         catch a return leg that dropped data, and the echo alone would
     *         not catch the guest hashing something other than what it echoed.
     */
    private boolean exchange(String label, byte[] out) {
        long t0 = SystemClock.elapsedRealtime();
        ParcelFileDescriptor pfd = null;
        try {
            pfd = mVm.connectVsock(PORT);
            Log.i(TAG, "[" + label + "] connectVsock(" + PORT + ") -> " + pfd
                    + " after " + (SystemClock.elapsedRealtime() - t0) + "ms");

            // One fd, two streams. Deliberately NOT AutoCloseInputStream plus
            // AutoCloseOutputStream: those each own the descriptor, and
            // whichever closed first would take the other down with it.
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
                if (out[i] != back[i]) {
                    echoOk = false;
                    firstBad = i;
                    break;
                }
            }

            Log.i(TAG, "[" + label + "] hashMatch=" + hashOk
                    + " echoMatch=" + echoOk
                    + (echoOk ? "" : " firstMismatchAt=" + firstBad)
                    + " roundTrip=" + (SystemClock.elapsedRealtime() - t0) + "ms");
            return hashOk && echoOk;

        } catch (Throwable th) {
            // NoSuchMethodError here would mean connectVsock is blocklisted
            // after all and the dex flag was misread. Anything else is the
            // channel itself refusing. They are different answers, so the
            // exception class is logged rather than swallowed.
            Log.e(TAG, "[" + label + "] threw " + th.getClass().getName()
                    + ": " + th.getMessage(), th);
            return false;
        } finally {
            if (pfd != null) {
                try { pfd.close(); } catch (Throwable ignored) {}
            }
        }
    }

    /** Tells the guest to stop accepting and exit 43. */
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

    // ------------------------------------------------------------------ modes

    private void runEcho() {
        // A string the guest has no way to produce on its own. If this comes
        // back, it came back through the channel.
        String msg = "PENNY3C-HOST-TO-GUEST " + System.currentTimeMillis()
                + " the quick brown fox";
        byte[] out = msg.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Log.i(TAG, "3c-i sending: " + msg);

        boolean ok = exchange("3c-i echo", out);
        if (ok) {
            Log.i(TAG, "VERDICT 3c-i: YES — " + out.length + " bytes went from"
                    + " the host into the guest and came back identical."
                    + " The channel works. Corroborate with the PENNY3C lines"
                    + " on the guest console before believing this log alone.");
        } else {
            Log.e(TAG, "VERDICT 3c-i: NO — the round trip failed. Read the guest"
                    + " console: a socket/bind/listen failure there is the guest"
                    + " refusing, and no host-side change would fix it.");
        }
        sayGoodbye();
    }

    private void runAudio() {
        byte[] pcm = capture();
        if (pcm == null) {
            Log.e(TAG, "VERDICT 3c-ii: NOT TESTED — no usable audio to send."
                    + " That is a microphone result, not a channel result.");
            sayGoodbye();
            return;
        }

        // The control leg first, in the same VM, so that a failure on the
        // audio leg cannot be blamed on the channel having gone away.
        boolean control = exchange("3c-ii control",
                "PENNY3C-CONTROL".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Log.i(TAG, "3c-ii control leg ok=" + control);

        boolean ok = exchange("3c-ii audio", pcm);
        if (ok) {
            Log.i(TAG, "VERDICT 3c-ii: YES — " + pcm.length + " bytes of real"
                    + " captured PCM crossed into the guest and the guest's"
                    + " hash of what it received equals ours.");
        } else {
            Log.e(TAG, "VERDICT 3c-ii: NO — the audio did not survive the trip."
                    + " Compare the two fnv1a values above; equal hashes with a"
                    + " failed echo is a different fault from unequal hashes.");
        }
        sayGoodbye();
    }

    /**
     * One real second from the microphone, returned as little-endian 16-bit
     * PCM bytes — and refused if it is silence.
     *
     * Android 17 suppresses background audio without throwing: AudioRecord
     * initialises, reports RECORDSTATE_RECORDING and returns zeros. 32,000
     * zero bytes would hash identically at both ends and 3c-ii would look like
     * a pass while proving nothing at all. So the samples are judged here,
     * exactly as MicProbe judges them, and null is returned rather than let
     * that happen.
     */
    private byte[] capture() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "AUDIO: RECORD_AUDIO is not granted");
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
                Log.e(TAG, "AUDIO: AudioRecord failed to initialise");
                return null;
            }
            rec.startRecording();
            if (rec.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "AUDIO: refused to enter the recording state");
                return null;
            }

            short[] buf = new short[SAMPLE_RATE];        // one second
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
            Log.i(TAG, "AUDIO: samples=" + total + " peak=" + peak
                    + " rms=" + String.format("%.2f", rms)
                    + " nonZero=" + nonZero + " (" + String.format("%.1f", pct) + "%)");

            if (total == 0 || nonZero == 0 || peak < 4 || pct < 5.0) {
                Log.e(TAG, "AUDIO: this is silence or near-silence. Sending it"
                        + " would produce a matching checksum that means"
                        + " nothing — a hash over 32,000 zeros matches trivially."
                        + " Refusing, so 3c-ii cannot be misread as a pass.");
                return null;
            }

            byte[] pcm = new byte[total * 2];
            for (int i = 0; i < total; i++) {
                pcm[i * 2]     = (byte) (buf[i] & 0xff);
                pcm[i * 2 + 1] = (byte) ((buf[i] >> 8) & 0xff);
            }
            return pcm;

        } catch (Throwable th) {
            Log.e(TAG, "AUDIO: threw " + th.getClass().getName() + ": " + th.getMessage(), th);
            return null;
        } finally {
            if (rec != null) {
                try { rec.stop(); } catch (Throwable ignored) {}
                rec.release();
            }
        }
    }

    // ------------------------------------------------------------- plumbing

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

    private class Callbacks implements VirtualMachineCallback {

        @Override
        public void onPayloadStarted(VirtualMachine vm) {
            Log.i(TAG, "CB onPayloadStarted sinceBoot="
                    + SystemClock.elapsedRealtime() + "ms");
        }

        @Override
        public void onPayloadReady(VirtualMachine vm) {
            Log.i(TAG, "CB onPayloadReady sinceBoot="
                    + SystemClock.elapsedRealtime() + "ms — the guest is"
                    + " listening on vsock " + PORT + " by now");
            // Off the callback executor: the exchange blocks on a socket, and
            // onPayloadFinished has to be free to arrive while it does.
            new Thread(() -> {
                if ("audio".equals(mMode)) {
                    runAudio();
                } else {
                    runEcho();
                }
            }, "penny3c-exchange").start();
        }

        @Override
        public void onPayloadFinished(VirtualMachine vm, int exitCode) {
            Log.i(TAG, "CB onPayloadFinished exitCode=" + exitCode);
            if (exitCode == 43) {
                Log.i(TAG, "guest exited 43 — it completed its exchanges and"
                        + " said so itself, which is independent of the host's"
                        + " own verdict above.");
            } else {
                Log.w(TAG, "guest exit code " + exitCode + " is a failure path:"
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

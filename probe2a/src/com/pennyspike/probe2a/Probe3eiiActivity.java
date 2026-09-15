package com.pennyspike.probe2a;

import android.app.Activity;
import android.content.Context;
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
 * Rung 3e-ii: IS THE MEMORY REAL?
 *
 * 3e-i answered "will it be given" — 2048MB and 8 vCPUs, accepted freely,
 * booted, served. It did NOT answer whether the guest can reach any of it.
 * Linux hands out address space eagerly and pages lazily, so a config that
 * says 2048MB and a guest that has 2048MB are two different claims, and
 * 3e-i's own numbers said so out loud: the host surrendered roughly 660MB
 * of the 2048MB granted, because nothing in the guest had ever asked for
 * the rest. Until something does, "the VM has 2GB" is a line in a config
 * file. This makes something ask.
 *
 * WHAT THE GUEST DOES, and why it is not just a malloc. penny3eii_payload.c
 * mmaps 16MB at a time, fills every byte with the output of a PRNG, reads
 * every byte back and checks it, and keeps it mapped. Three things in that
 * sentence are load-bearing:
 *
 *   - mmap succeeding proves nothing on an overcommitting kernel, so every
 *     page is written to.
 *   - The fill is PSEUDO-RANDOM, not zeros or a repeated byte, because
 *     microdroid builds a zram swap device sized to the guest's whole RAM.
 *     zram is compressed swap living in that same RAM. Zero pages would
 *     compress away to nothing and the payload would reach 2GB having
 *     proved nothing whatever. Incompressible bytes close that escape.
 *   - Nothing is unmapped between chunks, because the question is how much
 *     is held at once.
 *
 * WHAT THIS SIDE ADDS. The guest's answer is one number; on its own it
 * cannot distinguish "the guest got 2GB" from "the guest got 2GB and the
 * phone paid for it". So the host samples its OWN /proc/meminfo every 500ms
 * for the whole touch and logs the curve. If Android's MemAvailable falls by
 * roughly what the guest claims to have taken, the memory moved and both
 * sides agree. If the guest claims 2GB and the host never gives up more than
 * 700MB, the memory did not move and one of the two is wrong — which is the
 * result 3e-ii exists to catch.
 *
 * THE EXPECTED FAILURE MODE IS DEATH, NOT AN ERROR. 3e-i established that
 * Android's low-memory killer is the real ceiling and that it reached our own
 * foreground TOP process at 4GB. Here the guest is deliberately made to
 * demand its memory, so either the guest's own OOM killer takes the payload,
 * or Android's takes the app and the VM with it. In both cases no reply
 * arrives and the last guest console line is the answer. That is why the
 * payload logs progress every 64MB rather than only at the end.
 *
 * PARAMETERISED, one build for the whole bisect: --ei mem is the VM size,
 * --ei touch is how many MB the guest is told to demand. Keeping them
 * separate is the point — asking a 2048MB VM to touch 1536MB is a different
 * question from asking a 1536MB VM to touch 1536MB.
 *
 * DELIBERATELY NOT TOUCHED: VmService (rung 3, six reboots), Penny3dService
 * (rung 3d, two reboots) and Probe3eActivity (rung 3e-i, committed). This
 * owns a VM called penny3eii and nothing else is at risk.
 *
 *   adb shell am force-stop com.pennyspike.probe2a
 *   adb shell am start -n com.pennyspike.probe2a/.Probe3eiiActivity --ei mem 256 --ei touch 128
 *   adb shell am start -n com.pennyspike.probe2a/.Probe3eiiActivity --ei mem 2048 --ei touch 1536
 */
public class Probe3eiiActivity extends Activity {

    static final String TAG = "PENNY3EII";

    private static final String VM_NAME = "penny3eii";

    /** This rung's own payload. NOT 3c's — 3c's payload hashes and echoes;
     *  it never allocates, which is the entire thing being measured here. */
    private static final String PAYLOAD = "Penny3eiiPayload.so";

    /** Must match PORT in penny3eii_payload.c. */
    private static final long PORT = 5555L;

    /** 3e-i measured onPayloadReady at +4.3s for a 2GB VM. 60s is slack. */
    private static final long READY_TIMEOUT_MS = 60_000L;

    /* There is deliberately NO timeout on the touch itself. Touching and
     * verifying 2GB is ~4GB of memory traffic and anything that goes through
     * zram is far slower than RAM, so any figure picked here would eventually
     * be wrong. Every way this can fail — the guest's OOM killer, Android's
     * low-memory killer, the VM stopping — closes the socket, so the blocking
     * read below returns rather than hanging. A timeout would only add a
     * third verdict that looked like the other two. */

    private VirtualMachine mVm;

    private int mMemMb = 256;
    private int mTouchMb = 128;

    private volatile boolean mReady = false;
    private volatile boolean mDone = false;
    private volatile boolean mSampling = false;

    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        if (getIntent() != null) {
            mMemMb = getIntent().getIntExtra("mem", 256);
            mTouchMb = getIntent().getIntExtra("touch", 128);
        }

        Log.i(TAG, "==== rung 3e-ii: is the memory real? ====");
        Log.i(TAG, "ASK vm=" + mMemMb + "MB touch=" + mTouchMb + "MB"
                + " uid=" + Process.myUid()
                + " sinceBoot=" + SystemClock.elapsedRealtime() + "ms");
        Log.i(TAG, "HOST before: " + hostMem());

        // Its own thread, not mExecutor — mExecutor is single-threaded and is
        // what setCallback() is handed, and a watchdog sleeping on it blocks
        // the callback it is waiting for. That cost a run on 3e-i.
        new Thread(this::watchdog, "penny3eii-watchdog").start();

        mExecutor.execute(() -> {
            try {
                boot();
            } catch (Throwable t) {
                Log.e(TAG, "FAILED at " + t.getClass().getName()
                        + ": " + t.getMessage(), t);
                mDone = true;
            }
        });
    }

    private void boot() throws Exception {
        Context ctx = createDeviceProtectedStorageContext();

        VirtualMachineManager vmm = ctx.getSystemService(VirtualMachineManager.class);
        if (vmm == null) {
            Log.e(TAG, "VERDICT 3e-ii: no manager, nothing was tested");
            return;
        }

        String apkPath = getApplicationInfo().sourceDir;
        Log.i(TAG, "STEP1 apk=" + apkPath + " bytes=" + new File(apkPath).length());

        long bytes = (long) mMemMb * 1024L * 1024L;
        VirtualMachineConfig config = new VirtualMachineConfig.Builder(ctx)
                .setApkPath(apkPath)
                .setPayloadBinaryName(PAYLOAD)
                .setDebugLevel(VirtualMachineConfig.DEBUG_LEVEL_FULL)
                .setProtectedVm(false)
                .setMemoryBytes(bytes)
                .setCpuTopology(VirtualMachineConfig.CPU_TOPOLOGY_MATCH_HOST)
                .build();
        Log.i(TAG, "STEP2 config accepted " + bytes + " bytes (" + mMemMb + "MB)");

        // Otherwise a VM created earlier at a different size is reused with its
        // OLD config and the bisect silently measures the same number.
        try {
            vmm.delete(VM_NAME);
            Log.i(TAG, "STEP3 deleted the previous " + VM_NAME);
        } catch (VirtualMachineException e) {
            Log.i(TAG, "STEP3 nothing to delete (" + e.getMessage() + ")");
        }

        mVm = vmm.create(VM_NAME, config);
        mVm.setCallback(mExecutor, new Callbacks());
        long t0 = SystemClock.elapsedRealtime();
        mVm.run();
        Log.i(TAG, "STEP4 run() returned in " + (SystemClock.elapsedRealtime() - t0)
                + "ms, status=" + status(mVm) + " — accepted, NOT yet a boot.");
    }

    private void watchdog() {
        long deadline = SystemClock.elapsedRealtime() + READY_TIMEOUT_MS;
        while (!mReady && !mDone && SystemClock.elapsedRealtime() < deadline) {
            try { Thread.sleep(250); } catch (InterruptedException ignored) {}
        }
        if (!mReady && !mDone) {
            Log.e(TAG, "VERDICT 3e-ii vm=" + mMemMb + " touch=" + mTouchMb
                    + ": the VM never became ready in " + (READY_TIMEOUT_MS / 1000)
                    + "s. That is a 3e-i failure, not a 3e-ii one. Check"
                    + " `logcat | grep \"has died\"` before calling it a hang.");
            Log.i(TAG, "HOST at failure: " + hostMem());
        }
    }

    /**
     * Android's own memory, sampled for the whole touch. This is the half the
     * guest cannot report: the guest only knows what it was able to take, not
     * what the phone had to give up to supply it.
     */
    private void sampleHost() {
        long t0 = SystemClock.elapsedRealtime();
        while (mSampling) {
            Log.i(TAG, "SAMPLE +" + (SystemClock.elapsedRealtime() - t0) + "ms "
                    + hostMem());
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        }
    }

    /**
     * Send the target, wait for the guest to finish taking it.
     *
     * The reply is one message and it arrives only on success, so a closed
     * socket here is not a protocol bug — it is the guest or the app having
     * been killed, and the guest console holds the last number reached.
     */
    private void demandMemory() {
        mSampling = true;
        new Thread(this::sampleHost, "penny3eii-sampler").start();

        long t0 = SystemClock.elapsedRealtime();
        ParcelFileDescriptor pfd = null;
        try {
            pfd = mVm.connectVsock(PORT);
            FileDescriptor fd = pfd.getFileDescriptor();
            OutputStream os = new FileOutputStream(fd);
            InputStream is = new FileInputStream(fd);

            byte[] req = new byte[8];
            putBe32(req, 0, 4);
            putBe32(req, 4, mTouchMb);
            os.write(req);
            os.flush();
            Log.i(TAG, "STEP5 told the guest to touch " + mTouchMb + "MB");

            byte[] rhdr = readFully(is, 4);
            if (rhdr == null) {
                killed(t0, "the guest closed the channel without replying");
                return;
            }
            int rlen = getBe32(rhdr, 0);
            byte[] body = readFully(is, rlen);
            if (body == null || rlen < 12) {
                killed(t0, "the reply was truncated (" + rlen + " bytes)");
                return;
            }

            int heldMb = getBe32(body, 0);
            int statusCode = getBe32(body, 4);
            int guestMs = getBe32(body, 8);
            long wall = SystemClock.elapsedRealtime() - t0;

            mSampling = false;
            mDone = true;

            Log.i(TAG, "GUEST held=" + heldMb + "MB of " + mTouchMb + "MB asked,"
                    + " status=" + statusCode + " (0 ok, 1 mmap refused,"
                    + " 2 read-back mismatch), guestTouchMs=" + guestMs
                    + ", hostWallMs=" + wall);
            Log.i(TAG, "HOST after: " + hostMem());

            if (statusCode == 0 && heldMb >= mTouchMb) {
                Log.i(TAG, "VERDICT 3e-ii vm=" + mMemMb + " touch=" + mTouchMb
                        + ": REAL — the guest wrote and read back every byte of "
                        + heldMb + "MB and still holds it. Compare the SAMPLE"
                        + " lines: the host must have given up a comparable"
                        + " amount, or the two sides disagree and this line is"
                        + " the one that is wrong.");
            } else if (statusCode == 2) {
                Log.e(TAG, "VERDICT 3e-ii vm=" + mMemMb + " touch=" + mTouchMb
                        + ": CORRUPT — the guest wrote " + heldMb + "MB and read"
                        + " back something different. Memory that does not"
                        + " survive a round trip is not memory.");
            } else {
                Log.w(TAG, "VERDICT 3e-ii vm=" + mMemMb + " touch=" + mTouchMb
                        + ": CEILING at " + heldMb + "MB — the guest was"
                        + " configured for " + mMemMb + "MB and could only reach "
                        + heldMb + "MB. That gap is the finding.");
            }
        } catch (Throwable th) {
            killed(t0, th.getClass().getName() + ": " + th.getMessage());
        } finally {
            mSampling = false;
            if (pfd != null) {
                try { pfd.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private void killed(long t0, String why) {
        mSampling = false;
        mDone = true;
        Log.e(TAG, "VERDICT 3e-ii vm=" + mMemMb + " touch=" + mTouchMb
                + ": NO REPLY after " + (SystemClock.elapsedRealtime() - t0)
                + "ms — " + why + ". This is the expected shape of a kill, not"
                + " a protocol fault. The last `PENNY3EII: held and verified`"
                + " line in the guest console is the number reached; check"
                + " `logcat | grep \"has died\"` for who did the killing.");
        Log.i(TAG, "HOST after: " + hostMem());
    }

    // ------------------------------------------------------------- plumbing

    private static String hostMem() {
        try (java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.FileReader("/proc/meminfo"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("MemTotal:") || line.startsWith("MemFree:")
                        || line.startsWith("MemAvailable:")
                        || line.startsWith("SwapFree:")) {
                    sb.append(line.replaceAll("\\s+", " ")).append("  ");
                }
            }
            return sb.toString().trim();
        } catch (Throwable t) {
            return "unreadable: " + t;
        }
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

    private class Callbacks implements VirtualMachineCallback {

        @Override
        public void onPayloadStarted(VirtualMachine vm) {
            Log.i(TAG, "CB onPayloadStarted sinceBoot="
                    + SystemClock.elapsedRealtime() + "ms");
        }

        @Override
        public void onPayloadReady(VirtualMachine vm) {
            mReady = true;
            Log.i(TAG, "CB onPayloadReady sinceBoot="
                    + SystemClock.elapsedRealtime() + "ms — guest up at "
                    + mMemMb + "MB");
            new Thread(Probe3eiiActivity.this::demandMemory, "penny3eii-demand").start();
        }

        @Override
        public void onPayloadFinished(VirtualMachine vm, int exitCode) {
            Log.i(TAG, "CB onPayloadFinished exitCode=" + exitCode
                    + (exitCode == 44 ? " (this rung's completion signal)" : ""));
        }

        @Override
        public void onError(VirtualMachine vm, int errorCode, String message) {
            Log.e(TAG, "CB onError code=" + errorCode + " message=" + message);
        }

        @Override
        public void onStopped(VirtualMachine vm, int reason) {
            Log.w(TAG, "CB onStopped reason=" + reason + " ready=" + mReady
                    + " — if this arrives mid-touch, the guest died taking"
                    + " memory, which is the ceiling.");
        }
    }
}

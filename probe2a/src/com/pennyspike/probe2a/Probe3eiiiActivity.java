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
 * Rung 3e-iii: WHERE WOULD A MODEL FILE LIVE?
 *
 * 3e-ii proved 1792MB of a 2048MB VM is genuinely writable — written, read
 * back and held, twice. That is the ceiling on everything the guest does. The
 * open question is whether a model FILE comes out of the same budget. Rung 3d
 * logged `init: Unknown /data fs type: tmpfs` on the way down, and tmpfs is a
 * RAM disk, so the fear is that a 1.5GB file costs 1.5GB of RAM before
 * anything has loaded it — against a measured ceiling of 1792MB. That does not
 * fit, and if it is true this rung closes the plan down rather than advancing
 * it.
 *
 * WHAT THIS DRIVES. penny3eiii_payload.c is a command server, not a
 * single-shot, because there is no C compiler on the Mac and every payload
 * rebuild costs a manual trip into the phone's Debian guest. One connection
 * carries a whole sequence of commands and the guest keeps its state between
 * them, which is what makes the real question askable: not "what does a file
 * cost" and not "what can be touched", but what is left AFTER a file exists.
 *
 * THE PLAN IS AN INTENT EXTRA, so the whole rung runs off one build and one
 * install. Steps are separated by ';' and each is cmd,mb,path :
 *
 *   1 MOUNTS     print /proc/mounts and /proc/meminfo in the guest
 *   2 LISTDIR    list a directory — finds where the APK is mounted
 *   3 FILEWRITE  write <mb> MB of incompressible bytes to <path>
 *   4 TOUCH      3e-ii's anonymous mmap-and-fill loop, <mb> MB, cumulative
 *   5 MMAPREAD   mmap <path> read-only and fault in every page
 *
 * THE MEASUREMENT IS A DIFFERENCE, NOT A NUMBER. Every reply carries the
 * guest's OWN MemFree either side of the command. If MemFree falls by the
 * megabytes written, the file is RAM. If it does not, there is real storage
 * down there. That is 3e-ii's accounting method, and it is the method that
 * caught zram absorbing nothing when it could have quietly absorbed
 * everything.
 *
 * THE ESCAPE HATCH IS STEP 5 AND IT IS TESTED EVEN IF 3 AND 4 LOOK BAD. The
 * APK is already mounted read-only inside the guest — that is how the payload
 * .so loads at all. A model packaged the same way (`zip -0`, `zipalign -p 4`)
 * could be mmaped out of that read-only mount as clean, reclaimable page cache
 * rather than copied into a RAM disk. Clean pages can be dropped under
 * pressure; anonymous pages cannot, and 3e-ii showed what a guest with nothing
 * left to reclaim does — it live-locks in zram and dies silently. So this is
 * not a few percent, it is whether that failure mode exists at all.
 *
 * DELIBERATELY NOT TOUCHED: VmService (rung 3, six reboots), Penny3dService
 * (rung 3d, two reboots), Probe3eActivity (3e-i) and Probe3eiiActivity
 * (3e-ii). This owns a VM called penny3eiii and nothing else is at risk.
 *
 *   adb shell am force-stop com.pennyspike.probe2a
 *   adb shell am start -n com.pennyspike.probe2a/.Probe3eiiiActivity \
 *       --ei mem 256 --es plan "1;2,0,/;2,0,/mnt/apk"
 */
public class Probe3eiiiActivity extends Activity {

    static final String TAG = "PENNY3EIII";

    private static final String VM_NAME = "penny3eiii";

    /** This rung's own payload. Not 3e-ii's — that one is single-shot and
     *  cannot be asked a second question, which is the whole difficulty. */
    private static final String PAYLOAD = "Penny3eiiiPayload.so";

    /** Must match PORT in penny3eiii_payload.c. */
    private static final long PORT = 5555L;

    /** 3e-i measured onPayloadReady at +4.3s for a 2GB VM. 60s is slack. */
    private static final long READY_TIMEOUT_MS = 60_000L;

    /* No timeout on a command, for 3e-ii's reason: writing or touching a
     * gigabyte through zram has no defensible upper bound, and every way this
     * can fail — the guest's OOM killer, Android's low-memory killer, the VM
     * stopping — closes the socket, so the blocking read returns anyway. A
     * timeout would only add a third verdict indistinguishable from the other
     * two. */

    private VirtualMachine mVm;

    private int mMemMb = 256;
    private int mStorageMb = 0;
    private boolean mKeep = false;
    private String mPlan = "1";

    private volatile boolean mReady = false;
    private volatile boolean mDone = false;
    private volatile boolean mSampling = false;

    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        if (getIntent() != null) {
            mMemMb = getIntent().getIntExtra("mem", 256);
            mStorageMb = getIntent().getIntExtra("storage", 0);
            mKeep = getIntent().getIntExtra("keep", 0) != 0;
            String p = getIntent().getStringExtra("plan");
            if (p != null && !p.isEmpty()) {
                mPlan = p;
            }
        }

        Log.i(TAG, "==== rung 3e-iii: where would a model file live? ====");
        Log.i(TAG, "ASK vm=" + mMemMb + "MB storage=" + mStorageMb + "MB"
                + " keep=" + mKeep
                + " plan=" + mPlan
                + " uid=" + Process.myUid()
                + " sinceBoot=" + SystemClock.elapsedRealtime() + "ms");
        Log.i(TAG, "HOST before: " + hostMem());

        // Its own thread, not mExecutor — mExecutor is what setCallback() is
        // handed, and a watchdog sleeping on it blocks the callback it waits
        // for. That cost a run on 3e-i.
        new Thread(this::watchdog, "penny3eiii-watchdog").start();

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
            Log.e(TAG, "VERDICT 3e-iii: no manager, nothing was tested");
            return;
        }

        String apkPath = getApplicationInfo().sourceDir;
        Log.i(TAG, "STEP1 apk=" + apkPath + " bytes=" + new File(apkPath).length());

        long bytes = (long) mMemMb * 1024L * 1024L;
        VirtualMachineConfig.Builder cb = new VirtualMachineConfig.Builder(ctx)
                .setApkPath(apkPath)
                .setPayloadBinaryName(PAYLOAD)
                .setDebugLevel(VirtualMachineConfig.DEBUG_LEVEL_FULL)
                .setProtectedVm(false)
                .setMemoryBytes(bytes)
                .setCpuTopology(VirtualMachineConfig.CPU_TOPOLOGY_MATCH_HOST);

        // ENCRYPTED STORAGE. The first probe run answered the rung's first
        // question bluntly: the payload has NO writable filesystem at all.
        // /data and /mnt are EACCES, /tmp does not exist, and a relative path
        // is EROFS because the working directory is the read-only erofs root.
        // So "is the guest's writable storage RAM" was the wrong question —
        // there was no writable storage to ask about.
        //
        // This is the thing that was missing, and it was found the way every
        // reachability question in this repo has been: by reading the flag off
        // the device's own dex before writing a line of code.
        // setEncryptedStorageBytes(J) is hiddenapi 0x0020 (SDK,TEST-API), the
        // same flag setApkPath and setMemoryBytes carry, so it is callable
        // from an app in the `app` domain. It asks for a real disk backed by
        // the host's UFS rather than by guest RAM — which, if it works, is
        // exactly where a model file should live.
        if (mStorageMb > 0) {
            long sbytes = (long) mStorageMb * 1024L * 1024L;
            cb = cb.setEncryptedStorageBytes(sbytes);
            Log.i(TAG, "STEP2a asked for " + sbytes + " bytes ("
                    + mStorageMb + "MB) of encrypted storage");
        }

        VirtualMachineConfig config = cb.build();
        Log.i(TAG, "STEP2 config accepted " + bytes + " bytes (" + mMemMb + "MB)"
                + " storage=" + mStorageMb + "MB");

        // Otherwise a VM created earlier at a different size is reused with its
        // OLD config and the run silently measures the previous one.
        //
        // --ei keep 1 suppresses that, and it is not a convenience: deleting
        // the VM destroys its encrypted storage with it, so every run so far
        // has measured a file written moments earlier into a warm page cache.
        // Whether a model SURVIVES a VM restart — and what it costs to read
        // one back cold, off the disk rather than out of cache — can only be
        // asked across two runs that share a store. The size must not change
        // between them or the old config is silently reused.
        if (mKeep) {
            Log.i(TAG, "STEP3 KEEPING any existing " + VM_NAME
                    + " and its encrypted storage — this run may be reading a"
                    + " file an earlier run wrote.");
        } else {
            try {
                vmm.delete(VM_NAME);
                Log.i(TAG, "STEP3 deleted the previous " + VM_NAME);
            } catch (VirtualMachineException e) {
                Log.i(TAG, "STEP3 nothing to delete (" + e.getMessage() + ")");
            }
        }

        mVm = mKeep ? vmm.getOrCreate(VM_NAME, config) : vmm.create(VM_NAME, config);
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
            Log.e(TAG, "VERDICT 3e-iii vm=" + mMemMb + ": the VM never became"
                    + " ready in " + (READY_TIMEOUT_MS / 1000) + "s. That is a"
                    + " 3e-i failure, not a 3e-iii one. Check"
                    + " `logcat | grep \"has died\"` before calling it a hang.");
            Log.i(TAG, "HOST at failure: " + hostMem());
        }
    }

    /** Android's own memory for the whole run. The guest can only report what
     *  it took; this is what the phone gave up to supply it. */
    private void sampleHost() {
        long t0 = SystemClock.elapsedRealtime();
        while (mSampling) {
            Log.i(TAG, "SAMPLE +" + (SystemClock.elapsedRealtime() - t0) + "ms "
                    + hostMem());
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        }
    }

    /**
     * Walk the plan, one command at a time, down one connection.
     *
     * Sequential and synchronous on purpose: the guest's state accumulates, so
     * the order the commands are issued in IS the experiment. Overlapping them
     * would make the MemFree differences unattributable.
     */
    private void runPlan() {
        mSampling = true;
        new Thread(this::sampleHost, "penny3eiii-sampler").start();

        long t0 = SystemClock.elapsedRealtime();
        ParcelFileDescriptor pfd = null;
        try {
            pfd = mVm.connectVsock(PORT);
            FileDescriptor fd = pfd.getFileDescriptor();
            OutputStream os = new FileOutputStream(fd);
            InputStream is = new FileInputStream(fd);

            String[] steps = mPlan.split(";");
            for (int n = 0; n < steps.length; n++) {
                String step = steps[n].trim();
                if (step.isEmpty()) {
                    continue;
                }
                // A step marked '?' is a PROBE: its failure is a result, not a
                // fault, and the plan carries on. That distinction earns its
                // keep here — finding which of six paths a payload may write
                // to is one run with six probes, or six runs at half a minute
                // each, and the guest has to be rebooted between them anyway.
                boolean optional = step.startsWith("?");
                if (optional) {
                    step = step.substring(1).trim();
                }
                String[] f = step.split(",", 3);
                int cmd = Integer.parseInt(f[0].trim());
                int mb = f.length > 1 && !f[1].trim().isEmpty()
                        ? Integer.parseInt(f[1].trim()) : 0;
                String path = f.length > 2 ? f[2] : "";

                byte[] pb = path.getBytes("UTF-8");
                byte[] req = new byte[4 + 8 + pb.length];
                putBe32(req, 0, 8 + pb.length);
                putBe32(req, 4, cmd);
                putBe32(req, 8, mb);
                System.arraycopy(pb, 0, req, 12, pb.length);

                long s0 = SystemClock.elapsedRealtime();
                Log.i(TAG, "SEND step " + (n + 1) + "/" + steps.length
                        + " cmd=" + cmd + " (" + cmdName(cmd) + ") mb=" + mb
                        + " path='" + path + "'");
                os.write(req);
                os.flush();

                byte[] rhdr = readFully(is, 4);
                if (rhdr == null) {
                    killed(t0, "the guest closed the channel during step "
                            + (n + 1) + " (" + cmdName(cmd) + ")");
                    return;
                }
                int rlen = getBe32(rhdr, 0);
                byte[] body = readFully(is, rlen);
                if (body == null || rlen < 24) {
                    killed(t0, "the reply to step " + (n + 1)
                            + " was truncated (" + rlen + " bytes)");
                    return;
                }

                int st       = getBe32(body, 0);
                long freeBef = getBe32(body, 4)  & 0xffffffffL;
                long freeAft = getBe32(body, 8)  & 0xffffffffL;
                int resultMb = getBe32(body, 12);
                int guestMs  = getBe32(body, 16);
                int extra    = getBe32(body, 20);

                long deltaKb = freeBef - freeAft;
                Log.i(TAG, "REPLY step " + (n + 1) + " cmd=" + cmd
                        + " status=" + st + " (" + statusName(st) + ")"
                        + " result=" + resultMb + "MB"
                        + " guestMemFree " + freeBef + " -> " + freeAft + " kB"
                        + " (delta " + deltaKb + " kB = " + (deltaKb / 1024)
                        + " MB)"
                        + " guestMs=" + guestMs + " extra=" + extra
                        + " hostWallMs=" + (SystemClock.elapsedRealtime() - s0));

                // The interpretation, stated here rather than left to be
                // worked out later from two numbers in a log.
                if (st == 0 && (cmd == 3 || cmd == 5) && resultMb > 0) {
                    long costMb = deltaKb / 1024;
                    long pct = (costMb * 100) / resultMb;
                    Log.i(TAG, "READING step " + (n + 1) + ": "
                            + resultMb + "MB of file cost " + costMb
                            + "MB of the guest's own free memory (" + pct
                            + "%). Near 100% means it IS RAM; near 0% means it"
                            + " is not, or the kernel reclaimed as it went.");
                }
                if (st != 0 && optional) {
                    Log.w(TAG, "PROBE step " + (n + 1) + " (" + cmdName(cmd)
                            + " '" + path + "') returned status " + st + " ("
                            + statusName(st) + ") errno=" + extra
                            + ". Marked optional, so the plan continues.");
                } else if (st != 0) {
                    Log.w(TAG, "STOPPING: step " + (n + 1) + " returned status "
                            + st + " (" + statusName(st) + ") extra=" + extra
                            + ". Later steps depend on it, so they are not run.");
                    break;
                }
            }

            // Goodbye: a zero-length frame, so the guest exits cleanly with 45
            // rather than looking like a channel that dropped.
            byte[] bye = new byte[4];
            putBe32(bye, 0, 0);
            os.write(bye);
            os.flush();

            mSampling = false;
            mDone = true;
            Log.i(TAG, "HOST after: " + hostMem());
            Log.i(TAG, "VERDICT 3e-iii vm=" + mMemMb + ": plan completed in "
                    + (SystemClock.elapsedRealtime() - t0) + "ms. Read the"
                    + " READING lines above for the answer; the guest console"
                    + " carries the same numbers independently.");
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
        Log.e(TAG, "VERDICT 3e-iii vm=" + mMemMb + ": NO REPLY after "
                + (SystemClock.elapsedRealtime() - t0) + "ms — " + why
                + ". This is the expected shape of a kill, not a protocol"
                + " fault. The last PENNY3EIII line in the guest console is how"
                + " far it got; check `logcat | grep \"has died\"` for who did"
                + " the killing, and remember 3e-ii's live-lock leaves no"
                + " `has died` line at all.");
        Log.i(TAG, "HOST after: " + hostMem());
    }

    // ------------------------------------------------------------- plumbing

    private static String cmdName(int c) {
        switch (c) {
            case 1: return "MOUNTS";
            case 2: return "LISTDIR";
            case 3: return "FILEWRITE";
            case 4: return "TOUCH";
            case 5: return "MMAPREAD";
            default: return "?";
        }
    }

    private static String statusName(int s) {
        switch (s) {
            case 0: return "ok";
            case 1: return "mmap refused";
            case 2: return "read-back mismatch";
            case 3: return "open failed";
            case 4: return "write failed";
            case 5: return "bad command";
            default: return "?";
        }
    }

    private static String hostMem() {
        try (java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.FileReader("/proc/meminfo"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("MemTotal:") || line.startsWith("MemFree:")
                        || line.startsWith("MemAvailable:")
                        || line.startsWith("Cached:")
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
            new Thread(Probe3eiiiActivity.this::runPlan, "penny3eiii-plan").start();
        }

        @Override
        public void onPayloadFinished(VirtualMachine vm, int exitCode) {
            Log.i(TAG, "CB onPayloadFinished exitCode=" + exitCode
                    + (exitCode == 45 ? " (this rung's completion signal)" : ""));
        }

        @Override
        public void onError(VirtualMachine vm, int errorCode, String message) {
            Log.e(TAG, "CB onError code=" + errorCode + " message=" + message);
        }

        @Override
        public void onStopped(VirtualMachine vm, int reason) {
            Log.w(TAG, "CB onStopped reason=" + reason + " ready=" + mReady
                    + " — if this arrives mid-plan, the guest died doing the"
                    + " step that was in flight.");
        }
    }
}

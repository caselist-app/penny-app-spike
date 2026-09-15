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
 * Rungs 3f and 3h, driven from one activity because they share one payload.
 *
 * 3f  IS THE GUEST CPU REAL? Eight vCPUs were granted in 3e-i and gigabytes
 *     were moved in 3e-ii and 3e-iii, and no payload in this repo has ever
 *     asked a processor to calculate anything. Single-core first, then a
 *     1/2/4/8 scaling curve.
 *
 * 3h  CAN A GIGABYTE BE PUSHED IN? vsock's record is 32,000 bytes in a single
 *     shot (3c). The encrypted store is keyed to the VM so the host cannot
 *     write it and the guest must, and a model has to get in somehow. Sizes go
 *     up in steps — 32KB, then 64MB, then 1536MB — so a failure has a
 *     bracketed cause rather than one data point.
 *
 * Plus the loose end 3e-iv left: it verified the stored file's SIZE and never
 * its CONTENT, so a store handing back 1.5GB of zeroes would have logged
 * identically. CMD_VERIFY reads the bytes and hashes them.
 *
 * NOTHING HERE CALLS A NEW PLATFORM METHOD. Every API this uses —
 * setApkPath, setPayloadBinaryName, setDebugLevel, setProtectedVm,
 * setMemoryBytes, setCpuTopology, setEncryptedStorageBytes, connectVsock,
 * create/getOrCreate/delete, setCallback, run — was read off the device's own
 * dex and exercised in an earlier rung. There is no hidden-API question open
 * in this build, which is why it went straight to code.
 *
 * THE PLAN IS AN INTENT EXTRA, the shape 3e-iii proved: steps separated by
 * ';', each step cmd,arg,arg2,path, a leading '?' marking a probe whose
 * failure does not stop the plan. One build and one install answer both rungs.
 *
 *   1 INFO         dump the guest's /proc/cpuinfo, /proc/mounts, /proc/meminfo
 *   2 CPU_INT      arg = millions of iterations, one thread
 *   3 CPU_FP       arg = millions of iterations, one thread
 *   4 THREADS_INT  arg = millions of iterations EACH, arg2 = thread count
 *   5 THREADS_FP   as above, floating point
 *   6 STREAM       arg = MB to push in, arg2 = plus this many KB,
 *                  path = where the guest writes them
 *   7 VERIFY       path = file to read back and checksum
 *
 * THE SCALING CURVE IS READ AS WORK PER MILLISECOND, NOT AS A SPEED-UP. Every
 * thread runs the same iteration count, so ideal hardware returns the same
 * wall time whatever the thread count. And this is a Pixel 6a — Tensor, 2
 * Cortex-X1 + 2 Cortex-A76 + 4 Cortex-A55 — so eight cores are three different
 * kinds of core and a perfect 8x is not on the table. Four or five times
 * aggregate is what working hardware looks like here; the per-thread spread
 * the payload logs is the heterogeneity made visible.
 *
 * THE CONTROL FOR 3f IS NOT IN THIS FILE and cannot be. It is the same C,
 * compiled by the same gcc with the same flags, run in the phone's Debian
 * guest: payload/penny3f_payload.c builds a static Debian binary under
 * -DPENNY_CONTROL from the identical source. Same silicon, same day. It is NOT
 * a bare-metal control — Debian is itself a guest in the Terminal app's VM —
 * and must never be written up as one. It is the number that would catch a
 * microdroid guest running at a tenth of expected speed.
 *
 * THE FAILURE 3h IS LOOKING FOR does not throw. If the guest buffered the
 * whole transfer in RAM it would hit 3e-ii's live-lock: kswapd allocating in
 * order to free, the VM hanging, no exception, no `has died` line and no
 * reply. The payload holds one chunk at a time and writes straight through, so
 * that cannot happen by accident, and both sides sample memory the whole way
 * so it would be visible if it did.
 *
 * DELIBERATELY NOT TOUCHED: VmService (rung 3, seven reboots), Penny3dService
 * (rung 3d, three), Probe3eActivity, Probe3eiiActivity and Probe3eiiiActivity.
 * This owns a VM called penny3f and a store of its own; nothing committed is
 * in the frame.
 *
 *   adb shell am force-stop com.pennyspike.probe2a
 *   adb shell am start -n com.pennyspike.probe2a/.Probe3fActivity \
 *       --ei mem 2048 --es plan "1;2,200,0,;3,200,0,"
 */
public class Probe3fActivity extends Activity {

    static final String TAG = "PENNY3F";

    private static final String VM_NAME = "penny3f";

    /** The fifth payload in this APK. The four before it are untouched. */
    private static final String PAYLOAD = "Penny3fPayload.so";

    /** Must match PORT in penny3f_payload.c. */
    private static final long PORT = 5555L;

    private static final long READY_TIMEOUT_MS = 60_000L;

    /** The guest refuses anything above WBUF in penny3f_payload.c, because
     *  holding more than one chunk is the thing it must not do. Below that the
     *  size is the host's to pick, and --ei chunkkb makes it a variable: the
     *  first 3h run pushed 1.5GB intact at 26 MB/s, and a throughput figure
     *  whose bottleneck is unattributed is only half an answer. */
    private static final int CHUNK_MAX = 1024 * 1024;

    private int mChunk = CHUNK_MAX;

    /* FNV-1a over 64-bit little-endian words. NOT 3c's byte-at-a-time hash and
     * its numbers must never be compared with 3c's or 3d's — see the payload's
     * header for why hashing a gigabyte a byte at a time would be charged
     * straight to 3h's throughput figure. */
    private static final long FNV_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

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
            int ckb = getIntent().getIntExtra("chunkkb", 1024);
            if (ckb > 0 && ckb * 1024 <= CHUNK_MAX) {
                mChunk = ckb * 1024;
            }
            String p = getIntent().getStringExtra("plan");
            if (p != null && !p.isEmpty()) {
                mPlan = p;
            }
        }

        Log.i(TAG, "==== rungs 3f (is the guest CPU real?) and 3h (can a"
                + " gigabyte be pushed in?) ====");
        Log.i(TAG, "ASK vm=" + mMemMb + "MB storage=" + mStorageMb + "MB"
                + " keep=" + mKeep
                + " chunk=" + mChunk + "B"
                + " plan=" + mPlan
                + " uid=" + Process.myUid()
                + " sinceBoot=" + SystemClock.elapsedRealtime() + "ms");
        Log.i(TAG, "HOST before: " + hostMem());

        // Its own thread, not mExecutor — mExecutor is what setCallback() is
        // handed, and a watchdog sleeping on it blocks the callback it waits
        // for. That cost a run on 3e-i.
        new Thread(this::watchdog, "penny3f-watchdog").start();

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
            Log.e(TAG, "VERDICT: no manager, nothing was tested");
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

        // 3h needs somewhere to put a gigabyte and a half. 3e-iii found the
        // only writable filesystem a microdroid guest has: one line of config
        // mounts a real ext4 on dm-crypt at /mnt/encryptedstore. Without it
        // there is nothing to write to at all — /data is a 128MB tmpfs the
        // payload cannot write to anyway, and everything else is EACCES or
        // read-only erofs.
        if (mStorageMb > 0) {
            long sbytes = (long) mStorageMb * 1024L * 1024L;
            cb = cb.setEncryptedStorageBytes(sbytes);
            Log.i(TAG, "STEP2a asked for " + sbytes + " bytes ("
                    + mStorageMb + "MB) of encrypted storage");
        }

        VirtualMachineConfig config = cb.build();
        Log.i(TAG, "STEP2 config accepted " + bytes + " bytes (" + mMemMb + "MB)"
                + " storage=" + mStorageMb + "MB");

        // getOrCreate reuses the VM's STORED config, so a run at a different
        // size silently measures the previous one — and the stored config
        // names an APK path that a reinstall invalidates permanently. Deleting
        // is the default here for that reason; --ei keep 1 is only for the
        // second half of a two-run experiment that must share a store.
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
            Log.e(TAG, "VERDICT vm=" + mMemMb + ": the VM never became ready in "
                    + (READY_TIMEOUT_MS / 1000) + "s. That is a 3e-i failure,"
                    + " not a 3f one. Check `logcat | grep \"has died\"` before"
                    + " calling it a hang.");
            Log.i(TAG, "HOST at failure: " + hostMem());
        }
    }

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
     * Sequential and synchronous on purpose: the guest keeps its state between
     * commands, so the order they are issued in IS the experiment.
     */
    private void runPlan() {
        mSampling = true;
        new Thread(this::sampleHost, "penny3f-sampler").start();

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
                boolean optional = step.startsWith("?");
                if (optional) {
                    step = step.substring(1).trim();
                }
                String[] f = step.split(",", 4);
                int cmd  = Integer.parseInt(f[0].trim());
                int arg  = num(f, 1);
                int arg2 = num(f, 2);
                String path = f.length > 3 ? f[3] : "";

                byte[] pb = path.getBytes("UTF-8");
                byte[] req = new byte[4 + 12 + pb.length];
                putBe32(req, 0, 12 + pb.length);
                putBe32(req, 4, cmd);
                putBe32(req, 8, arg);
                putBe32(req, 12, arg2);
                System.arraycopy(pb, 0, req, 16, pb.length);

                long s0 = SystemClock.elapsedRealtime();
                Log.i(TAG, "SEND step " + (n + 1) + "/" + steps.length
                        + " cmd=" + cmd + " (" + cmdName(cmd) + ") arg=" + arg
                        + " arg2=" + arg2 + " path='" + path + "'");
                os.write(req);
                os.flush();

                long sentCk = 0;
                long sentBytes = 0;
                long streamMs = 0;
                if (cmd == 6) {
                    long[] r = stream(os, arg, arg2);
                    sentCk = r[0];
                    sentBytes = r[1];
                    streamMs = r[2];
                }

                byte[] rhdr = readFully(is, 4);
                if (rhdr == null) {
                    killed(t0, "the guest closed the channel during step "
                            + (n + 1) + " (" + cmdName(cmd) + ")");
                    return;
                }
                int rlen = getBe32(rhdr, 0);
                byte[] body = readFully(is, rlen);
                if (body == null || rlen < 44) {
                    killed(t0, "the reply to step " + (n + 1)
                            + " was truncated (" + rlen + " bytes)");
                    return;
                }

                int st        = getBe32(body, 0);
                long freeBef  = getBe32(body, 4)  & 0xffffffffL;
                long freeAft  = getBe32(body, 8)  & 0xffffffffL;
                int resultMb  = getBe32(body, 12);
                int guestMs   = getBe32(body, 16);
                int extra     = getBe32(body, 20);
                long ck       = ((getBe32(body, 24) & 0xffffffffL) << 32)
                              | (getBe32(body, 28) & 0xffffffffL);
                long size     = ((getBe32(body, 32) & 0xffffffffL) << 32)
                              | (getBe32(body, 36) & 0xffffffffL);
                int aux       = getBe32(body, 40);

                Log.i(TAG, "REPLY step " + (n + 1) + " cmd=" + cmd
                        + " status=" + st + " (" + statusName(st) + ")"
                        + " guestMs=" + guestMs
                        + " result=" + resultMb
                        + " extra=" + extra
                        + " ck64=" + hex(ck)
                        + " size=" + size
                        + " aux=" + aux
                        + " guestMemFree " + freeBef + " -> " + freeAft + " kB"
                        + " hostWallMs=" + (SystemClock.elapsedRealtime() - s0));

                // The reading, written here rather than left to be worked out
                // later from two numbers in a log.
                if (st == 0 && (cmd == 2 || cmd == 3) && guestMs > 0) {
                    long iters = (long) aux * 1_000_000L;
                    Log.i(TAG, "READING 3f step " + (n + 1) + ": "
                            + cmdName(cmd) + " single thread, " + aux
                            + "M iterations in " + guestMs + " ms = "
                            + (iters / guestMs / 1000) + "M iterations/second."
                            + " Compare against the SAME binary run in the"
                            + " phone's Debian guest — and remember a single"
                            + " thread may have landed on a Cortex-A55.");
                }
                if (st == 0 && (cmd == 4 || cmd == 5) && guestMs > 0) {
                    long threads = extra;
                    long total = threads * (long) aux * 1_000_000L;
                    Log.i(TAG, "READING 3f step " + (n + 1) + ": " + threads
                            + " threads x " + aux + "M iterations, wall "
                            + guestMs + " ms = " + (total / guestMs / 1000)
                            + "M iterations/second aggregate. Per-thread"
                            + " fastest " + resultMb + " ms, slowest " + size
                            + " ms — that spread is the big.LITTLE split, not"
                            + " noise. 8 cores here are 2 X1 + 2 A76 + 4 A55,"
                            + " so 8x is not on the table.");
                }
                if (cmd == 6) {
                    boolean intact = (ck == sentCk) && (size == sentBytes);
                    Log.i(TAG, "READING 3h step " + (n + 1) + ": host sent "
                            + sentBytes + " bytes ck64=" + hex(sentCk)
                            + " in " + streamMs + " ms ("
                            + rate(sentBytes, streamMs) + " MB/s host wall);"
                            + " guest received " + size + " bytes ck64="
                            + hex(ck) + " in " + guestMs + " ms ("
                            + rate(size, guestMs) + " MB/s guest, fsync"
                            + " included). INTACT=" + intact);
                    if (!intact) {
                        Log.e(TAG, "READING 3h step " + (n + 1) + ": MISMATCH."
                                + " Different sizes mean truncation; same size"
                                + " and different checksums mean corruption."
                                + " Those are two different faults and this"
                                + " tells them apart.");
                    }
                }
                if (st == 0 && cmd == 7) {
                    Log.i(TAG, "READING step " + (n + 1) + ": read back "
                            + size + " bytes from the store, ck64=" + hex(ck)
                            + ", in " + guestMs + " ms ("
                            + rate(size, guestMs) + " MB/s)."
                            + (aux != 0 ? "  WARNING: stat and the bytes read"
                                    + " disagree — the file is short."
                                  : "  Compare this checksum against the one"
                                    + " the stream reported; equal means the"
                                    + " bytes on the disk are the bytes that"
                                    + " were sent, which 3e-iv could not say."));
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

            byte[] bye = new byte[4];
            putBe32(bye, 0, 0);
            os.write(bye);
            os.flush();

            mSampling = false;
            mDone = true;
            Log.i(TAG, "HOST after: " + hostMem());
            Log.i(TAG, "VERDICT vm=" + mMemMb + ": plan completed in "
                    + (SystemClock.elapsedRealtime() - t0) + "ms. Read the"
                    + " READING lines above; the guest console carries the"
                    + " same numbers independently.");
        } catch (Throwable th) {
            killed(t0, th.getClass().getName() + ": " + th.getMessage());
        } finally {
            mSampling = false;
            if (pfd != null) {
                try { pfd.close(); } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * RUNG 3h's push. Send mb megabytes PLUS kb kilobytes of incompressible
     * bytes as length-prefixed chunks of at most 1MB, then a zero-length chunk
     * to end it. The kilobyte term exists so the size ladder can start at
     * 3c's proven 32,000-byte figure rather than at a megabyte: a failure that
     * only appears above some size needs a rung below it to be believed.
     *
     * The bytes MUST NOT compress. microdroid gives every guest a zram swap
     * device sized to its whole RAM, so a stream of zeros could be absorbed
     * into a few megabytes and the run would report having pushed a gigabyte
     * having proved nothing — 3e-ii's trap, in a new place. Every chunk is
     * freshly generated from a running xorshift64* stream, so no two chunks
     * are alike either.
     *
     * Returns { checksum, bytes, wallMs }. The checksum is computed here, on
     * what was actually handed to the socket, so that a corruption anywhere
     * between this line and the guest's disk has something to disagree with.
     */
    private long[] stream(OutputStream os, int mb, int kb) throws Exception {
        long total = (long) mb * 1024L * 1024L + (long) kb * 1024L;
        byte[] buf = new byte[mChunk];
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(buf)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        byte[] lhdr = new byte[4];
        long h = FNV_BASIS;
        long sent = 0;
        long rng = 0x2545F4914F6CDD1DL;
        int chunks = 0;
        // Generation and the socket write are timed apart. 26 MB/s on the
        // first run is either this process failing to make bytes fast enough
        // or the channel failing to carry them, and those are different
        // findings — one is an artefact of the probe, the other is a fact
        // about vsock that rung 4 would inherit.
        long genNs = 0, wrNs = 0;

        long t0 = SystemClock.elapsedRealtime();
        while (sent < total) {
            int len = (int) Math.min((long) mChunk, total - sent);

            // ONE pass, not three. The first version of this shifted each
            // word out a byte at a time and then re-read the whole buffer to
            // hash it, and that cost 7977ms per 256MB — 32 MB/s, against
            // socket writes managing 154 MB/s on the same run. It made 3h's
            // headline figure a measurement of this loop rather than of the
            // channel. The word is hashed where it is generated and stored
            // with putLong, and the ByteBuffer is LITTLE_ENDIAN so the bytes
            // on the wire are exactly the bytes ck() and the guest's
            // ck_update() both read back.
            long g0 = System.nanoTime();
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
            genNs += System.nanoTime() - g0;

            long w0 = System.nanoTime();
            putBe32(lhdr, 0, len);
            os.write(lhdr);
            os.write(buf, 0, len);
            wrNs += System.nanoTime() - w0;

            sent += len;
            chunks++;

            if ((chunks % 256) == 0) {
                long ms = SystemClock.elapsedRealtime() - t0;
                Log.i(TAG, "PUSH " + (sent >> 20) + "/" + (total >> 20)
                        + " MB sent in " + ms + " ms (" + rate(sent, ms)
                        + " MB/s) " + hostMem());
            }
        }
        putBe32(lhdr, 0, 0);
        os.write(lhdr);
        os.flush();
        long ms = SystemClock.elapsedRealtime() - t0;
        Log.i(TAG, "PUSH done: " + sent + " bytes in " + ms + " ms ("
                + rate(sent, ms) + " MB/s host wall) using " + chunks
                + " chunks of " + mChunk + "B"
                + " — SPLIT: generating+hashing " + (genNs / 1000000L) + " ms ("
                + rate(sent, genNs / 1000000L) + " MB/s), socket writes "
                + (wrNs / 1000000L) + " ms (" + rate(sent, wrNs / 1000000L)
                + " MB/s). The larger of those two is the bottleneck.");
        return new long[] { h, sent, ms };
    }

    /**
     * The guest's ck_update, in Java. FNV-1a over 64-bit LITTLE-ENDIAN words,
     * with any trailing bytes folded in one at a time — the two implementations
     * have to agree byte for byte or the comparison means nothing, so this is
     * written to mirror the C rather than to be idiomatic.
     */
    private static long ck(long h, byte[] p, int n) {
        int i = 0;
        while (i + 8 <= n) {
            long w = (p[i] & 0xffL)
                   | ((p[i + 1] & 0xffL) << 8)
                   | ((p[i + 2] & 0xffL) << 16)
                   | ((p[i + 3] & 0xffL) << 24)
                   | ((p[i + 4] & 0xffL) << 32)
                   | ((p[i + 5] & 0xffL) << 40)
                   | ((p[i + 6] & 0xffL) << 48)
                   | ((p[i + 7] & 0xffL) << 56);
            h ^= w;
            h *= FNV_PRIME;
            i += 8;
        }
        while (i < n) {
            h ^= (p[i] & 0xffL);
            h *= FNV_PRIME;
            i++;
        }
        return h;
    }

    private void killed(long t0, String why) {
        mSampling = false;
        mDone = true;
        Log.e(TAG, "VERDICT vm=" + mMemMb + ": NO REPLY after "
                + (SystemClock.elapsedRealtime() - t0) + "ms — " + why
                + ". This is the expected shape of a kill, not a protocol"
                + " fault. The last PENNY3F line in the guest console is how"
                + " far it got; check `logcat | grep \"has died\"` for who did"
                + " the killing, and remember 3e-ii's live-lock leaves no"
                + " `has died` line at all.");
        Log.i(TAG, "HOST after: " + hostMem());
    }

    // ------------------------------------------------------------- plumbing

    private static int num(String[] f, int i) {
        if (f.length <= i) {
            return 0;
        }
        String s = f[i].trim();
        return s.isEmpty() ? 0 : Integer.parseInt(s);
    }

    private static String hex(long v) {
        return "0x" + String.format("%016x", v);
    }

    private static String rate(long bytes, long ms) {
        if (ms <= 0) {
            return "n/a";
        }
        return String.valueOf(bytes / 1024 / 1024 * 1000 / ms);
    }

    private static String cmdName(int c) {
        switch (c) {
            case 1: return "INFO";
            case 2: return "CPU_INT";
            case 3: return "CPU_FP";
            case 4: return "THREADS_INT";
            case 5: return "THREADS_FP";
            case 6: return "STREAM";
            case 7: return "VERIFY";
            default: return "?";
        }
    }

    private static String statusName(int s) {
        switch (s) {
            case 0: return "ok";
            case 1: return "refused (mmap or clone)";
            case 2: return "mismatch";
            case 3: return "open failed";
            case 4: return "write failed";
            case 5: return "bad command";
            case 6: return "short stream";
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
            new Thread(Probe3fActivity.this::runPlan, "penny3f-plan").start();
        }

        @Override
        public void onPayloadFinished(VirtualMachine vm, int exitCode) {
            Log.i(TAG, "CB onPayloadFinished exitCode=" + exitCode
                    + (exitCode == 46 ? " (this rung's completion signal)" : ""));
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

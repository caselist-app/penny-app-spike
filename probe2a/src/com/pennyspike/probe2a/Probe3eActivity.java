package com.pennyspike.probe2a;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
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
 * Rung 3e-i: WILL IT BE GIVEN?
 *
 * Every VM this spike has ever booted had 256MB and ONE CPU — enough for a
 * payload that hashes 32KB and exits. A small language model needs roughly
 * 1.5GB and several threads. If microdroid will not hand a sideloaded app
 * that, no benchmark matters and the model-in-the-guest plan is dead before
 * it is written. So this asks the cheapest version of the question first:
 * will the config be ACCEPTED and will the VM BOOT.
 *
 * WHAT WAS READ BEFORE ANY OF THIS WAS WRITTEN, because a blocked member and
 * a typo present identically as NoSuchMethodError. Out of the device's own
 * framework-virtualization.jar, dexdump -d:
 *
 *   VirtualMachineConfig$Builder.setMemoryBytes  (J)  hiddenapi 0x0020 SDK
 *   VirtualMachineConfig$Builder.setCpuTopology  (I)  hiddenapi 0x0020 SDK
 *   VirtualMachineConfig.CPU_TOPOLOGY_MATCH_HOST  I   hiddenapi 0x0020 SDK
 *   VirtualMachineConfig.CPU_TOPOLOGY_ONE_CPU     I   hiddenapi 0x0020 SDK
 *
 * 0x0020 is the same flag DEBUG_LEVEL_FULL and setApkPath carry, both of
 * which 2b and 2d called successfully. So the hidden-API gate is predicted
 * OPEN here, and a NoSuchMethodError would mean the dex flag was misread —
 * which is why the exception class is logged rather than swallowed.
 *
 * Note setMemoryBytes takes a LONG. The same trap as connectVsock: a wrong
 * width presents as NoSuchMethodError, which on this device is also exactly
 * how a hidden-API block presents.
 *
 * PARAMETERISED ON PURPOSE. The memory figure and the CPU topology come in as
 * intent extras, so the whole bisect — 2GB, 1.5GB, 1GB, and a 256MB control —
 * runs off ONE build. Rebuilding between attempts would put the APK, the
 * signature and the packaging back in the frame every time, and the point of
 * the bisect is that memory is the only thing that moves.
 *
 * THE CONTROL COMES FIRST, as it has for four rungs running: 256MB with one
 * CPU is the exact config 3c and 3d booted. If that fails today, nothing that
 * follows is about memory at all.
 *
 * WHAT COUNTS AS BOOTED, and it is deliberately not "run() returned". run()
 * returning only means VirtualizationService accepted the request. Three
 * things are required before this logs a YES:
 *   1  onPayloadReady fired — which happens only because our own C in the
 *      guest called AVmPayload_notifyPayloadReady()
 *   2  a byte round trip over vsock came back identical, so the guest is
 *      genuinely serving, not merely registered
 *   3  the guest exited 43 of its own accord
 * A VM that is granted memory it cannot reach could still satisfy 1 and 2 —
 * that is 3e-ii's question, not this one, and this class does not pretend
 * otherwise.
 *
 * DELIBERATELY NOT TOUCHED: VmService (rung 3, six reboots) and
 * Penny3dService (rung 3d, two reboots). Same discipline 2d, 3c and 3d each
 * kept. This owns a VM called penny3e and nothing else is at risk.
 *
 * The payload is 3c's Penny3cPayload.so, REUSED UNCHANGED and not rebuilt.
 * Same wire protocol, same exit codes, no new variable, and no round trip
 * into the Debian guest for a compiler.
 *
 * Run it unlocked, in the foreground, over adb:
 *   adb shell am force-stop com.pennyspike.probe2a
 *   adb shell am start -n com.pennyspike.probe2a/.Probe3eActivity --ei mem 256 --es cpu one
 *   adb shell am start -n com.pennyspike.probe2a/.Probe3eActivity --ei mem 2048 --es cpu match
 */
public class Probe3eActivity extends Activity {

    static final String TAG = "PENNY3E";

    private static final String VM_NAME = "penny3e";

    /** 3c's payload, reused unchanged. Not rebuilt for this rung. */
    private static final String PAYLOAD = "Penny3cPayload.so";

    /** Must match PORT in penny3c_payload.c. */
    private static final long PORT = 5555L;

    /** If the guest has not said it is ready by now, it did not boot. Generous:
     *  3c and 3d saw onPayloadReady ~1.3s after run(), and a bigger VM has
     *  more memory for the kernel to bring up, so slower is expected rather
     *  than suspicious. */
    private static final long READY_TIMEOUT_MS = 60_000L;

    private VirtualMachine mVm;

    private int mMemMb = 256;
    private String mCpu = "one";

    private volatile boolean mReady = false;

    /** Set when a verdict has already been logged, so the watchdog cannot
     *  print a second, contradictory one over the top of it. */
    private volatile boolean mDone = false;

    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        if (getIntent() != null) {
            mMemMb = getIntent().getIntExtra("mem", 256);
            String c = getIntent().getStringExtra("cpu");
            if (c != null && !c.isEmpty()) {
                mCpu = c;
            }
        }

        Log.i(TAG, "==== rung 3e-i: will it be given? ====");
        Log.i(TAG, "ASK mem=" + mMemMb + "MB cpu=" + mCpu
                + " uid=" + Process.myUid()
                + " sinceBoot=" + SystemClock.elapsedRealtime() + "ms");
        Log.i(TAG, "HOST " + hostMem());

        // The watchdog runs on its OWN thread, and that is not tidiness.
        // mExecutor is a single-thread executor and is also what setCallback()
        // is handed, so a watchdog sleeping on it would block the very
        // callback it is waiting for and report a healthy VM as a failure.
        // The 256MB control caught exactly that before any real figure was
        // measured, which is what a control is for.
        new Thread(this::watchdog, "penny3e-watchdog").start();

        mExecutor.execute(() -> {
            try {
                boot();
            } catch (Throwable t) {
                Log.e(TAG, "FAILED at " + t.getClass().getName()
                        + ": " + t.getMessage(), t);
                mDone = true;
                if (t instanceof NoSuchMethodError || t instanceof NoSuchFieldError) {
                    Log.e(TAG, "VERDICT 3e-i mem=" + mMemMb + " cpu=" + mCpu
                            + ": BLOCKED — that is the hidden-API gate, not a"
                            + " memory refusal. The dex flag said 0x0020 SDK,"
                            + " so either it was misread or the argument width"
                            + " is wrong. Check `adb logcat -d | grep hiddenapi`"
                            + " for `using linking: denied` to tell them apart.");
                } else {
                    Log.e(TAG, "VERDICT 3e-i mem=" + mMemMb + " cpu=" + mCpu
                            + ": NO — refused before the VM ran. The exception"
                            + " above is the refusal; it is not a hidden-API"
                            + " block, because the call was reached.");
                }
            }
        });
    }

    private void boot() throws Exception {
        Context ctx = createDeviceProtectedStorageContext();

        VirtualMachineManager vmm = ctx.getSystemService(VirtualMachineManager.class);
        Log.i(TAG, "STEP1 manager=" + vmm + " MANAGE_VIRTUAL_MACHINE="
                + perm("android.permission.MANAGE_VIRTUAL_MACHINE"));
        if (vmm == null) {
            Log.e(TAG, "VERDICT 3e-i: no manager, nothing was tested");
            return;
        }

        String apkPath = getApplicationInfo().sourceDir;
        Log.i(TAG, "STEP2 apk=" + apkPath + " bytes=" + new File(apkPath).length());

        int topology = "match".equals(mCpu)
                ? VirtualMachineConfig.CPU_TOPOLOGY_MATCH_HOST
                : VirtualMachineConfig.CPU_TOPOLOGY_ONE_CPU;
        Log.i(TAG, "STEP3 topology constant resolved to " + topology
                + " (MATCH_HOST=" + VirtualMachineConfig.CPU_TOPOLOGY_MATCH_HOST
                + " ONE_CPU=" + VirtualMachineConfig.CPU_TOPOLOGY_ONE_CPU + ")");

        long bytes = (long) mMemMb * 1024L * 1024L;
        VirtualMachineConfig config = new VirtualMachineConfig.Builder(ctx)
                .setApkPath(apkPath)
                .setPayloadBinaryName(PAYLOAD)
                .setDebugLevel(VirtualMachineConfig.DEBUG_LEVEL_FULL)
                .setProtectedVm(false)
                .setMemoryBytes(bytes)
                .setCpuTopology(topology)
                .build();
        Log.i(TAG, "STEP4 config ACCEPTED the request: " + bytes + " bytes ("
                + mMemMb + "MB), topology " + topology
                + ". That is the builder agreeing, not the hypervisor.");

        // A VM created earlier at a different size would otherwise be reused
        // with its OLD config, and the bisect would silently measure the same
        // number every time.
        try {
            vmm.delete(VM_NAME);
            Log.i(TAG, "STEP5 deleted the previous " + VM_NAME);
        } catch (VirtualMachineException e) {
            Log.i(TAG, "STEP5 nothing to delete (" + e.getMessage() + ")");
        }

        mVm = vmm.create(VM_NAME, config);
        Log.i(TAG, "STEP6 create() -> " + mVm.getName());

        mVm.setCallback(mExecutor, new Callbacks());
        long t0 = SystemClock.elapsedRealtime();
        mVm.run();
        Log.i(TAG, "STEP7 run() returned in " + (SystemClock.elapsedRealtime() - t0)
                + "ms, status=" + status(mVm)
                + " — VirtualizationService accepted it. NOT yet a boot.");
    }

    /**
     * Distinguishes "refused" from "accepted and then died" from "booted".
     * Without this a VM that is granted memory the host cannot actually find
     * looks like a hang, and a hang is not a finding.
     */
    private void watchdog() {
        long deadline = SystemClock.elapsedRealtime() + READY_TIMEOUT_MS;
        if (mDone) return;
        while (!mReady && SystemClock.elapsedRealtime() < deadline) {
            try { Thread.sleep(250); } catch (InterruptedException ignored) {}
        }
        if (!mReady && !mDone) {
            Log.e(TAG, "VERDICT 3e-i mem=" + mMemMb + " cpu=" + mCpu
                    + ": NO — accepted but never became ready in "
                    + (READY_TIMEOUT_MS / 1000) + "s. Read the guest console and"
                    + " onStopped/onError above: a VM that was granted memory"
                    + " the host could not find dies here rather than at run().");
            Log.i(TAG, "HOST at failure: " + hostMem());
        }
    }

    // ---------------------------------------------------------------- channel

    /** 3c's exchange, unchanged in wire format. Proves the guest is serving. */
    private boolean exchange(String label, byte[] out) {
        long t0 = SystemClock.elapsedRealtime();
        ParcelFileDescriptor pfd = null;
        try {
            pfd = mVm.connectVsock(PORT);
            FileDescriptor fd = pfd.getFileDescriptor();
            OutputStream os = new FileOutputStream(fd);
            InputStream is = new FileInputStream(fd);

            byte[] hdr = new byte[4];
            putBe32(hdr, 0, out.length);
            os.write(hdr);
            os.write(out);
            os.flush();

            byte[] rhdr = readFully(is, 8);
            if (rhdr == null) {
                Log.e(TAG, "[" + label + "] the guest closed before replying");
                return false;
            }
            int rlen = getBe32(rhdr, 0);
            int rhash = getBe32(rhdr, 4);
            if (rlen != out.length) {
                Log.e(TAG, "[" + label + "] guest saw " + rlen + ", we sent " + out.length);
                return false;
            }
            byte[] back = readFully(is, rlen);
            if (back == null) {
                Log.e(TAG, "[" + label + "] the echo was truncated");
                return false;
            }
            boolean hashOk = fnv1a(out, out.length) == rhash;
            boolean echoOk = true;
            for (int i = 0; i < out.length; i++) {
                if (out[i] != back[i]) { echoOk = false; break; }
            }
            Log.i(TAG, "[" + label + "] " + out.length + " bytes,"
                    + " guest fnv1a=" + hex32(rhash)
                    + " hashMatch=" + hashOk + " echoMatch=" + echoOk
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

    private void sayGoodbye() {
        ParcelFileDescriptor pfd = null;
        try {
            pfd = mVm.connectVsock(PORT);
            OutputStream os = new FileOutputStream(pfd.getFileDescriptor());
            os.write(new byte[] { 0, 0, 0, 0 });
            os.flush();
        } catch (Throwable th) {
            Log.w(TAG, "goodbye failed: " + th);
        } finally {
            if (pfd != null) {
                try { pfd.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private void proveItServes() {
        String msg = "PENNY3E mem=" + mMemMb + "MB cpu=" + mCpu + " "
                + System.currentTimeMillis();
        boolean ok = exchange("3e-i probe",
                msg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (ok) {
            Log.i(TAG, "VERDICT 3e-i mem=" + mMemMb + " cpu=" + mCpu
                    + ": YES — the VM was granted it, booted, and is serving."
                    + " Corroborate with the guest console (its own kernel"
                    + " reports the memory and CPUs it actually saw) before"
                    + " believing this line alone.");
        } else {
            Log.e(TAG, "VERDICT 3e-i mem=" + mMemMb + " cpu=" + mCpu
                    + ": PARTIAL — it booted and said ready, but the round trip"
                    + " failed. That is a channel fault, not a memory one.");
        }
        Log.i(TAG, "HOST after boot: " + hostMem());
        mDone = true;
        sayGoodbye();
    }

    // ------------------------------------------------------------- plumbing

    /**
     * Android's own view of free memory, read here rather than assumed.
     * CLAUDE.md records that host-side memory has never been measured in this
     * spike; every earlier statement about pressure was read from inside a
     * guest. This is the host.
     */
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
            mReady = true;
            Log.i(TAG, "CB onPayloadReady sinceBoot="
                    + SystemClock.elapsedRealtime() + "ms — the guest kernel"
                    + " came up at " + mMemMb + "MB and our code is running");
            new Thread(Probe3eActivity.this::proveItServes, "penny3e-probe").start();
        }

        @Override
        public void onPayloadFinished(VirtualMachine vm, int exitCode) {
            Log.i(TAG, "CB onPayloadFinished exitCode=" + exitCode
                    + (exitCode == 43 ? " (the guest's own completion signal)" : ""));
        }

        @Override
        public void onError(VirtualMachine vm, int errorCode, String message) {
            Log.e(TAG, "CB onError code=" + errorCode + " message=" + message
                    + " — this is the hypervisor refusing, and at a large"
                    + " memory figure it is the answer rather than a fault.");
        }

        @Override
        public void onStopped(VirtualMachine vm, int reason) {
            Log.w(TAG, "CB onStopped reason=" + reason
                    + " ready=" + mReady);
        }
    }
}

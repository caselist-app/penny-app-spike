package com.pennyspike.probe2a;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;
import android.os.UserManager;
import android.util.Log;

/**
 * Rung 3b: the measurement itself, shared by the two places that attempt it.
 *
 * WHY THIS IS NOT A TRY/CATCH AROUND AudioRecord.
 *
 * Android 17 does not refuse background microphone access by throwing. It
 * hands the caller a perfectly healthy AudioRecord, reports RECORDSTATE_RECORDING,
 * returns a full buffer from read() — and every sample in that buffer is zero.
 * The documented wording is that access is suppressed "silently without
 * throwing an exception". A probe that only watches for an exception therefore
 * reports a confident YES when the answer is NO, which is the single worst
 * outcome available to this rung.
 *
 * So the verdict is decided on the SAMPLES, not on the absence of an error:
 * peak amplitude, RMS, and how many of the samples were non-zero. Digital
 * silence is exactly zero on every sample. A real microphone in a quiet room
 * is not — it has a noise floor, and that noise floor is the signal we are
 * looking for. A handful of stray non-zero samples is not enough either, hence
 * the ratio rather than a boolean.
 *
 * The one ambiguity this cannot resolve on its own is a genuinely dead
 * microphone versus a suppressed one. That is what the control run is for:
 * the same probe invoked by hand, unlocked, from an activity, where access is
 * unquestionably allowed. If the control is silent too, the probe is broken
 * and no conclusion about boot-time access can be drawn from it.
 */
final class MicProbe {

    static final String TAG = "PENNY3B";

    private static final int SAMPLE_RATE = 16000;
    private static final int MILLIS = 1000;

    private MicProbe() {}

    /**
     * Records for one second and logs a verdict.
     *
     * @param where which of the two attempt sites is calling — the whole point
     *              of rung 3b is that these two can legitimately disagree.
     */
    static void run(Context ctx, String where) {
        long t = SystemClock.elapsedRealtime();

        // Recorded first, because every possible outcome below is unreadable
        // without them. "Silence" means nothing if the permission was not held
        // in the first place, and "audio" means nothing if a human had already
        // unlocked the phone.
        boolean granted = ctx.checkSelfPermission(
                android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        UserManager um = ctx.getSystemService(UserManager.class);
        boolean unlocked = um != null && um.isUserUnlocked();

        Log.i(TAG, "MIC [" + where + "] attempt"
                + " sinceBoot=" + t + "ms"
                + " RECORD_AUDIO=" + (granted ? "GRANTED" : "DENIED")
                + " userUnlocked=" + unlocked);

        // A runtime permission is stored in device-encrypted storage, so it is
        // readable before first unlock — but that is an assumption worth
        // failing loudly on rather than quietly around.
        if (!granted) {
            Log.e(TAG, "MIC [" + where + "] VERDICT: NOT TESTED —"
                    + " RECORD_AUDIO is not held, so silence would prove nothing."
                    + " Grant it and run again.");
            return;
        }

        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuf <= 0) {
            Log.e(TAG, "MIC [" + where + "] VERDICT: NOT TESTED —"
                    + " getMinBufferSize returned " + minBuf
                    + " (no usable capture path at all)");
            return;
        }

        int wanted = SAMPLE_RATE * 2 * MILLIS / 1000;   // 16-bit mono
        int bufBytes = Math.max(minBuf * 2, wanted);
        AudioRecord rec = null;

        try {
            rec = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    bufBytes);

            if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "MIC [" + where + "] VERDICT: NO —"
                        + " AudioRecord failed to initialise (state="
                        + rec.getState() + "). This IS a hard refusal rather"
                        + " than suppression.");
                return;
            }

            rec.startRecording();
            int rs = rec.getRecordingState();
            Log.i(TAG, "MIC [" + where + "] startRecording -> recordingState="
                    + rs + (rs == AudioRecord.RECORDSTATE_RECORDING
                        ? " (RECORDING)" : " (NOT recording)"));

            if (rs != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "MIC [" + where + "] VERDICT: NO —"
                        + " the device refused to enter the recording state.");
                return;
            }

            short[] buf = new short[SAMPLE_RATE * MILLIS / 1000];
            int total = 0;
            int peak = 0;
            long sumSquares = 0;
            int nonZero = 0;
            long deadline = SystemClock.elapsedRealtime() + MILLIS + 1500;

            while (total < buf.length && SystemClock.elapsedRealtime() < deadline) {
                int n = rec.read(buf, total, buf.length - total);
                if (n <= 0) {
                    Log.w(TAG, "MIC [" + where + "] read returned " + n
                            + " after " + total + " samples");
                    break;
                }
                for (int i = total; i < total + n; i++) {
                    int v = Math.abs(buf[i]);
                    if (v != 0) nonZero++;
                    if (v > peak) peak = v;
                    sumSquares += (long) buf[i] * buf[i];
                }
                total += n;
            }

            double rms = total > 0 ? Math.sqrt((double) sumSquares / total) : 0.0;
            double pct = total > 0 ? (100.0 * nonZero / total) : 0.0;

            Log.i(TAG, "MIC [" + where + "] samples=" + total
                    + " peak=" + peak
                    + " rms=" + String.format("%.2f", rms)
                    + " nonZero=" + nonZero
                    + " (" + String.format("%.1f", pct) + "%)"
                    + " sinceBoot=" + SystemClock.elapsedRealtime() + "ms");

            // The threshold is deliberately generous. A quiet room still has a
            // noise floor of a few counts; true suppression is exactly zero on
            // every one of 16,000 samples. Anything in between is interesting
            // and is reported as such rather than forced into a yes or a no.
            if (total == 0) {
                Log.e(TAG, "MIC [" + where + "] VERDICT: NO — no samples read at all");
            } else if (nonZero == 0) {
                Log.e(TAG, "MIC [" + where + "] VERDICT: NO — DIGITAL SILENCE."
                        + " Every sample was zero and nothing threw. This is the"
                        + " documented signature of suppressed background audio,"
                        + " not of a broken microphone.");
            } else if (pct < 5.0 || peak < 4) {
                Log.w(TAG, "MIC [" + where + "] VERDICT: AMBIGUOUS — almost"
                        + " silent (peak=" + peak + ", " + String.format("%.1f", pct)
                        + "% non-zero). Do not call this a yes. Re-run with"
                        + " deliberate noise near the phone.");
            } else {
                Log.i(TAG, "MIC [" + where + "] VERDICT: YES — REAL AUDIO."
                        + " peak=" + peak + " rms=" + String.format("%.2f", rms)
                        + ". Corroborate with appops/dumpsys from the host"
                        + " before believing this log alone.");
            }

        } catch (Throwable th) {
            Log.e(TAG, "MIC [" + where + "] VERDICT: NO — threw "
                    + th.getClass().getName() + ": " + th.getMessage(), th);
        } finally {
            if (rec != null) {
                try { rec.stop(); } catch (Throwable ignored) {}
                rec.release();
            }
        }
    }

    /** Runs the probe off the calling thread. Binder and service callbacks
     *  must not block for a second. */
    static void runAsync(final Context ctx, final String where) {
        new Thread(() -> run(ctx, where), "micprobe-" + where).start();
    }
}

package com.pennyspike.probe2a;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

/**
 * The control run, and rung 3b is worthless without it.
 *
 * Both real attempt sites can report digital silence for two completely
 * different reasons: Android suppressed the audio, or the probe itself is
 * broken / the microphone is dead / the emulator has no input. Those are
 * indistinguishable from the samples alone, and one of them would produce a
 * false NO that closes the question wrongly.
 *
 * This activity runs the identical MicProbe from the foreground, with the
 * phone unlocked and a human present — the one situation where microphone
 * access is unambiguously permitted. If THIS is silent, nothing else measured
 * today means anything and the probe must be fixed first.
 *
 * Started by hand over adb. No launcher icon.
 */
public class MicControlActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(MicProbe.TAG, "==== MicControlActivity: CONTROL RUN,"
                + " foreground, unlocked, access should be allowed ====");
        MicProbe.runAsync(this, "CONTROL-foreground");
    }
}

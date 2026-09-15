package com.pennyspike.probe2a;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

/**
 * Rung 3, first half: the thing the OS talks to when it finishes booting.
 *
 * TWO broadcasts are listened for, and which one arrives is a finding in its
 * own right:
 *
 *   LOCKED_BOOT_COMPLETED — fires as soon as the OS is up, BEFORE anyone has
 *       entered the PIN. Only components marked directBootAware receive it,
 *       and at that moment the app's normal (credential-encrypted) data
 *       directory does not exist yet.
 *   BOOT_COMPLETED — fires only after the FIRST unlock on a device with a
 *       screen lock. On a phone in a drawer with nobody to type a PIN, this
 *       may never arrive at all.
 *
 * That distinction is the whole commercial question behind rung 3. An
 * appliance that needs a human to unlock it once per power cut is not headless.
 *
 * This receiver deliberately does no work beyond starting the service. A
 * receiver's process can be killed the moment onReceive returns, so anything
 * started here has to be something that keeps itself alive.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "PENNY3";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = (intent == null) ? "null" : intent.getAction();
        Log.i(TAG, "==== BootReceiver: " + action
                + " sinceBoot=" + SystemClock.elapsedRealtime() + "ms ====");

        Intent start = new Intent(context, VmService.class);
        start.putExtra("why", action);

        try {
            context.startForegroundService(start);
            Log.i(TAG, "startForegroundService accepted for " + action);
        } catch (Throwable t) {
            // The Android 15 rule that blocks some service types from starting
            // at boot surfaces HERE, as ForegroundServiceStartNotAllowedException.
            Log.e(TAG, "startForegroundService REFUSED -> "
                    + t.getClass().getName() + ": " + t.getMessage(), t);
        }

        // Rung 3b, attempt site B. Started in its own right rather than from
        // inside VmService, so that a refusal here cannot take the proven rung
        // 3 wake path down with it. The two are independent on purpose: rung 3
        // must keep reproducing while rung 3b is being answered.
        Intent mic = new Intent(context, MicFgsService.class);
        mic.putExtra("why", action);
        try {
            context.startForegroundService(mic);
            Log.i(MicProbe.TAG, "startForegroundService(MicFgsService) accepted for " + action);
        } catch (Throwable t) {
            // A microphone-typed foreground service is one of the types the
            // background-start rule may refuse outright. If that is what
            // happens, site B never gets to ask the microphone question at all
            // — which is itself the finding, and is why it is logged as one.
            Log.e(MicProbe.TAG, "startForegroundService(MicFgsService) REFUSED -> "
                    + t.getClass().getName() + ": " + t.getMessage(), t);
            Log.e(MicProbe.TAG, "MIC [B-fgs] VERDICT: NOT TESTED — refused at"
                    + " start, before the microphone was ever requested.");
        }
    }
}

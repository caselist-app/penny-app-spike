package com.pennyspike.probe2a;

import android.os.SystemClock;
import android.service.voice.VoiceInteractionService;
import android.util.Log;

/**
 * Rung 3b, attempt site A: the assistant itself.
 *
 * This class is the whole hypothesis in one place. AOSP's
 * VoiceInteractionManagerService binds the chosen assistant at
 * PHASE_THIRD_PARTY_APPS_CAN_START — before any unlock — using
 * BIND_FOREGROUND_SERVICE together with BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS.
 * Android 17's audio documentation names exactly that shape of binding as the
 * exemption from the background microphone restriction: foreground services
 * "are granted WIU access if they are started by ... system bindings
 * representing an elevated foreground state ... (such as for a
 * VoiceInteractionService)".
 *
 * So if the exemption is real anywhere, it is real HERE, in a process the
 * system itself brought up. That makes this the cleanest possible test: no
 * foreground service of our own, no boot broadcast, no notification — just the
 * OS binding us because we are the assistant, and us asking for a microphone.
 *
 * directBootAware is the trap that is in no document. AOSP calls
 * getServiceInfo(serviceComponent, 0, mCurUser) with flags of ZERO, and before
 * first unlock PackageManager matches only direct-boot-aware components. Drop
 * that attribute in the manifest and this class silently fails to resolve at
 * boot, binds later at unlock instead, and every log looks like success while
 * measuring the wrong moment entirely.
 *
 * onReady() is the earliest point at which the system has finished connecting
 * to us, so it is where the clock starts.
 */
public class PennyVoiceService extends VoiceInteractionService {

    private static final String TAG = MicProbe.TAG;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "==== PennyVoiceService onCreate — the OS bound the assistant"
                + " sinceBoot=" + SystemClock.elapsedRealtime() + "ms ====");
    }

    @Override
    public void onReady() {
        super.onReady();
        Log.i(TAG, "PennyVoiceService onReady sinceBoot="
                + SystemClock.elapsedRealtime() + "ms");

        // Attempt A. If this comes back with real audio before the PIN is
        // typed, rung 3b is answered yes and Penny's shape survives.
        MicProbe.runAsync(this, "A-assistant");
    }

    @Override
    public void onShutdown() {
        Log.w(TAG, "PennyVoiceService onShutdown");
        super.onShutdown();
    }
}

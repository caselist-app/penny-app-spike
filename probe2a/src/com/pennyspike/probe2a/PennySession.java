package com.pennyspike.probe2a;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.service.voice.VoiceInteractionSession;

/**
 * Required by the assistant contract, and deliberately empty.
 *
 * This is what the OS constructs when a human actually invokes the assistant —
 * long-press, hotword, whatever. Rung 3b never invokes it; the question is
 * whether the microphone is available at boot with nobody in the room, which
 * is answered before any session exists. The class exists because
 * voice_interaction.xml must name a session service, and a session service
 * must be able to produce one of these.
 */
public class PennySession extends VoiceInteractionSession {

    public PennySession(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(MicProbe.TAG, "PennySession onCreate — a human invoked the assistant");
    }
}

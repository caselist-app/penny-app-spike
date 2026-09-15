package com.pennyspike.probe2a;

import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;
import android.util.Log;

/**
 * Required by voice_interaction.xml. Named there, protected by
 * BIND_VOICE_INTERACTION in the manifest, and otherwise does nothing.
 *
 * PermissionController rejects the app as an assistant candidate — silently,
 * with no log line — if the session service named in the XML cannot be
 * resolved. That silent rejection is why this stub exists.
 */
public class PennySessionService extends VoiceInteractionSessionService {

    @Override
    public VoiceInteractionSession onNewSession(Bundle args) {
        Log.i(MicProbe.TAG, "PennySessionService onNewSession");
        return new PennySession(this);
    }
}

package com.pennyspike.probe2a;

import android.content.Intent;
import android.speech.RecognitionService;
import android.util.Log;

/**
 * The third of the three the assistant declaration must name. A stub.
 *
 * Penny's real recognition is meant to happen inside the guest VM, which rung
 * 3b does not touch — this rung measures Android handing the app a microphone,
 * not voice reaching Penny. But an app cannot qualify as an assistant without
 * offering a recogniser, so here it is.
 */
public class PennyRecognitionService extends RecognitionService {

    @Override
    protected void onStartListening(Intent recognizerIntent, Callback listener) {
        Log.i(MicProbe.TAG, "PennyRecognitionService onStartListening (stub)");
    }

    @Override
    protected void onCancel(Callback listener) {
        Log.i(MicProbe.TAG, "PennyRecognitionService onCancel (stub)");
    }

    @Override
    protected void onStopListening(Callback listener) {
        Log.i(MicProbe.TAG, "PennyRecognitionService onStopListening (stub)");
    }
}

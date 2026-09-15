package com.pennyspike.probe2a;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

/**
 * Rung 3b, attempt site B: an ordinary foreground service started at boot.
 *
 * WHY THIS IS A SEPARATE SERVICE AND NOT A FEW LINES INSIDE VmService.
 *
 * Rung 3 proved, twice, that VmService brings the VM up ~15s after power-on
 * with the phone locked. Giving that service a microphone foreground-service
 * type would put the proven result at risk: on Android 14 and later the
 * microphone and camera FGS types are exactly the ones the system may refuse
 * to start from the background, and that refusal arrives as an exception from
 * startForeground — killing the service, and with it the VM. Rung 3's result
 * is worth more than the convenience of one file, so the mic attempt lives
 * here and VmService is untouched.
 *
 * Keeping them apart also makes the finding cleaner. The open question behind
 * this rung is whether the wake service and the listening service have to be
 * two different things. Building them as two different things is the only way
 * to find out, and if A succeeds where B fails, that IS the answer.
 *
 * Three distinct outcomes are possible here and they mean different things:
 *
 *   startForegroundService throws          the boot-start rule blocks a
 *                                          microphone-typed service outright.
 *                                          Logged by BootReceiver, not here.
 *   startForeground throws                 same rule, enforced one step later.
 *   it starts and records digital silence  the service ran, held the type, and
 *                                          Android suppressed the audio anyway
 *                                          — i.e. being the assistant did not
 *                                          extend the exemption this far.
 */
public class MicFgsService extends Service {

    private static final String TAG = MicProbe.TAG;
    private static final String CHANNEL = "penny3b";
    private static final int NOTIFICATION_ID = 2;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String why = (intent == null) ? "RESTARTED-BY-SYSTEM" : intent.getStringExtra("why");
        Log.i(TAG, "==== MicFgsService onStartCommand why=" + why
                + " sinceBoot=" + SystemClock.elapsedRealtime() + "ms ====");

        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "Penny mic probe", NotificationManager.IMPORTANCE_LOW));

            Notification n = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setContentTitle("Penny mic probe")
                    .setContentText("listening test")
                    .setOngoing(true)
                    .build();

            startForeground(NOTIFICATION_ID, n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            Log.i(TAG, "MicFgsService startForeground(MICROPHONE) ACCEPTED");
        } catch (Throwable t) {
            // This is a finding, not a bug. It means the boot-start rule shut
            // the door before the microphone question was ever asked.
            Log.e(TAG, "MicFgsService startForeground(MICROPHONE) REFUSED -> "
                    + t.getClass().getName() + ": " + t.getMessage(), t);
            Log.e(TAG, "MIC [B-fgs] VERDICT: NOT TESTED — the service could not"
                    + " hold the microphone FGS type at boot, so no conclusion"
                    + " about suppression can be drawn from site B.");
            stopSelf();
            return START_NOT_STICKY;
        }

        MicProbe.runAsync(this, "B-fgs");

        // Deliberately not sticky. This service exists to take one measurement;
        // it must not compete with VmService for the process's life.
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

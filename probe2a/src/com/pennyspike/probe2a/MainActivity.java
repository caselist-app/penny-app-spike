package com.pennyspike.probe2a;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Rung 2a: can a sideloaded, non-platform-signed app touch
 * android.system.virtualmachine at all?
 *
 * Everything here is reflection. The classes are @SystemApi and are absent
 * from the stock android.jar, so they cannot be named at compile time. That
 * is a compile-time inconvenience only — the gate we are measuring is
 * enforced at runtime by the Android runtime and by VirtualizationService,
 * and it does not care how the compiler was satisfied. A verdict reached
 * here therefore binds on the stub-class route we will use for 2b and 2c.
 *
 * There are three interesting outcomes and they mean different things:
 *
 *   STEP1 fails  - the class is not visible to a third-party classloader at
 *                  all. Nothing else matters; the app route is dead here.
 *   STEP3 fails  - the class is visible but the runtime's non-SDK interface
 *                  restrictions, or a permission check, refuse the call.
 *                  Read the exception type carefully: it distinguishes the
 *                  two.
 *   STEP4 works  - we hold a live VirtualMachineManager and it answered.
 *
 * Everything is logged under one tag so the whole run can be read back with
 * a single adb command. Nothing is printed on screen.
 */
public class MainActivity extends Activity {

    private static final String TAG = "PENNY2A";
    private static final String VMM = "android.system.virtualmachine.VirtualMachineManager";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "==== rung 2a probe start ====");
        Log.i(TAG, "uid=" + Process.myUid()
                + " pkg=" + getPackageName()
                + " api=" + Build.VERSION.SDK_INT
                + " fingerprint=" + Build.FINGERPRINT);

        // Whether pm grant actually took. If these say DENIED, any refusal
        // below is explained and the probe has not tested what we think.
        reportPermission("android.permission.MANAGE_VIRTUAL_MACHINE");
        reportPermission("android.permission.USE_CUSTOM_VIRTUAL_MACHINE");

        probe();

        Log.i(TAG, "==== rung 2a probe end ====");
    }

    private void reportPermission(String name) {
        int state = checkSelfPermission(name);
        Log.i(TAG, "PERM " + name + " = "
                + (state == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
    }

    private void probe() {
        Class<?> vmm;

        // STEP 1 — is the class reachable from a third-party classloader?
        try {
            vmm = Class.forName(VMM);
            Log.i(TAG, "STEP1 class found: " + vmm.getName()
                    + " loader=" + vmm.getClassLoader());
        } catch (Throwable t) {
            Log.e(TAG, "STEP1 class NOT reachable -> " + describe(t), t);
            Log.e(TAG, "VERDICT 2a: class not visible to a sideloaded app");
            return;
        }

        // STEP 2 — what does it actually offer? Read the real signatures
        // rather than trusting anything written down about them.
        try {
            for (Method m : vmm.getDeclaredMethods()) {
                Log.i(TAG, "STEP2 method: " + m);
            }
        } catch (Throwable t) {
            Log.e(TAG, "STEP2 could not enumerate methods -> " + describe(t), t);
        }

        // STEP 3 — get hold of an instance. Two plausible factory routes;
        // try the documented static one first, then the system-service one.
        Object manager = null;

        try {
            Method get = vmm.getMethod("getInstance", Context.class);
            manager = get.invoke(null, this);
            Log.i(TAG, "STEP3A getInstance(Context) returned " + manager);
        } catch (Throwable t) {
            Log.e(TAG, "STEP3A getInstance(Context) failed -> " + describe(t), t);
        }

        if (manager == null) {
            try {
                manager = getSystemService(vmm);
                Log.i(TAG, "STEP3B getSystemService(class) returned " + manager);
            } catch (Throwable t) {
                Log.e(TAG, "STEP3B getSystemService(class) failed -> " + describe(t), t);
            }
        }

        if (manager == null) {
            Log.e(TAG, "VERDICT 2a: class visible, no manager obtained");
            return;
        }

        // STEP 4 — a live call. Cheap, read-only, creates nothing.
        try {
            Method caps = vmm.getMethod("getCapabilities");
            Object value = caps.invoke(manager);
            Log.i(TAG, "STEP4 getCapabilities() = " + value);
            Log.i(TAG, "VERDICT 2a: manager obtained and a call succeeded");
        } catch (Throwable t) {
            Log.e(TAG, "STEP4 getCapabilities() failed -> " + describe(t), t);
            Log.e(TAG, "VERDICT 2a: manager obtained, call refused");
        }
    }

    /** Reflection wraps the real failure one layer down. Unwrap it. */
    private static String describe(Throwable t) {
        Throwable cause = (t instanceof InvocationTargetException && t.getCause() != null)
                ? t.getCause()
                : t;
        return cause.getClass().getName() + ": " + cause.getMessage();
    }
}

package com.crtghoul.locklauncher;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;

public class BiometricHelper {

    public interface AuthCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public static boolean isBiometricSupported(Context ctx) {
        KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
        return km != null && km.isDeviceSecure();
    }

    public static void authenticate(Activity activity, String title, String subtitle, AuthCallback callback) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        KeyguardManager km = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
        if (km == null || !km.isDeviceSecure()) {
            // Device has no PIN, pattern, or biometrics set up; grant access
            callback.onSuccess();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                BiometricPrompt.Builder builder = new BiometricPrompt.Builder(activity)
                        .setTitle(title)
                        .setSubtitle(subtitle);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setAllowedAuthenticators(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG |
                            BiometricManager.Authenticators.BIOMETRIC_WEAK |
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    );
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setDeviceCredentialAllowed(true);
                } else {
                    builder.setNegativeButton("Cancel", activity.getMainExecutor(), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            callback.onFailure("Cancelled");
                        }
                    });
                }

                CancellationSignal signal = new CancellationSignal();
                BiometricPrompt prompt = builder.build();

                prompt.authenticate(signal, activity.getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                callback.onSuccess();
                            }
                        });
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                callback.onFailure(errString != null ? errString.toString() : "Authentication failed");
                            }
                        });
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                    }
                });
                return;
            } catch (Exception e) {
                // BiometricPrompt fallback
            }
        }
        callback.onSuccess();
    }
}

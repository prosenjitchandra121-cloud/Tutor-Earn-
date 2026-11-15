package com.tutor.earn.pro;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstraction for user authentication and profile initialization against Firebase Auth
 * and Firebase Realtime Database (https://proti-pro-tutor-default-rtdb.firebaseio.com).
 */
public interface AuthGateway {

    interface AuthCallback {
        void onSuccess(@NonNull String uid);

        void onError(@NonNull String message);
    }

    void signIn(@NonNull String email, @NonNull String password, @NonNull AuthCallback callback);

    void signUp(
            @NonNull String fullName,
            @NonNull String email,
            @NonNull String password,
            @NonNull AuthCallback callback
    );

    boolean isUserSignedIn();

    final class Holder {
        @Nullable
        private static AuthGateway testOverride;

        private Holder() {
        }

        @NonNull
        public static AuthGateway get(@NonNull Context context) {
            if (testOverride != null) {
                return testOverride;
            }
            return new FirebaseAuthGateway(context);
        }

        @VisibleForTesting
        public static void setTestOverride(@Nullable AuthGateway override) {
            testOverride = override;
        }
    }

    final class FirebaseAuthGateway implements AuthGateway {

        private final Context appContext;

        public FirebaseAuthGateway(@NonNull Context context) {
            this.appContext = context.getApplicationContext();
            try {
                TutorEarnApplication.ensureFirebaseInitialized(this.appContext);
            } catch (Throwable t) {
                Log.e(TutorEarnApplication.STARTUP_LOG_TAG, "FirebaseAuthGateway initialization failed", t);
            }
        }

        @Override
        public void signIn(
                @NonNull String email,
                @NonNull String password,
                @NonNull AuthCallback callback
        ) {
            try {
                FirebaseAuth auth = FirebaseAuth.getInstance();
                auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful() && auth.getCurrentUser() != null) {
                                FirebaseUser user = auth.getCurrentUser();
                                updateLastLoginTimestamp(user.getUid());
                                callback.onSuccess(user.getUid());
                            } else {
                                String errorMsg = task.getException() != null
                                        && task.getException().getLocalizedMessage() != null
                                        ? task.getException().getLocalizedMessage()
                                        : appContext.getString(R.string.error_firebase_auth_failed);
                                callback.onError(errorMsg);
                            }
                        });
            } catch (Throwable t) {
                Log.e(TutorEarnApplication.STARTUP_LOG_TAG, "FirebaseAuth signIn failed", t);
                callback.onError(t.getLocalizedMessage() != null
                        ? t.getLocalizedMessage()
                        : appContext.getString(R.string.error_firebase_auth_failed));
            }
        }

        @Override
        public void signUp(
                @NonNull String fullName,
                @NonNull String email,
                @NonNull String password,
                @NonNull AuthCallback callback
        ) {
            try {
                FirebaseAuth auth = FirebaseAuth.getInstance();
                auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(task -> {
                            if (!task.isSuccessful() || auth.getCurrentUser() == null) {
                                String errorMsg = task.getException() != null
                                        && task.getException().getLocalizedMessage() != null
                                        ? task.getException().getLocalizedMessage()
                                        : appContext.getString(R.string.error_firebase_auth_failed);
                                callback.onError(errorMsg);
                                return;
                            }

                            FirebaseUser user = auth.getCurrentUser();
                            UserProfileChangeRequest profileUpdate = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(fullName)
                                    .build();
                            user.updateProfile(profileUpdate);

                            createUserRecordInDatabase(user.getUid(), fullName, email);
                            callback.onSuccess(user.getUid());
                        });
            } catch (Throwable t) {
                callback.onError(t.getLocalizedMessage() != null
                        ? t.getLocalizedMessage()
                        : appContext.getString(R.string.error_firebase_auth_failed));
            }
        }

        @Override
        public boolean isUserSignedIn() {
            try {
                TutorEarnApplication.ensureFirebaseInitialized(appContext);
                return FirebaseAuth.getInstance().getCurrentUser() != null;
            } catch (Throwable t) {
                Log.e(TutorEarnApplication.STARTUP_LOG_TAG, "FirebaseAuth isUserSignedIn check failed", t);
                return false;
            }
        }

        private void updateLastLoginTimestamp(@NonNull String uid) {
            try {
                String dbUrl = appContext.getString(R.string.tutor_firebase_database_url);
                DatabaseReference profileRef = FirebaseDatabase.getInstance(dbUrl)
                        .getReference("users")
                        .child(uid)
                        .child("profile");
                Map<String, Object> updates = new HashMap<>();
                updates.put("lastLoginAt", ServerValue.TIMESTAMP);
                profileRef.updateChildren(updates);
            } catch (Throwable ignored) {
            }
        }

        private void createUserRecordInDatabase(
                @NonNull String uid,
                @NonNull String fullName,
                @NonNull String email
        ) {
            try {
                String dbUrl = appContext.getString(R.string.tutor_firebase_database_url);
                DatabaseReference userRef = FirebaseDatabase.getInstance(dbUrl)
                        .getReference("users")
                        .child(uid);

                Map<String, Object> profile = new HashMap<>();
                profile.put("fullName", fullName);
                profile.put("email", email);
                profile.put("createdAt", ServerValue.TIMESTAMP);
                profile.put("lastLoginAt", ServerValue.TIMESTAMP);

                Map<String, Object> initialCoins = new HashMap<>();
                initialCoins.put("balance", 0L);

                Map<String, Object> initialUserPayload = new HashMap<>();
                initialUserPayload.put("profile", profile);
                initialUserPayload.put("coins", initialCoins);

                userRef.updateChildren(initialUserPayload);
            } catch (Throwable ignored) {
            }
        }
    }
}

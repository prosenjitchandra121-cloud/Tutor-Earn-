package com.tutor.earn.pro;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.Patterns;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tutor.earn.pro.databinding.ActivitySignupBinding;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Sign Up Screen for Tutor Earn.
 * Validates Full Name, Email Address, Password, and Confirm Password inputs
 * and navigates to {@link HomeActivity} on valid submission, or switches back
 * to {@link LoginActivity}.
 */
public class SignUpActivity extends AppCompatActivity {

    private ActivitySignupBinding binding;
    private AnimatorSet logoGlowAnimatorSet;
    private AnimatorSet ctaGlowAnimatorSet;
    private AuthGateway authGateway;
    private boolean isNavigating = false;
    private boolean isAuthenticating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TutorEarnApplication.STARTUP_LOG_TAG, "SignUpActivity.onCreate() started");
        EdgeToEdge.enable(
                this,
                SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.dark(Color.TRANSPARENT)
        );
        super.onCreate(savedInstanceState);

        binding = ActivitySignupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authGateway = AuthGateway.Holder.get(this);
        applySystemAndImeInsets();
        styleSwitchToLoginLink();
        setupInputListeners();
        setupCreateAccountButton();
        startAnimations();
        binding.getRoot().post(() -> Log.i(
                TutorEarnApplication.STARTUP_LOG_TAG,
                "SignUpActivity first frame laid out and visible"
        ));
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TutorEarnApplication.STARTUP_LOG_TAG, "SignUpActivity.onResume() active");
    }

    private void applySystemAndImeInsets() {
        int baseHorizontal = dpToPx(20);
        int baseTop = dpToPx(14);
        int baseBottom = dpToPx(22);

        ViewCompat.setOnApplyWindowInsetsListener(binding.signupContentWrapper, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
                            | WindowInsetsCompat.Type.ime()
            );
            view.setPadding(
                    baseHorizontal + insets.left,
                    baseTop + insets.top,
                    baseHorizontal + insets.right,
                    baseBottom + insets.bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void styleSwitchToLoginLink() {
        String fullText = getString(R.string.link_have_account);
        String actionText = "Login";
        SpannableString spannable = new SpannableString(fullText);
        int index = fullText.indexOf(actionText);
        if (index >= 0) {
            int goldColor = ContextCompat.getColor(this, R.color.gold_primary);
            spannable.setSpan(
                    new ForegroundColorSpan(goldColor),
                    index,
                    index + actionText.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            spannable.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    index,
                    index + actionText.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        binding.tvGoToLogin.setText(spannable);
        binding.tvGoToLogin.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            navigateToLogin();
        });
    }

    private void setupInputListeners() {
        clearErrorOnType(binding.etSignupName, binding.tilSignupName);
        clearErrorOnType(binding.etSignupEmail, binding.tilSignupEmail);
        clearErrorOnType(binding.etSignupPassword, binding.tilSignupPassword);
        clearErrorOnType(binding.etSignupConfirmPassword, binding.tilSignupConfirmPassword);

        binding.etSignupConfirmPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptSignUp();
                return true;
            }
            return false;
        });
    }

    private void clearErrorOnType(android.widget.EditText editText, TextInputLayout inputLayout) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (inputLayout.getError() != null) {
                    inputLayout.setError(null);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupCreateAccountButton() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int goldGlow = ContextCompat.getColor(this, R.color.earn_ai_glow);
            binding.btnCreateAccount.setOutlineSpotShadowColor(goldGlow);
            binding.btnCreateAccount.setOutlineAmbientShadowColor(goldGlow);
        }

        binding.btnCreateAccount.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate()
                            .scaleX(0.96f)
                            .scaleY(0.96f)
                            .setDuration(100L)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(180L)
                            .setInterpolator(new OvershootInterpolator(1.7f))
                            .start();
                    break;
            }
            return false;
        });

        binding.btnCreateAccount.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            attemptSignUp();
        });
    }

    private void attemptSignUp() {
        String fullName = binding.etSignupName.getText() != null
                ? binding.etSignupName.getText().toString().trim()
                : "";
        String email = binding.etSignupEmail.getText() != null
                ? binding.etSignupEmail.getText().toString().trim()
                : "";
        String password = binding.etSignupPassword.getText() != null
                ? binding.etSignupPassword.getText().toString()
                : "";
        String confirmPassword = binding.etSignupConfirmPassword.getText() != null
                ? binding.etSignupConfirmPassword.getText().toString()
                : "";

        boolean isValid = true;
        View firstErrorField = null;

        if (fullName.isEmpty()) {
            binding.tilSignupName.setError(getString(R.string.error_name_required));
            shakeView(binding.tilSignupName);
            firstErrorField = binding.etSignupName;
            isValid = false;
        } else if (fullName.length() < 2) {
            binding.tilSignupName.setError(getString(R.string.error_name_too_short));
            shakeView(binding.tilSignupName);
            firstErrorField = binding.etSignupName;
            isValid = false;
        } else {
            binding.tilSignupName.setError(null);
        }

        if (email.isEmpty()) {
            binding.tilSignupEmail.setError(getString(R.string.error_email_required));
            shakeView(binding.tilSignupEmail);
            if (firstErrorField == null) {
                firstErrorField = binding.etSignupEmail;
            }
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilSignupEmail.setError(getString(R.string.error_email_invalid));
            shakeView(binding.tilSignupEmail);
            if (firstErrorField == null) {
                firstErrorField = binding.etSignupEmail;
            }
            isValid = false;
        } else {
            binding.tilSignupEmail.setError(null);
        }

        if (password.isEmpty()) {
            binding.tilSignupPassword.setError(getString(R.string.error_password_required));
            shakeView(binding.tilSignupPassword);
            if (firstErrorField == null) {
                firstErrorField = binding.etSignupPassword;
            }
            isValid = false;
        } else if (password.length() < 6) {
            binding.tilSignupPassword.setError(getString(R.string.error_password_too_short));
            shakeView(binding.tilSignupPassword);
            if (firstErrorField == null) {
                firstErrorField = binding.etSignupPassword;
            }
            isValid = false;
        } else {
            binding.tilSignupPassword.setError(null);
        }

        if (confirmPassword.isEmpty()) {
            binding.tilSignupConfirmPassword.setError(getString(R.string.error_confirm_password_required));
            shakeView(binding.tilSignupConfirmPassword);
            if (firstErrorField == null) {
                firstErrorField = binding.etSignupConfirmPassword;
            }
            isValid = false;
        } else if (!confirmPassword.equals(password)) {
            binding.tilSignupConfirmPassword.setError(getString(R.string.error_passwords_mismatch));
            shakeView(binding.tilSignupConfirmPassword);
            if (firstErrorField == null) {
                firstErrorField = binding.etSignupConfirmPassword;
            }
            isValid = false;
        } else {
            binding.tilSignupConfirmPassword.setError(null);
        }

        if (!isValid) {
            if (firstErrorField != null) {
                firstErrorField.requestFocus();
            }
            return;
        }

        if (isAuthenticating || isNavigating) {
            return;
        }
        setAuthLoading(true);
        authGateway.signUp(fullName, email, password, new AuthGateway.AuthCallback() {
            @Override
            public void onSuccess(@androidx.annotation.NonNull String uid) {
                if (isFinishing() || isDestroyed() || binding == null) {
                    return;
                }
                setAuthLoading(false);
                navigateToHome();
            }

            @Override
            public void onError(@androidx.annotation.NonNull String message) {
                if (isFinishing() || isDestroyed() || binding == null) {
                    return;
                }
                setAuthLoading(false);
                binding.tilSignupEmail.setError(message);
                shakeView(binding.tilSignupEmail);
            }
        });
    }

    private void setAuthLoading(boolean loading) {
        isAuthenticating = loading;
        binding.btnCreateAccount.setEnabled(!loading);
        binding.btnCreateAccount.setClickable(!loading);
        binding.btnCreateAccount.setAlpha(loading ? 0.72f : 1.0f);
        binding.btnCreateAccountText.setText(
                loading ? R.string.btn_creating_account : R.string.btn_create_account
        );
    }

    private void shakeView(View target) {
        float offset = dpToPx(6);
        ObjectAnimator animator = ObjectAnimator.ofFloat(
                target,
                "translationX",
                0f, -offset, offset, -offset * 0.6f, offset * 0.6f, 0f
        );
        animator.setDuration(320L);
        animator.start();
    }

    private void startAnimations() {
        View[] sequence = new View[]{
                binding.signupBrandHeader,
                binding.signupFormCard,
                binding.tvGoToLogin
        };
        float initialOffset = dpToPx(18);
        DecelerateInterpolator interpolator = new DecelerateInterpolator();

        for (int i = 0; i < sequence.length; i++) {
            View target = sequence[i];
            target.setAlpha(0f);
            target.setTranslationY(initialOffset);
            target.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * 75L)
                    .setDuration(420L)
                    .setInterpolator(interpolator)
                    .start();
        }

        ObjectAnimator logoAlpha = ObjectAnimator.ofFloat(binding.signupLogoGlow, "alpha", 0.50f, 1.0f);
        logoAlpha.setRepeatCount(ValueAnimator.INFINITE);
        logoAlpha.setRepeatMode(ValueAnimator.REVERSE);
        ObjectAnimator logoScaleX = ObjectAnimator.ofFloat(binding.signupLogoGlow, "scaleX", 0.92f, 1.08f);
        logoScaleX.setRepeatCount(ValueAnimator.INFINITE);
        logoScaleX.setRepeatMode(ValueAnimator.REVERSE);
        ObjectAnimator logoScaleY = ObjectAnimator.ofFloat(binding.signupLogoGlow, "scaleY", 0.92f, 1.08f);
        logoScaleY.setRepeatCount(ValueAnimator.INFINITE);
        logoScaleY.setRepeatMode(ValueAnimator.REVERSE);

        logoGlowAnimatorSet = new AnimatorSet();
        logoGlowAnimatorSet.playTogether(logoAlpha, logoScaleX, logoScaleY);
        logoGlowAnimatorSet.setDuration(1300L);
        logoGlowAnimatorSet.start();

        ObjectAnimator ctaAlpha = ObjectAnimator.ofFloat(binding.btnCreateAccountGlow, "alpha", 0.45f, 0.92f);
        ctaAlpha.setRepeatCount(ValueAnimator.INFINITE);
        ctaAlpha.setRepeatMode(ValueAnimator.REVERSE);
        ObjectAnimator ctaScaleY = ObjectAnimator.ofFloat(binding.btnCreateAccountGlow, "scaleY", 1.15f, 1.30f);
        ctaScaleY.setRepeatCount(ValueAnimator.INFINITE);
        ctaScaleY.setRepeatMode(ValueAnimator.REVERSE);

        ctaGlowAnimatorSet = new AnimatorSet();
        ctaGlowAnimatorSet.playTogether(ctaAlpha, ctaScaleY);
        ctaGlowAnimatorSet.setDuration(1200L);
        ctaGlowAnimatorSet.start();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(SignUpActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        applyTransition();
        finish();
    }

    private void navigateToHome() {
        if (isNavigating || isFinishing() || isDestroyed()) {
            return;
        }
        isNavigating = true;
        Log.i(TutorEarnApplication.STARTUP_LOG_TAG, "SignUpActivity navigating to HomeActivity");

        Intent intent = new Intent(SignUpActivity.this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        applyTransition();
        finish();
    }

    private void applyTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                    OVERRIDE_TRANSITION_OPEN,
                    R.anim.fade_in_smooth,
                    R.anim.fade_out_smooth
            );
        } else {
            applyLegacyTransition();
        }
    }

    @SuppressWarnings("deprecation")
    private void applyLegacyTransition() {
        overridePendingTransition(R.anim.fade_in_smooth, R.anim.fade_out_smooth);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (logoGlowAnimatorSet != null) {
            logoGlowAnimatorSet.cancel();
        }
        if (ctaGlowAnimatorSet != null) {
            ctaGlowAnimatorSet.cancel();
        }
        binding = null;
        super.onDestroy();
    }
}

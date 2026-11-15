package com.example;

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

import com.example.databinding.ActivityLoginBinding;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Login Screen for Tutor Earn.
 * Validates Email Address and Password inputs and navigates to {@link HomeActivity}
 * on valid submission, or switches to {@link SignUpActivity}.
 */
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private AnimatorSet logoGlowAnimatorSet;
    private AnimatorSet ctaGlowAnimatorSet;
    private AuthGateway authGateway;
    private boolean isNavigating = false;
    private boolean isAuthenticating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(
                this,
                SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.dark(Color.TRANSPARENT)
        );
        super.onCreate(savedInstanceState);

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authGateway = AuthGateway.Holder.get(this);
        applySystemAndImeInsets();
        styleSwitchToSignUpLink();
        setupInputListeners();
        setupLoginButton();
        startAnimations();
    }

    private void applySystemAndImeInsets() {
        int baseHorizontal = dpToPx(20);
        int baseTop = dpToPx(18);
        int baseBottom = dpToPx(24);

        ViewCompat.setOnApplyWindowInsetsListener(binding.loginContentWrapper, (view, windowInsets) -> {
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

    private void styleSwitchToSignUpLink() {
        String fullText = getString(R.string.link_no_account);
        String actionText = "Sign Up";
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
        binding.tvGoToSignup.setText(spannable);
        binding.tvGoToSignup.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            navigateToSignUp();
        });
    }

    private void setupInputListeners() {
        clearErrorOnType(binding.etLoginEmail, binding.tilLoginEmail);
        clearErrorOnType(binding.etLoginPassword, binding.tilLoginPassword);

        binding.etLoginPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
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
    private void setupLoginButton() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int goldGlow = ContextCompat.getColor(this, R.color.earn_ai_glow);
            binding.btnLogin.setOutlineSpotShadowColor(goldGlow);
            binding.btnLogin.setOutlineAmbientShadowColor(goldGlow);
        }

        binding.btnLogin.setOnTouchListener((view, event) -> {
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

        binding.btnLogin.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            attemptLogin();
        });
    }

    private void attemptLogin() {
        String email = binding.etLoginEmail.getText() != null
                ? binding.etLoginEmail.getText().toString().trim()
                : "";
        String password = binding.etLoginPassword.getText() != null
                ? binding.etLoginPassword.getText().toString()
                : "";

        boolean isValid = true;
        View firstErrorField = null;

        if (email.isEmpty()) {
            binding.tilLoginEmail.setError(getString(R.string.error_email_required));
            shakeView(binding.tilLoginEmail);
            firstErrorField = binding.etLoginEmail;
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilLoginEmail.setError(getString(R.string.error_email_invalid));
            shakeView(binding.tilLoginEmail);
            firstErrorField = binding.etLoginEmail;
            isValid = false;
        } else {
            binding.tilLoginEmail.setError(null);
        }

        if (password.isEmpty()) {
            binding.tilLoginPassword.setError(getString(R.string.error_password_required));
            shakeView(binding.tilLoginPassword);
            if (firstErrorField == null) {
                firstErrorField = binding.etLoginPassword;
            }
            isValid = false;
        } else if (password.length() < 6) {
            binding.tilLoginPassword.setError(getString(R.string.error_password_too_short));
            shakeView(binding.tilLoginPassword);
            if (firstErrorField == null) {
                firstErrorField = binding.etLoginPassword;
            }
            isValid = false;
        } else {
            binding.tilLoginPassword.setError(null);
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
        authGateway.signIn(email, password, new AuthGateway.AuthCallback() {
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
                binding.tilLoginPassword.setError(message);
                shakeView(binding.tilLoginPassword);
            }
        });
    }

    private void setAuthLoading(boolean loading) {
        isAuthenticating = loading;
        binding.btnLogin.setEnabled(!loading);
        binding.btnLogin.setClickable(!loading);
        binding.btnLogin.setAlpha(loading ? 0.72f : 1.0f);
        binding.btnLoginText.setText(loading ? R.string.btn_logging_in : R.string.btn_login);
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
                binding.loginBrandHeader,
                binding.loginFormCard,
                binding.tvGoToSignup
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

        ObjectAnimator logoAlpha = ObjectAnimator.ofFloat(binding.loginLogoGlow, "alpha", 0.50f, 1.0f);
        logoAlpha.setRepeatCount(ValueAnimator.INFINITE);
        logoAlpha.setRepeatMode(ValueAnimator.REVERSE);
        ObjectAnimator logoScaleX = ObjectAnimator.ofFloat(binding.loginLogoGlow, "scaleX", 0.92f, 1.08f);
        logoScaleX.setRepeatCount(ValueAnimator.INFINITE);
        logoScaleX.setRepeatMode(ValueAnimator.REVERSE);
        ObjectAnimator logoScaleY = ObjectAnimator.ofFloat(binding.loginLogoGlow, "scaleY", 0.92f, 1.08f);
        logoScaleY.setRepeatCount(ValueAnimator.INFINITE);
        logoScaleY.setRepeatMode(ValueAnimator.REVERSE);

        logoGlowAnimatorSet = new AnimatorSet();
        logoGlowAnimatorSet.playTogether(logoAlpha, logoScaleX, logoScaleY);
        logoGlowAnimatorSet.setDuration(1300L);
        logoGlowAnimatorSet.start();

        ObjectAnimator ctaAlpha = ObjectAnimator.ofFloat(binding.btnLoginGlow, "alpha", 0.45f, 0.92f);
        ctaAlpha.setRepeatCount(ValueAnimator.INFINITE);
        ctaAlpha.setRepeatMode(ValueAnimator.REVERSE);
        ObjectAnimator ctaScaleY = ObjectAnimator.ofFloat(binding.btnLoginGlow, "scaleY", 1.15f, 1.30f);
        ctaScaleY.setRepeatCount(ValueAnimator.INFINITE);
        ctaScaleY.setRepeatMode(ValueAnimator.REVERSE);

        ctaGlowAnimatorSet = new AnimatorSet();
        ctaGlowAnimatorSet.playTogether(ctaAlpha, ctaScaleY);
        ctaGlowAnimatorSet.setDuration(1200L);
        ctaGlowAnimatorSet.start();
    }

    private void navigateToSignUp() {
        Intent intent = new Intent(LoginActivity.this, SignUpActivity.class);
        startActivity(intent);
        applyTransition();
    }

    private void navigateToHome() {
        if (isNavigating || isFinishing() || isDestroyed()) {
            return;
        }
        isNavigating = true;

        Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
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

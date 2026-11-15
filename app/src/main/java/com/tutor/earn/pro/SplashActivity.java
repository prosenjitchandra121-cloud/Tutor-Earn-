package com.tutor.earn.pro;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tutor.earn.pro.databinding.ActivitySplashBinding;

/**
 * Animated Splash Screen for Tutor Earn.
 * Features a deep navy-purple gradient, glowing 3D-style education emblem,
 * golden-accented tagline, and smooth rotating loading ring before transitioning
 * automatically to {@link HomeActivity}.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DURATION_MS = 2200L;

    private ActivitySplashBinding binding;
    private final Handler navigationHandler = new Handler(Looper.getMainLooper());
    private ObjectAnimator ringAnimator;
    private ObjectAnimator floatAnimator;
    private AnimatorSet glowAnimatorSet;
    private boolean hasNavigated = false;

    private final Runnable navigateToHomeRunnable = this::navigateToHome;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TutorEarnApplication.STARTUP_LOG_TAG, "SplashActivity.onCreate() started");
        EdgeToEdge.enable(
                this,
                SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.dark(Color.TRANSPARENT)
        );
        super.onCreate(savedInstanceState);

        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applySystemBarInsets();
        styleTaglineDots();
        startSplashAnimations();

        navigationHandler.postDelayed(navigateToHomeRunnable, SPLASH_DURATION_MS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TutorEarnApplication.STARTUP_LOG_TAG, "SplashActivity.onResume() visible");
    }

    private void applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.splashRoot, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void styleTaglineDots() {
        String raw = getString(R.string.tagline_learn_complete_earn);
        SpannableString spannable = new SpannableString(raw);
        int goldColor = ContextCompat.getColor(this, R.color.gold_primary);
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) == '•') {
                spannable.setSpan(
                        new ForegroundColorSpan(goldColor),
                        i,
                        i + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }
        binding.splashTagline.setText(spannable);
    }

    private void startSplashAnimations() {
        // Entrance animation for brand emblem, title, and tagline
        binding.splashBrandContainer.setAlpha(0f);
        binding.splashBrandContainer.setScaleX(0.90f);
        binding.splashBrandContainer.setScaleY(0.90f);
        binding.splashBrandContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(680L)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Subtle fade-in for loader group
        binding.splashLoaderContainer.setAlpha(0f);
        binding.splashLoaderContainer.animate()
                .alpha(1f)
                .setStartDelay(200L)
                .setDuration(520L)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Gentle floating levitation on the 3D emblem
        float floatOffset = 6f * getResources().getDisplayMetrics().density;
        floatAnimator = ObjectAnimator.ofFloat(binding.splashEmblem, "translationY", -floatOffset, floatOffset);
        floatAnimator.setDuration(1500L);
        floatAnimator.setRepeatCount(ValueAnimator.INFINITE);
        floatAnimator.setRepeatMode(ValueAnimator.REVERSE);
        floatAnimator.setInterpolator(new DecelerateInterpolator());
        floatAnimator.start();

        // Ambient pulsing glow behind emblem
        ObjectAnimator glowAlpha = ObjectAnimator.ofFloat(binding.splashEmblemGlow, "alpha", 0.50f, 1.0f);
        glowAlpha.setRepeatCount(ValueAnimator.INFINITE);
        glowAlpha.setRepeatMode(ValueAnimator.REVERSE);

        ObjectAnimator glowScaleX = ObjectAnimator.ofFloat(binding.splashEmblemGlow, "scaleX", 0.90f, 1.10f);
        glowScaleX.setRepeatCount(ValueAnimator.INFINITE);
        glowScaleX.setRepeatMode(ValueAnimator.REVERSE);

        ObjectAnimator glowScaleY = ObjectAnimator.ofFloat(binding.splashEmblemGlow, "scaleY", 0.90f, 1.10f);
        glowScaleY.setRepeatCount(ValueAnimator.INFINITE);
        glowScaleY.setRepeatMode(ValueAnimator.REVERSE);

        glowAnimatorSet = new AnimatorSet();
        glowAnimatorSet.playTogether(glowAlpha, glowScaleX, glowScaleY);
        glowAnimatorSet.setDuration(1150L);
        glowAnimatorSet.start();

        // Smooth continuous rotation on luminous loading arc
        ringAnimator = ObjectAnimator.ofFloat(binding.splashLoadingRing, "rotation", 0f, 360f);
        ringAnimator.setDuration(900L);
        ringAnimator.setRepeatCount(ValueAnimator.INFINITE);
        ringAnimator.setInterpolator(new LinearInterpolator());
        ringAnimator.start();
    }

    private void navigateToHome() {
        if (hasNavigated || isFinishing() || isDestroyed()) {
            return;
        }
        hasNavigated = true;
        Log.i(TutorEarnApplication.STARTUP_LOG_TAG, "SplashActivity navigating to HomeActivity");

        Intent intent = new Intent(SplashActivity.this, HomeActivity.class);
        startActivity(intent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                    OVERRIDE_TRANSITION_OPEN,
                    R.anim.fade_in_smooth,
                    R.anim.fade_out_smooth
            );
        } else {
            applyLegacyTransition();
        }
        finish();
    }

    @SuppressWarnings("deprecation")
    private void applyLegacyTransition() {
        overridePendingTransition(R.anim.fade_in_smooth, R.anim.fade_out_smooth);
    }

    @Override
    protected void onDestroy() {
        navigationHandler.removeCallbacks(navigateToHomeRunnable);
        if (ringAnimator != null) {
            ringAnimator.cancel();
        }
        if (floatAnimator != null) {
            floatAnimator.cancel();
        }
        if (glowAnimatorSet != null) {
            glowAnimatorSet.cancel();
        }
        binding = null;
        super.onDestroy();
    }
}

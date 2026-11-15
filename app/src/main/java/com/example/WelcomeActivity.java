package com.example;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.databinding.ActivityWelcomeBinding;

/**
 * Initial Welcome / Landing Screen for Tutor Earn.
 * First screen displayed on app launch; transitions to {@link HomeActivity}
 * when the user taps "Get Started".
 */
public class WelcomeActivity extends AppCompatActivity {

    private ActivityWelcomeBinding binding;
    private ObjectAnimator floatAnimator;
    private AnimatorSet heroGlowAnimatorSet;
    private AnimatorSet ctaGlowAnimatorSet;
    private boolean isNavigating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(
                this,
                SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.dark(Color.TRANSPARENT)
        );
        super.onCreate(savedInstanceState);

        binding = ActivityWelcomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applySystemBarInsets();
        styleSubtitleDots();
        setupGetStartedButton();
        startEntranceAndAmbientAnimations();
    }

    private void applySystemBarInsets() {
        int baseHorizontal = dpToPx(24);
        int baseTop = dpToPx(24);
        int baseBottom = dpToPx(32);

        ViewCompat.setOnApplyWindowInsetsListener(binding.welcomeContentWrapper, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
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

    private void styleSubtitleDots() {
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
        binding.welcomeSubtitle.setText(spannable);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupGetStartedButton() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int goldGlow = ContextCompat.getColor(this, R.color.earn_ai_glow);
            binding.btnGetStarted.setOutlineSpotShadowColor(goldGlow);
            binding.btnGetStarted.setOutlineAmbientShadowColor(goldGlow);
        }

        binding.btnGetStarted.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate()
                            .scaleX(0.95f)
                            .scaleY(0.95f)
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

        binding.btnGetStarted.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            navigateToLogin();
        });
    }

    private void startEntranceAndAmbientAnimations() {
        View[] sequence = new View[]{
                binding.welcomeHeadlineBadge,
                binding.welcomeIllustrationGroup,
                binding.welcomeTitleRow,
                binding.welcomeSubtitle,
                binding.welcomeCtaContainer
        };

        float initialOffset = dpToPx(20);
        DecelerateInterpolator interpolator = new DecelerateInterpolator();

        for (int i = 0; i < sequence.length; i++) {
            View target = sequence[i];
            target.setAlpha(0f);
            target.setTranslationY(initialOffset);
            target.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * 75L)
                    .setDuration(460L)
                    .setInterpolator(interpolator)
                    .start();
        }

        // Gentle vertical floating on 3D illustration
        float floatOffset = 6f * getResources().getDisplayMetrics().density;
        floatAnimator = ObjectAnimator.ofFloat(binding.welcomeIllustration, "translationY", -floatOffset, floatOffset);
        floatAnimator.setDuration(1650L);
        floatAnimator.setRepeatCount(ValueAnimator.INFINITE);
        floatAnimator.setRepeatMode(ValueAnimator.REVERSE);
        floatAnimator.setInterpolator(new DecelerateInterpolator());
        floatAnimator.start();

        // Ambient pulsing glow behind 3D hero illustration
        ObjectAnimator heroAlpha = ObjectAnimator.ofFloat(binding.welcomeHeroGlow, "alpha", 0.50f, 1.0f);
        heroAlpha.setRepeatCount(ValueAnimator.INFINITE);
        heroAlpha.setRepeatMode(ValueAnimator.REVERSE);

        ObjectAnimator heroScaleX = ObjectAnimator.ofFloat(binding.welcomeHeroGlow, "scaleX", 0.90f, 1.10f);
        heroScaleX.setRepeatCount(ValueAnimator.INFINITE);
        heroScaleX.setRepeatMode(ValueAnimator.REVERSE);

        ObjectAnimator heroScaleY = ObjectAnimator.ofFloat(binding.welcomeHeroGlow, "scaleY", 0.90f, 1.10f);
        heroScaleY.setRepeatCount(ValueAnimator.INFINITE);
        heroScaleY.setRepeatMode(ValueAnimator.REVERSE);

        heroGlowAnimatorSet = new AnimatorSet();
        heroGlowAnimatorSet.playTogether(heroAlpha, heroScaleX, heroScaleY);
        heroGlowAnimatorSet.setDuration(1300L);
        heroGlowAnimatorSet.start();

        // Breathing glow pulse behind Get Started button
        ObjectAnimator ctaAlpha = ObjectAnimator.ofFloat(binding.btnGetStartedGlow, "alpha", 0.45f, 0.95f);
        ctaAlpha.setRepeatCount(ValueAnimator.INFINITE);
        ctaAlpha.setRepeatMode(ValueAnimator.REVERSE);

        ObjectAnimator ctaScaleX = ObjectAnimator.ofFloat(binding.btnGetStartedGlow, "scaleX", 1.01f, 1.08f);
        ctaScaleX.setRepeatCount(ValueAnimator.INFINITE);
        ctaScaleX.setRepeatMode(ValueAnimator.REVERSE);

        ObjectAnimator ctaScaleY = ObjectAnimator.ofFloat(binding.btnGetStartedGlow, "scaleY", 1.18f, 1.36f);
        ctaScaleY.setRepeatCount(ValueAnimator.INFINITE);
        ctaScaleY.setRepeatMode(ValueAnimator.REVERSE);

        ctaGlowAnimatorSet = new AnimatorSet();
        ctaGlowAnimatorSet.playTogether(ctaAlpha, ctaScaleX, ctaScaleY);
        ctaGlowAnimatorSet.setDuration(1200L);
        ctaGlowAnimatorSet.start();
    }

    private void navigateToLogin() {
        if (isNavigating || isFinishing() || isDestroyed()) {
            return;
        }
        isNavigating = true;

        Class<?> destination = AuthGateway.Holder.get(this).isUserSignedIn()
                ? HomeActivity.class
                : LoginActivity.class;
        Intent intent = new Intent(WelcomeActivity.this, destination);
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        isNavigating = false;
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
        if (floatAnimator != null) {
            floatAnimator.cancel();
        }
        if (heroGlowAnimatorSet != null) {
            heroGlowAnimatorSet.cancel();
        }
        if (ctaGlowAnimatorSet != null) {
            ctaGlowAnimatorSet.cancel();
        }
        binding = null;
        super.onDestroy();
    }
}

package com.example;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.databinding.ActivitySurveyRewardsBinding;

import java.util.UUID;

/**
 * Survey Rewards screen featuring account-based Firebase Realtime Database
 * integration and strict 24-hour reward claim enforcement (500 coins).
 */
public class SurveyRewardsActivity extends AppCompatActivity {

    public static final int REWARD_COINS = 500;
    private static final long COUNTDOWN_TICK_MS = 1000L;

    private ActivitySurveyRewardsBinding binding;
    private SurveyRewardsAdManager adManager;
    private SurveyRewardsRepository repository;

    private boolean isClaimFlowBusy = false;
    private AnimatorSet ambientAnimatorSet;

    private final Handler cooldownHandler = new Handler(Looper.getMainLooper());
    private long activeRemainingCooldownMs = 0L;
    private long lastTickElapsedMs = 0L;

    private final Runnable cooldownTicker = new Runnable() {
        @Override
        public void run() {
            if (binding == null) {
                return;
            }
            long nowElapsed = SystemClock.elapsedRealtime();
            long delta = Math.max(0L, nowElapsed - lastTickElapsedMs);
            lastTickElapsedMs = nowElapsed;

            activeRemainingCooldownMs = Math.max(0L, activeRemainingCooldownMs - delta);
            if (activeRemainingCooldownMs <= 0L) {
                refreshClaimEligibility();
                return;
            }
            updateCooldownLabel(activeRemainingCooldownMs);
            cooldownHandler.postDelayed(this, COUNTDOWN_TICK_MS);
        }
    };

    private int basePaddingHorizontal;
    private int basePaddingTop;
    private int basePaddingBottom;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(
                this,
                SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.dark(Color.TRANSPARENT)
        );
        super.onCreate(savedInstanceState);

        binding = ActivitySurveyRewardsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        basePaddingHorizontal = dpToPx(20);
        basePaddingTop = dpToPx(14);
        basePaddingBottom = dpToPx(28);

        applySystemBarInsets();
        setupDependencies();
        setupVisualPolish();
        setupInteractions();
        setupBackNavigation();

        adManager.initializeAndPreload();
        animateEntrance();
        startAmbientAnimations();
    }

    private void setupDependencies() {
        adManager = new SurveyRewardsAdManager(this);
        repository = new SurveyRewardsRepository(this);
    }

    private void applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.surveyRewardsContentWrapper, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    basePaddingHorizontal + insets.left,
                    basePaddingTop + insets.top,
                    basePaddingHorizontal + insets.right,
                    basePaddingBottom + insets.bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupVisualPolish() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int glowColor = ContextCompat.getColor(this, R.color.survey_rewards_glow);
            binding.cardSurveyReward.setOutlineSpotShadowColor(glowColor);
            binding.cardSurveyReward.setOutlineAmbientShadowColor(glowColor);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupInteractions() {
        binding.btnSurveyRewardsBack.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            finish();
        });

        binding.btnClaimSurveyReward.setOnTouchListener((view, event) -> {
            if (!view.isEnabled()) {
                return false;
            }
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
                            .setInterpolator(new OvershootInterpolator(1.6f))
                            .start();
                    break;
            }
            return false;
        });

        binding.btnClaimSurveyReward.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            onClaimClicked();
        });
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    private void refreshClaimEligibility() {
        cooldownHandler.removeCallbacks(cooldownTicker);
        repository.verifyClaimEligibility(state -> runOnUiThread(() -> {
            if (binding == null) {
                return;
            }
            if (state.canClaim) {
                activeRemainingCooldownMs = 0L;
                binding.tvSurveyRewardsCooldown.setVisibility(View.GONE);
                setClaimButtonState(true, false, getString(R.string.survey_rewards_claim_500_coins));
            } else {
                activeRemainingCooldownMs = state.remainingCooldownMs;
                updateCooldownLabel(activeRemainingCooldownMs);
                binding.tvSurveyRewardsCooldown.setVisibility(View.VISIBLE);
                setClaimButtonState(false, false, getString(R.string.survey_rewards_claim_500_coins));
                startCooldownTicker();
            }
        }));
    }

    private void startCooldownTicker() {
        cooldownHandler.removeCallbacks(cooldownTicker);
        lastTickElapsedMs = SystemClock.elapsedRealtime();
        cooldownHandler.postDelayed(cooldownTicker, COUNTDOWN_TICK_MS);
    }

    private void updateCooldownLabel(long remainingMs) {
        if (binding == null) {
            return;
        }
        String formatted = PrimeSurveyRepository.formatRemainingHms(remainingMs);
        binding.tvSurveyRewardsCooldown.setText(
                getString(R.string.survey_rewards_cooldown_remaining, formatted)
        );
    }

    private void onClaimClicked() {
        if (isClaimFlowBusy || repository.isClaimInFlight() || isFinishing() || isDestroyed()) {
            return;
        }
        isClaimFlowBusy = true;
        hideStatusMessage();
        setClaimButtonLoading(true);

        final String transactionId = "sr_" + UUID.randomUUID().toString().replace("-", "");
        adManager.loadAndShowRewardedAd(this, new SurveyRewardsAdManager.RewardedAdOutcomeListener() {
            @Override
            public void onLoadingStarted() {
                runOnUiThread(() -> {
                    if (binding != null) {
                        setClaimButtonLoading(true);
                    }
                });
            }

            @Override
            public void onRewardVerified() {
                repository.commitVerifiedReward(
                        true,
                        transactionId,
                        (success, message, updatedState) -> runOnUiThread(() -> {
                            isClaimFlowBusy = false;
                            if (binding == null) {
                                return;
                            }
                            if (success) {
                                setClaimButtonLoading(false);
                                playCoinCelebrateAnimation();
                                showSuccessMessage(message);
                                refreshClaimEligibility();
                            } else {
                                setClaimButtonLoading(false);
                                showErrorMessage(message);
                                if (!updatedState.canClaim) {
                                    activeRemainingCooldownMs = updatedState.remainingCooldownMs;
                                    updateCooldownLabel(activeRemainingCooldownMs);
                                    binding.tvSurveyRewardsCooldown.setVisibility(View.VISIBLE);
                                    setClaimButtonState(false, false, getString(R.string.survey_rewards_claim_500_coins));
                                    startCooldownTicker();
                                }
                            }
                        })
                );
            }

            @Override
            public void onRewardNotEarned() {
                runOnUiThread(() -> {
                    isClaimFlowBusy = false;
                    if (binding == null) {
                        return;
                    }
                    setClaimButtonLoading(false);
                    showErrorMessage(getString(R.string.survey_rewards_ad_not_completed));
                    refreshClaimEligibility();
                });
            }

            @Override
            public void onAdUnavailableOrFailed(@NonNull String reason) {
                runOnUiThread(() -> {
                    isClaimFlowBusy = false;
                    if (binding == null) {
                        return;
                    }
                    setClaimButtonLoading(false);
                    showErrorMessage(getString(R.string.survey_rewards_ad_unavailable));
                    refreshClaimEligibility();
                });
            }
        });
    }

    private void setClaimButtonState(boolean enabled, boolean loading, @NonNull String label) {
        if (binding == null) {
            return;
        }
        binding.btnClaimSurveyReward.setEnabled(enabled && !loading);
        binding.btnClaimSurveyReward.setClickable(enabled && !loading);
        binding.btnClaimSurveyReward.setAlpha((enabled || loading) ? 1.0f : 0.45f);
        binding.claimSurveyRewardProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnClaimSurveyRewardText.setText(
                loading ? getString(R.string.survey_rewards_loading_ad) : label
        );
        binding.btnClaimSurveyRewardGlow.setVisibility(enabled && !loading ? View.VISIBLE : View.INVISIBLE);
    }

    private void setClaimButtonLoading(boolean loading) {
        setClaimButtonState(!loading, loading, getString(R.string.survey_rewards_claim_500_coins));
    }

    private void showSuccessMessage(@NonNull String message) {
        binding.tvSurveyRewardStatus.setBackgroundResource(R.drawable.bg_survey_option_correct);
        binding.tvSurveyRewardStatus.setTextColor(ContextCompat.getColor(this, R.color.emerald_bright));
        binding.tvSurveyRewardStatus.setText(message);
        binding.tvSurveyRewardStatus.setVisibility(View.VISIBLE);
    }

    private void showErrorMessage(@NonNull String message) {
        binding.tvSurveyRewardStatus.setBackgroundResource(R.drawable.bg_survey_option_wrong);
        binding.tvSurveyRewardStatus.setTextColor(ContextCompat.getColor(this, R.color.auth_error));
        binding.tvSurveyRewardStatus.setText(message);
        binding.tvSurveyRewardStatus.setVisibility(View.VISIBLE);
    }

    private void hideStatusMessage() {
        binding.tvSurveyRewardStatus.setVisibility(View.GONE);
    }

    private void animateEntrance() {
        binding.cardSurveyReward.setAlpha(0f);
        binding.cardSurveyReward.setTranslationY(dpToPx(18));
        binding.cardSurveyReward.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(380L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void startAmbientAnimations() {
        if (binding == null || ambientAnimatorSet != null) {
            return;
        }
        ObjectAnimator coinScaleX = ObjectAnimator.ofFloat(binding.ivSurveyRewardCoin, "scaleX", 1.0f, 1.07f);
        coinScaleX.setRepeatCount(ValueAnimator.INFINITE);
        coinScaleX.setRepeatMode(ValueAnimator.REVERSE);

        ObjectAnimator coinScaleY = ObjectAnimator.ofFloat(binding.ivSurveyRewardCoin, "scaleY", 1.0f, 1.07f);
        coinScaleY.setRepeatCount(ValueAnimator.INFINITE);
        coinScaleY.setRepeatMode(ValueAnimator.REVERSE);

        ObjectAnimator haloAlpha = ObjectAnimator.ofFloat(binding.ivSurveyRewardCoinGlow, "alpha", 0.48f, 0.96f);
        haloAlpha.setRepeatCount(ValueAnimator.INFINITE);
        haloAlpha.setRepeatMode(ValueAnimator.REVERSE);

        ObjectAnimator ctaGlowAlpha = ObjectAnimator.ofFloat(binding.btnClaimSurveyRewardGlow, "alpha", 0.45f, 0.92f);
        ctaGlowAlpha.setRepeatCount(ValueAnimator.INFINITE);
        ctaGlowAlpha.setRepeatMode(ValueAnimator.REVERSE);

        ambientAnimatorSet = new AnimatorSet();
        ambientAnimatorSet.playTogether(coinScaleX, coinScaleY, haloAlpha, ctaGlowAlpha);
        ambientAnimatorSet.setDuration(1400L);
        ambientAnimatorSet.start();
    }

    private void playCoinCelebrateAnimation() {
        if (binding == null) {
            return;
        }
        binding.ivSurveyRewardCoin.animate()
                .rotationYBy(360f)
                .setDuration(600L)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshClaimEligibility();
        if (ambientAnimatorSet != null && ambientAnimatorSet.isPaused()) {
            ambientAnimatorSet.resume();
        }
    }

    @Override
    protected void onPause() {
        cooldownHandler.removeCallbacks(cooldownTicker);
        if (ambientAnimatorSet != null && ambientAnimatorSet.isRunning()) {
            ambientAnimatorSet.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        cooldownHandler.removeCallbacksAndMessages(null);
        if (ambientAnimatorSet != null) {
            ambientAnimatorSet.cancel();
            ambientAnimatorSet = null;
        }
        if (adManager != null) {
            adManager.destroy();
        }
        binding = null;
        super.onDestroy();
    }
}

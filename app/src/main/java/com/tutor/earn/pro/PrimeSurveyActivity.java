package com.tutor.earn.pro;

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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tutor.earn.pro.databinding.ActivityPrimeSurveyBinding;

import java.util.List;

/**
 * Prime Survey Activity implementing:
 * - 15 non-repeating English MCQ questions with immediate feedback
 * - Exact alternating AdMob Interstitial Ad schedule (after Q2, Q4, Q6, Q8, Q10, Q12, Q14)
 * - No automatic ad on Q15 completion
 * - AdMob Rewarded Ad verification before granting 1050 virtual coins
 * - Tamper-resistant 24-hour reward cooldown with live countdown timer
 */
public class PrimeSurveyActivity extends AppCompatActivity {

    private static final long COUNTDOWN_TICK_MS = 1000L;

    private ActivityPrimeSurveyBinding binding;
    private PrimeSurveyAdManager adManager;
    private PrimeSurveyRepository repository;

    private List<PrimeSurveyQuestion> questions;
    private int currentQuestionIndex = 0;
    private int selectedOptionIndex = -1;
    private boolean hasAnsweredCurrent = false;
    private int correctAnswerCount = 0;
    private boolean isCompleted = false;

    private boolean isClaimFlowBusy = false;
    private boolean hasClaimedInThisView = false;
    private boolean isWaitingForInterstitialAd = false;
    private boolean isInterstitialRetryAvailable = false;
    private int awaitingAdForQuestionNumber = -1;

    private LinearLayout[] optionCards;
    private TextView[] optionTextViews;
    private ImageView[] optionStatusIcons;

    private AnimatorSet claimGlowAnimatorSet;
    private final Handler cooldownHandler = new Handler(Looper.getMainLooper());
    private long activeRemainingCooldownMs = 0L;
    private long lastTickElapsedMs = 0L;

    private final Runnable cooldownTicker = new Runnable() {
        @Override
        public void run() {
            if (binding == null || !isCompleted) {
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

        binding = ActivityPrimeSurveyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        basePaddingHorizontal = dpToPx(16);
        basePaddingTop = dpToPx(12);
        basePaddingBottom = dpToPx(24);

        applySystemBarInsets();
        setupDependencies();
        bindOptionViews();
        setupInteractions();
        setupBackNavigation();

        adManager.initializeAndPreload();
        binding.tvAdModeBadge.setVisibility(View.GONE);

        questions = PrimeSurveyQuestionBank.buildSessionQuestions(
                repository.getAnsweredIdsForToday(),
                true
        );

        renderCurrentQuestion();
    }

    private void setupDependencies() {
        adManager = new PrimeSurveyAdManager(this);
        repository = new PrimeSurveyRepository(this);
    }

    private void applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.primeSurveyContentWrapper, (view, windowInsets) -> {
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

    private void bindOptionViews() {
        optionCards = new LinearLayout[]{
                binding.optionCardA,
                binding.optionCardB,
                binding.optionCardC,
                binding.optionCardD
        };
        optionTextViews = new TextView[]{
                binding.tvOptionTextA,
                binding.tvOptionTextB,
                binding.tvOptionTextC,
                binding.tvOptionTextD
        };
        optionStatusIcons = new ImageView[]{
                binding.ivOptionStatusA,
                binding.ivOptionStatusB,
                binding.ivOptionStatusC,
                binding.ivOptionStatusD
        };
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupInteractions() {
        binding.btnSurveyBack.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            finish();
        });

        binding.btnBackToHome.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            finish();
        });

        for (int i = 0; i < optionCards.length; i++) {
            final int optionIdx = i;
            optionCards[i].setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                onOptionSelected(optionIdx);
            });
        }

        applyPressAnimation(binding.btnNextQuestion);
        binding.btnNextQuestion.setOnClickListener(v -> {
            if (!hasAnsweredCurrent || isWaitingForInterstitialAd || isInterstitialRetryAvailable) {
                return;
            }
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            onNextClicked();
        });

        applyPressAnimation(binding.btnRetryAd);
        binding.btnRetryAd.setOnClickListener(v -> {
            if (!isInterstitialRetryAvailable || isWaitingForInterstitialAd || awaitingAdForQuestionNumber <= 0) {
                return;
            }
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            onRetryInterstitialClicked();
        });

        applyPressAnimation(binding.btnClaimCoins);
        binding.btnClaimCoins.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            onClaimCoinsClicked();
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

    @SuppressLint("ClickableViewAccessibility")
    private void applyPressAnimation(@NonNull View target) {
        target.setOnTouchListener((view, event) -> {
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
    }

    private void renderCurrentQuestion() {
        isCompleted = false;
        binding.quizContainer.setVisibility(View.VISIBLE);
        binding.completionContainer.setVisibility(View.GONE);

        PrimeSurveyQuestion question = questions.get(currentQuestionIndex);
        int questionNumber = currentQuestionIndex + 1;
        int totalQuestions = questions.size();
        int percent = Math.round((questionNumber * 100f) / totalQuestions);

        binding.tvQuestionCounter.setText(
                getString(R.string.prime_survey_question_progress, questionNumber, totalQuestions)
        );
        binding.tvProgressPercent.setText(percent + "%");
        binding.surveyProgressBar.setMax(totalQuestions);
        binding.surveyProgressBar.setProgressCompat(questionNumber, true);

        binding.tvQuestionCategory.setText(question.getCategory());
        binding.tvQuestionText.setText(question.getQuestionText());

        hasAnsweredCurrent = false;
        selectedOptionIndex = -1;
        isWaitingForInterstitialAd = false;
        isInterstitialRetryAvailable = false;
        awaitingAdForQuestionNumber = -1;
        binding.feedbackBanner.setVisibility(View.GONE);
        binding.adStatusContainer.setVisibility(View.GONE);
        binding.btnRetryAd.setVisibility(View.GONE);
        setNextButtonEnabled(false);

        List<String> options = question.getOptions();
        for (int i = 0; i < optionCards.length; i++) {
            optionTextViews[i].setText(options.get(i));
            optionCards[i].setBackgroundResource(R.drawable.bg_survey_option_default);
            optionCards[i].setClickable(true);
            optionCards[i].setEnabled(true);
            optionStatusIcons[i].setVisibility(View.GONE);
        }

        if (PrimeSurveyAdSchedule.shouldPreloadInterstitialForQuestion(questionNumber)) {
            adManager.preloadInterstitial();
        }
        if (questionNumber >= 13) {
            adManager.preloadRewardedAd();
        }

        binding.quizContainer.setAlpha(0f);
        binding.quizContainer.setTranslationY(dpToPx(10));
        binding.quizContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(240L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void onOptionSelected(int optionIdx) {
        if (hasAnsweredCurrent || isCompleted) {
            return;
        }
        hasAnsweredCurrent = true;
        selectedOptionIndex = optionIdx;

        PrimeSurveyQuestion question = questions.get(currentQuestionIndex);
        boolean isCorrect = question.isCorrectSelection(optionIdx);
        if (isCorrect) {
            correctAnswerCount++;
        }

        repository.markQuestionAnsweredToday(question.getId());

        int correctIndex = question.getCorrectOptionIndex();
        for (int i = 0; i < optionCards.length; i++) {
            optionCards[i].setClickable(false);
            if (i == correctIndex) {
                optionCards[i].setBackgroundResource(R.drawable.bg_survey_option_correct);
                optionStatusIcons[i].setImageResource(R.drawable.ic_check_circle);
                optionStatusIcons[i].setVisibility(View.VISIBLE);
            } else if (i == optionIdx) {
                optionCards[i].setBackgroundResource(R.drawable.bg_survey_option_wrong);
                optionStatusIcons[i].setImageResource(R.drawable.ic_close_circle);
                optionStatusIcons[i].setVisibility(View.VISIBLE);
            }
        }

        binding.feedbackBanner.setVisibility(View.VISIBLE);
        if (isCorrect) {
            binding.tvFeedbackHeading.setText(R.string.prime_survey_feedback_correct);
            binding.tvFeedbackHeading.setTextColor(
                    ContextCompat.getColor(this, R.color.emerald_bright)
            );
        } else {
            binding.tvFeedbackHeading.setText(R.string.prime_survey_feedback_incorrect);
            binding.tvFeedbackHeading.setTextColor(
                    ContextCompat.getColor(this, R.color.auth_error)
            );
        }
        binding.tvFeedbackDetail.setText(question.getExplanation());

        setNextButtonEnabled(true);
    }

    private void setNextButtonEnabled(boolean enabled) {
        binding.btnNextQuestion.setEnabled(enabled);
        binding.btnNextQuestion.setClickable(enabled);
        binding.btnNextQuestion.setAlpha(enabled ? 1.0f : 0.45f);
    }

    private void onNextClicked() {
        int completedQuestionNumber = currentQuestionIndex + 1;
        if (completedQuestionNumber >= questions.size()) {
            // Question 15 completion: NO automatic interstitial ad
            showCompletionScreen();
            return;
        }

        if (PrimeSurveyAdSchedule.shouldShowInterstitialAfterQuestion(completedQuestionNumber)
                && !adManager.wasInterstitialDisplayedForQuestion(completedQuestionNumber)) {
            if (isWaitingForInterstitialAd || isInterstitialRetryAvailable) {
                return;
            }
            awaitingAdForQuestionNumber = completedQuestionNumber;
            showInterstitialLoadingUi();
            adManager.showOrLoadInterstitialForQuestion(
                    this,
                    completedQuestionNumber,
                    createInterstitialFlowListener()
            );
        } else {
            advanceToNextQuestion();
        }
    }

    @NonNull
    private PrimeSurveyAdManager.InterstitialFlowListener createInterstitialFlowListener() {
        return new PrimeSurveyAdManager.InterstitialFlowListener() {
            @Override
            public void onAdLoading() {
                if (isFinishing() || isDestroyed() || binding == null) {
                    return;
                }
                showInterstitialLoadingUi();
            }

            @Override
            public void onAdDismissed() {
                if (isFinishing() || isDestroyed() || binding == null) {
                    return;
                }
                isWaitingForInterstitialAd = false;
                isInterstitialRetryAvailable = false;
                awaitingAdForQuestionNumber = -1;
                binding.adStatusContainer.setVisibility(View.GONE);
                binding.btnRetryAd.setVisibility(View.GONE);
                setNextButtonEnabled(true);
            }

            @Override
            public void onAdLoadFailed(@NonNull String errorMessage, int attemptCount) {
                if (isFinishing() || isDestroyed() || binding == null) {
                    return;
                }
                showInterstitialFailureUi();
            }
        };
    }

    private void showInterstitialLoadingUi() {
        isWaitingForInterstitialAd = true;
        isInterstitialRetryAvailable = false;
        setNextButtonEnabled(false);
        binding.adStatusContainer.setVisibility(View.VISIBLE);
        binding.adLoadingSpinner.setVisibility(View.VISIBLE);
        binding.btnRetryAd.setVisibility(View.GONE);
        binding.tvAdStatusMessage.setGravity(android.view.Gravity.CENTER);
        binding.tvAdStatusMessage.setText(R.string.prime_survey_loading_advertisement);
        binding.tvAdStatusMessage.setTextColor(ContextCompat.getColor(this, R.color.gold_primary));
    }

    private void showInterstitialFailureUi() {
        isWaitingForInterstitialAd = false;
        isInterstitialRetryAvailable = true;
        setNextButtonEnabled(false);
        binding.adStatusContainer.setVisibility(View.VISIBLE);
        binding.adLoadingSpinner.setVisibility(View.GONE);
        binding.btnRetryAd.setVisibility(View.VISIBLE);
        binding.tvAdStatusMessage.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        binding.tvAdStatusMessage.setText(R.string.prime_survey_ad_unavailable_compact);
        binding.tvAdStatusMessage.setTextColor(ContextCompat.getColor(this, R.color.gold_bright));
    }

    private void onRetryInterstitialClicked() {
        int questionNumber = awaitingAdForQuestionNumber;
        if (questionNumber <= 0) {
            return;
        }
        showInterstitialLoadingUi();
        adManager.retryInterstitialForQuestion(
                this,
                questionNumber,
                createInterstitialFlowListener()
        );
    }

    private void advanceToNextQuestion() {
        if (currentQuestionIndex + 1 < questions.size()) {
            currentQuestionIndex++;
            renderCurrentQuestion();
            binding.primeSurveyScroll.smoothScrollTo(0, 0);
        } else {
            showCompletionScreen();
        }
    }

    private void showCompletionScreen() {
        isCompleted = true;
        isWaitingForInterstitialAd = false;
        isInterstitialRetryAvailable = false;
        awaitingAdForQuestionNumber = -1;
        repository.resetDailyAnsweredSessionIfCompleted();
        adManager.preloadRewardedAd();

        binding.adStatusContainer.setVisibility(View.GONE);
        binding.quizContainer.setVisibility(View.GONE);
        binding.completionContainer.setVisibility(View.VISIBLE);
        if (adManager.isTestAdMode()) {
            binding.tvAdModeBadge.setVisibility(View.VISIBLE);
        }
        binding.tvScoreSummary.setText(
                getString(R.string.prime_survey_score_summary, correctAnswerCount, questions.size())
        );

        binding.completionContainer.setAlpha(0f);
        binding.completionContainer.setTranslationY(dpToPx(14));
        binding.completionContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(320L)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        startClaimGlowAnimation();

        // Render cached cooldown immediately, then verify against authoritative server/Firebase time
        applyEligibilityUi(repository.getCachedEligibilityState());
        refreshClaimEligibility();
    }

    private void refreshClaimEligibility() {
        repository.verifyClaimEligibility(state -> runOnUiThread(() -> {
            if (binding != null && isCompleted && !isClaimFlowBusy) {
                applyEligibilityUi(state);
            }
        }));
    }

    private void applyEligibilityUi(@NonNull PrimeSurveyRepository.EligibilityState state) {
        stopCooldownTicker();

        if (hasClaimedInThisView) {
            binding.tvRewardSuccess.setVisibility(View.VISIBLE);
        }

        if (state.canClaim) {
            binding.cooldownContainer.setVisibility(View.GONE);
            binding.tvClaimStatus.setVisibility(View.GONE);
            setClaimButtonState(true, false, getString(R.string.prime_survey_claim_coins));
            return;
        }

        if (state.remainingCooldownMs > 0L) {
            binding.cooldownContainer.setVisibility(View.VISIBLE);
            activeRemainingCooldownMs = state.remainingCooldownMs;
            lastTickElapsedMs = SystemClock.elapsedRealtime();
            updateCooldownLabel(activeRemainingCooldownMs);
            cooldownHandler.postDelayed(cooldownTicker, COUNTDOWN_TICK_MS);
            setClaimButtonState(false, false, getString(R.string.prime_survey_claim_coins));
            if (state.requiresNetworkVerification) {
                binding.tvClaimStatus.setText(R.string.prime_survey_time_verification_failed);
                binding.tvClaimStatus.setVisibility(View.VISIBLE);
            } else {
                binding.tvClaimStatus.setVisibility(View.GONE);
            }
            return;
        }

        if (state.requiresNetworkVerification) {
            binding.cooldownContainer.setVisibility(View.GONE);
            binding.tvClaimStatus.setText(R.string.prime_survey_time_verification_failed);
            binding.tvClaimStatus.setVisibility(View.VISIBLE);
            setClaimButtonState(true, false, getString(R.string.prime_survey_claim_coins));
        }
    }

    private void updateCooldownLabel(long remainingMs) {
        if (binding == null) {
            return;
        }
        String formatted = PrimeSurveyRepository.formatRemainingHms(remainingMs);
        binding.tvCooldownTimer.setText(
                getString(R.string.prime_survey_cooldown_remaining, formatted)
        );
    }

    private void setClaimButtonState(boolean enabled, boolean loading, @NonNull String label) {
        binding.btnClaimCoins.setEnabled(enabled && !loading);
        binding.btnClaimCoins.setClickable(enabled && !loading);
        binding.btnClaimCoins.setAlpha((enabled || loading) ? 1.0f : 0.45f);
        binding.claimProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnClaimCoinsText.setText(label);
        binding.btnClaimGlow.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
    }

    private void onClaimCoinsClicked() {
        if (isClaimFlowBusy || repository.isClaimInFlight()) {
            return;
        }

        isClaimFlowBusy = true;
        binding.tvClaimStatus.setVisibility(View.GONE);
        setClaimButtonState(false, true, getString(R.string.prime_survey_ad_loading));

        repository.verifyClaimEligibility(state -> runOnUiThread(() -> {
            if (binding == null || isFinishing() || isDestroyed()) {
                isClaimFlowBusy = false;
                return;
            }

            if (!state.canClaim) {
                isClaimFlowBusy = false;
                applyEligibilityUi(state);
                if (state.requiresNetworkVerification && state.remainingCooldownMs == 0L) {
                    binding.tvClaimStatus.setText(R.string.prime_survey_time_verification_failed);
                    binding.tvClaimStatus.setVisibility(View.VISIBLE);
                } else {
                    binding.tvClaimStatus.setText(R.string.prime_survey_already_claimed);
                    binding.tvClaimStatus.setVisibility(View.VISIBLE);
                }
                return;
            }

            final String transactionId = repository.generateTransactionId();
            adManager.loadAndShowRewardedAd(this, new PrimeSurveyAdManager.RewardedAdOutcomeListener() {
                @Override
                public void onLoadingStarted() {
                    runOnUiThread(() -> {
                        if (binding != null) {
                            setClaimButtonState(false, true, getString(R.string.prime_survey_ad_loading));
                        }
                    });
                }

                @Override
                public void onRewardVerified() {
                    repository.commitVerifiedReward(true, transactionId, (success, message, updatedState) ->
                            runOnUiThread(() -> {
                                isClaimFlowBusy = false;
                                if (binding == null) {
                                    return;
                                }
                                if (success) {
                                    hasClaimedInThisView = true;
                                    binding.tvRewardSuccess.setText(R.string.prime_survey_coins_added);
                                    binding.tvRewardSuccess.setVisibility(View.VISIBLE);
                                    binding.tvClaimStatus.setVisibility(View.GONE);
                                } else {
                                    binding.tvClaimStatus.setText(message);
                                    binding.tvClaimStatus.setVisibility(View.VISIBLE);
                                }
                                applyEligibilityUi(updatedState);
                            })
                    );
                }

                @Override
                public void onRewardNotEarned() {
                    runOnUiThread(() -> {
                        isClaimFlowBusy = false;
                        if (binding != null) {
                            binding.tvClaimStatus.setText(R.string.prime_survey_ad_not_completed);
                            binding.tvClaimStatus.setVisibility(View.VISIBLE);
                            setClaimButtonState(true, false, getString(R.string.prime_survey_claim_coins));
                        }
                    });
                }

                @Override
                public void onAdUnavailableOrFailed(@NonNull String reason) {
                    runOnUiThread(() -> {
                        isClaimFlowBusy = false;
                        if (binding != null) {
                            binding.tvClaimStatus.setText(R.string.prime_survey_ad_unavailable);
                            binding.tvClaimStatus.setVisibility(View.VISIBLE);
                            setClaimButtonState(true, false, getString(R.string.prime_survey_retry_rewarded));
                        }
                    });
                }
            });
        }));
    }

    private void startClaimGlowAnimation() {
        if (claimGlowAnimatorSet != null) {
            return;
        }
        ObjectAnimator alpha = ObjectAnimator.ofFloat(binding.btnClaimGlow, "alpha", 0.45f, 0.92f);
        alpha.setRepeatCount(ValueAnimator.INFINITE);
        alpha.setRepeatMode(ValueAnimator.REVERSE);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(binding.btnClaimGlow, "scaleY", 1.12f, 1.26f);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatMode(ValueAnimator.REVERSE);

        claimGlowAnimatorSet = new AnimatorSet();
        claimGlowAnimatorSet.playTogether(alpha, scaleY);
        claimGlowAnimatorSet.setDuration(1200L);
        claimGlowAnimatorSet.start();
    }

    private void stopCooldownTicker() {
        cooldownHandler.removeCallbacks(cooldownTicker);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isCompleted && !isClaimFlowBusy) {
            refreshClaimEligibility();
        }
    }

    @Override
    protected void onPause() {
        stopCooldownTicker();
        super.onPause();
    }

    @VisibleForTesting
    PrimeSurveyAdManager getAdManagerForTest() {
        return adManager;
    }

    @Override
    protected void onDestroy() {
        stopCooldownTicker();
        if (claimGlowAnimatorSet != null) {
            claimGlowAnimatorSet.cancel();
            claimGlowAnimatorSet = null;
        }
        if (adManager != null) {
            adManager.destroy();
        }
        binding = null;
        super.onDestroy();
    }
}

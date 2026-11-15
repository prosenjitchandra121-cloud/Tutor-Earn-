package com.tutor.earn.pro;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
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
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.tutor.earn.pro.databinding.ActivityHomeBinding;
import com.google.android.material.card.MaterialCardView;

/**
 * Home Screen for Tutor Earn.
 * Displays the branded header, the top centralized golden Coin Balance card,
 * the four glassmorphic gradient feature cards (Prime Survey, Survey Rewards,
 * Read and Rewards, Earn With AI), and a continuous 3-slide promotional banner carousel.
 */
public class HomeActivity extends AppCompatActivity {

    private static final long CAROUSEL_AUTO_SLIDE_MS = 3000L;

    private ActivityHomeBinding binding;
    private CoinBalanceRepository coinBalanceRepository;
    private long lastRenderedBalance = -1L;

    private AnimatorSet coinAmbientAnimatorSet;
    private AnimatorSet coinCelebrateAnimatorSet;

    private int basePaddingHorizontal;
    private int basePaddingTop;
    private int basePaddingBottom;

    private final Handler carouselHandler = new Handler(Looper.getMainLooper());
    private View[] paginationDots;
    private ViewPager2.OnPageChangeCallback pageChangeCallback;
    private final CoinBalanceRepository.BalanceListener coinBalanceListener =
            this::updateCoinBalanceDisplay;
    private final Runnable autoSlideRunnable = new Runnable() {
        @Override
        public void run() {
            if (binding == null) {
                return;
            }
            int nextItem = binding.promoViewPager.getCurrentItem() + 1;
            binding.promoViewPager.setCurrentItem(nextItem, true);
            carouselHandler.postDelayed(this, CAROUSEL_AUTO_SLIDE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TutorEarnApplication.STARTUP_LOG_TAG, "Stage 7: HomeActivity.onCreate() started");
        EdgeToEdge.enable(
                this,
                SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.dark(Color.TRANSPARENT)
        );
        super.onCreate(savedInstanceState);

        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        coinBalanceRepository = CoinBalanceRepository.getInstance(this);

        basePaddingHorizontal = getResources().getDimensionPixelSize(R.dimen.screen_horizontal_padding);
        basePaddingTop = dpToPx(12);
        basePaddingBottom = dpToPx(18);

        applySystemBarInsets();
        styleSubtitleDots();
        setupCoinBalanceCard();
        setupFeatureCards();
        setupPromoCarousel();
        animateEntrance();
        binding.getRoot().post(() -> Log.i(
                TutorEarnApplication.STARTUP_LOG_TAG,
                "Stage 8: HomeActivity main screen visible"
        ));
    }

    private void applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.homeContentWrapper, (view, windowInsets) -> {
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
        binding.homeSubtitle.setText(spannable);
    }

    private void setupCoinBalanceCard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int glowColor = ContextCompat.getColor(this, R.color.earn_ai_glow);
            binding.cardCoinBalance.setOutlineSpotShadowColor(glowColor);
            binding.cardCoinBalance.setOutlineAmbientShadowColor(glowColor);
        }
        long initialBalance = coinBalanceRepository.getCurrentBalance();
        updateCoinBalanceDisplay(initialBalance);
        startCoinAmbientAnimations();
    }

    private void updateCoinBalanceDisplay(long newBalance) {
        if (binding == null) {
            return;
        }
        long safeBalance = Math.max(0L, newBalance);
        binding.tvCoinBalanceValue.setText(CoinBalanceRepository.formatCoins(this, safeBalance));
        if (lastRenderedBalance >= 0L && safeBalance > lastRenderedBalance) {
            playCoinIncreaseAnimation();
        }
        lastRenderedBalance = safeBalance;
    }

    private void startCoinAmbientAnimations() {
        if (binding == null || coinAmbientAnimatorSet != null) {
            return;
        }
        ObjectAnimator glowAlpha = ObjectAnimator.ofFloat(binding.ivCoinBalanceGlow, "alpha", 0.45f, 0.95f);
        glowAlpha.setRepeatCount(ValueAnimator.INFINITE);
        glowAlpha.setRepeatMode(ValueAnimator.REVERSE);

        ObjectAnimator glowScaleX = ObjectAnimator.ofFloat(binding.ivCoinBalanceGlow, "scaleX", 0.92f, 1.12f);
        glowScaleX.setRepeatCount(ValueAnimator.INFINITE);
        glowScaleX.setRepeatMode(ValueAnimator.REVERSE);

        ObjectAnimator glowScaleY = ObjectAnimator.ofFloat(binding.ivCoinBalanceGlow, "scaleY", 0.92f, 1.12f);
        glowScaleY.setRepeatCount(ValueAnimator.INFINITE);
        glowScaleY.setRepeatMode(ValueAnimator.REVERSE);

        ObjectAnimator coinScaleX = ObjectAnimator.ofFloat(binding.ivCoinBalanceIcon, "scaleX", 1.0f, 1.06f);
        coinScaleX.setRepeatCount(ValueAnimator.INFINITE);
        coinScaleX.setRepeatMode(ValueAnimator.REVERSE);

        ObjectAnimator coinScaleY = ObjectAnimator.ofFloat(binding.ivCoinBalanceIcon, "scaleY", 1.0f, 1.06f);
        coinScaleY.setRepeatCount(ValueAnimator.INFINITE);
        coinScaleY.setRepeatMode(ValueAnimator.REVERSE);

        ObjectAnimator coinTilt = ObjectAnimator.ofFloat(binding.ivCoinBalanceIcon, "rotationY", -8f, 8f);
        coinTilt.setRepeatCount(ValueAnimator.INFINITE);
        coinTilt.setRepeatMode(ValueAnimator.REVERSE);

        coinAmbientAnimatorSet = new AnimatorSet();
        coinAmbientAnimatorSet.playTogether(glowAlpha, glowScaleX, glowScaleY, coinScaleX, coinScaleY, coinTilt);
        coinAmbientAnimatorSet.setDuration(1500L);
        coinAmbientAnimatorSet.start();
    }

    private void playCoinIncreaseAnimation() {
        if (binding == null) {
            return;
        }
        if (coinCelebrateAnimatorSet != null) {
            coinCelebrateAnimatorSet.cancel();
        }
        ObjectAnimator iconSpin = ObjectAnimator.ofFloat(binding.ivCoinBalanceIcon, "rotationY", 0f, 360f);
        ObjectAnimator cardScaleX = ObjectAnimator.ofFloat(binding.cardCoinBalance, "scaleX", 1.0f, 1.03f, 1.0f);
        ObjectAnimator cardScaleY = ObjectAnimator.ofFloat(binding.cardCoinBalance, "scaleY", 1.0f, 1.03f, 1.0f);

        coinCelebrateAnimatorSet = new AnimatorSet();
        coinCelebrateAnimatorSet.playTogether(iconSpin, cardScaleX, cardScaleY);
        coinCelebrateAnimatorSet.setDuration(550L);
        coinCelebrateAnimatorSet.setInterpolator(new OvershootInterpolator(1.3f));
        coinCelebrateAnimatorSet.start();
    }

    private void setupFeatureCards() {
        configureCard(binding.cardPrimeSurvey, R.color.prime_survey_glow);
        configureCard(binding.cardSurveyRewards, R.color.survey_rewards_glow);
        configureCard(binding.cardReadAndRewards, R.color.read_rewards_glow);
        configureCard(binding.cardEarnWithAi, R.color.earn_ai_glow);

        binding.cardPrimeSurvey.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            openPrimeSurvey();
        });

        binding.cardSurveyRewards.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            openSurveyRewards();
        });
    }

    private void openPrimeSurvey() {
        Intent intent = new Intent(HomeActivity.this, PrimeSurveyActivity.class);
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

    private void openSurveyRewards() {
        Intent intent = new Intent(HomeActivity.this, SurveyRewardsActivity.class);
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

    @SuppressWarnings("deprecation")
    private void applyLegacyTransition() {
        overridePendingTransition(R.anim.fade_in_smooth, R.anim.fade_out_smooth);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void configureCard(MaterialCardView card, int glowColorRes) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int glowColor = ContextCompat.getColor(this, glowColorRes);
            card.setOutlineSpotShadowColor(glowColor);
            card.setOutlineAmbientShadowColor(glowColor);
        }

        card.setOnTouchListener((view, event) -> {
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
                            .setDuration(190L)
                            .setInterpolator(new OvershootInterpolator(1.7f))
                            .start();
                    break;
            }
            return false;
        });

        card.setOnClickListener(view ->
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        );
    }

    private void setupPromoCarousel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int glowColor = ContextCompat.getColor(this, R.color.prime_survey_glow);
            binding.promoCarouselCard.setOutlineSpotShadowColor(glowColor);
            binding.promoCarouselCard.setOutlineAmbientShadowColor(glowColor);
        }

        paginationDots = new View[]{
                binding.promoDot0,
                binding.promoDot1,
                binding.promoDot2
        };

        PromoSlideAdapter adapter = new PromoSlideAdapter();
        binding.promoViewPager.setAdapter(adapter);
        binding.promoViewPager.setOffscreenPageLimit(1);
        binding.promoViewPager.setCurrentItem(PromoSlideAdapter.START_POSITION, false);

        // Smooth subtle fade & scale transition during horizontal swipe/slide
        binding.promoViewPager.setPageTransformer((page, position) -> {
            float absPos = Math.abs(position);
            page.setAlpha(Math.max(0.35f, 1f - absPos * 0.55f));
            float scale = 1f - Math.min(absPos, 1f) * 0.05f;
            page.setScaleX(scale);
            page.setScaleY(scale);
        });

        pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                int activeIndex = ((position % PromoSlideAdapter.SLIDE_COUNT) + PromoSlideAdapter.SLIDE_COUNT)
                        % PromoSlideAdapter.SLIDE_COUNT;
                updatePaginationDots(activeIndex);
                restartAutoSlideTimer();
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    stopAutoSlideTimer();
                } else if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    restartAutoSlideTimer();
                }
            }
        };
        binding.promoViewPager.registerOnPageChangeCallback(pageChangeCallback);

        binding.promoBtnPrev.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            int current = binding.promoViewPager.getCurrentItem();
            binding.promoViewPager.setCurrentItem(current - 1, true);
        });

        binding.promoBtnNext.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            int current = binding.promoViewPager.getCurrentItem();
            binding.promoViewPager.setCurrentItem(current + 1, true);
        });

        updatePaginationDots(0);
    }

    private void updatePaginationDots(int activeIndex) {
        if (paginationDots == null) {
            return;
        }
        int activeWidth = dpToPx(20);
        int inactiveWidth = dpToPx(7);
        int dotHeight = dpToPx(7);

        for (int i = 0; i < paginationDots.length; i++) {
            View dot = paginationDots[i];
            boolean isActive = (i == activeIndex);
            dot.setBackgroundResource(isActive ? R.drawable.bg_promo_dot_active : R.drawable.bg_promo_dot_inactive);
            ViewGroup.LayoutParams params = dot.getLayoutParams();
            int targetWidth = isActive ? activeWidth : inactiveWidth;
            if (params.width != targetWidth || params.height != dotHeight) {
                params.width = targetWidth;
                params.height = dotHeight;
                dot.setLayoutParams(params);
            }
            dot.setAlpha(isActive ? 1f : 0.72f);
        }
    }

    private void startAutoSlideTimer() {
        carouselHandler.removeCallbacks(autoSlideRunnable);
        carouselHandler.postDelayed(autoSlideRunnable, CAROUSEL_AUTO_SLIDE_MS);
    }

    private void restartAutoSlideTimer() {
        startAutoSlideTimer();
    }

    private void stopAutoSlideTimer() {
        carouselHandler.removeCallbacks(autoSlideRunnable);
    }

    private void animateEntrance() {
        View[] animatedViews = new View[]{
                binding.homeHeader,
                binding.coinBalanceWrapper,
                binding.cardPrimeSurvey,
                binding.cardSurveyRewards,
                binding.cardReadAndRewards,
                binding.cardEarnWithAi,
                binding.promoCarouselCard
        };

        float initialOffset = dpToPx(16);
        DecelerateInterpolator interpolator = new DecelerateInterpolator();

        for (int i = 0; i < animatedViews.length; i++) {
            View target = animatedViews[i];
            target.setAlpha(0f);
            target.setTranslationY(initialOffset);
            target.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * 55L)
                    .setDuration(400L)
                    .setInterpolator(interpolator)
                    .start();
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (coinBalanceRepository != null) {
            coinBalanceRepository.addListener(coinBalanceListener);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TutorEarnApplication.STARTUP_LOG_TAG, "Stage 8: HomeActivity.onResume() active");
        startAutoSlideTimer();
        if (coinBalanceRepository != null) {
            coinBalanceRepository.refreshBalance();
        }
        if (coinAmbientAnimatorSet != null && coinAmbientAnimatorSet.isPaused()) {
            coinAmbientAnimatorSet.resume();
        }
    }

    @Override
    protected void onPause() {
        stopAutoSlideTimer();
        if (coinAmbientAnimatorSet != null && coinAmbientAnimatorSet.isRunning()) {
            coinAmbientAnimatorSet.pause();
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (coinBalanceRepository != null) {
            coinBalanceRepository.removeListener(coinBalanceListener);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopAutoSlideTimer();
        if (coinBalanceRepository != null) {
            coinBalanceRepository.removeListener(coinBalanceListener);
        }
        if (coinAmbientAnimatorSet != null) {
            coinAmbientAnimatorSet.cancel();
            coinAmbientAnimatorSet = null;
        }
        if (coinCelebrateAnimatorSet != null) {
            coinCelebrateAnimatorSet.cancel();
            coinCelebrateAnimatorSet = null;
        }
        if (binding != null && pageChangeCallback != null) {
            binding.promoViewPager.unregisterOnPageChangeCallback(pageChangeCallback);
        }
        paginationDots = null;
        binding = null;
        super.onDestroy();
    }
}

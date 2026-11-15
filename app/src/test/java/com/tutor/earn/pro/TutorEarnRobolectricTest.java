package com.tutor.earn.pro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.time.Duration;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TutorEarnRobolectricTest {

    @Before
    public void setUpAuthOverride() {
        CoinBalanceRepository.Holder.setDriverOverride(null);
        CoinBalanceRepository.resetForTest(ApplicationProvider.getApplicationContext());
        AuthGateway.Holder.setTestOverride(new AuthGateway() {
            @Override
            public void signIn(
                    @androidx.annotation.NonNull String email,
                    @androidx.annotation.NonNull String password,
                    @androidx.annotation.NonNull AuthCallback callback
            ) {
                callback.onSuccess("test_uid_001");
            }

            @Override
            public void signUp(
                    @androidx.annotation.NonNull String fullName,
                    @androidx.annotation.NonNull String email,
                    @androidx.annotation.NonNull String password,
                    @androidx.annotation.NonNull AuthCallback callback
            ) {
                callback.onSuccess("test_uid_001");
            }

            @Override
            public boolean isUserSignedIn() {
                return false;
            }
        });
    }

    @After
    public void tearDownOverrides() {
        AuthGateway.Holder.setTestOverride(null);
        PrimeSurveyAdManager.Holder.setInterstitialDriverOverride(null);
        PrimeSurveyAdManager.Holder.setRewardedDriverOverride(null);
        SurveyRewardsAdManager.Holder.setRewardedDriverOverride(null);
        SurveyRewardsRepository.Holder.setTimeProviderOverride(null);
        SurveyRewardsRepository.Holder.setBackendOverride(null);
        TutorEarnApplication.AdInitHolder.setDriverOverride(null);
        CoinBalanceRepository.Holder.setDriverOverride(null);
        CoinBalanceRepository.resetForTest(ApplicationProvider.getApplicationContext());
        ApplicationProvider.getApplicationContext()
                .getSharedPreferences("tutor_earn_survey_rewards_ledger", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void appNameIsTutorEarn() {
        Context context = ApplicationProvider.getApplicationContext();
        assertEquals("Tutor Earn", context.getString(R.string.app_name));
    }

    @Test
    public void launcherActivityIsWelcomeActivity() {
        Context context = ApplicationProvider.getApplicationContext();
        PackageManager pm = context.getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        launcherIntent.setPackage(context.getPackageName());

        ResolveInfo resolveInfo = pm.resolveActivity(launcherIntent, 0);
        assertNotNull(resolveInfo);
        assertEquals(WelcomeActivity.class.getName(), resolveInfo.activityInfo.name);
    }

    @Test
    public void welcomeScreenDisplaysRequiredElementsAndNavigatesOnGetStarted() {
        WelcomeActivity activity = Robolectric.buildActivity(WelcomeActivity.class)
                .create()
                .start()
                .resume()
                .get();

        TextView welcomeHeadline = activity.findViewById(R.id.welcome_headline_badge);
        TextView tutorText = activity.findViewById(R.id.welcome_title_tutor);
        TextView earnText = activity.findViewById(R.id.welcome_title_earn);
        TextView subtitleText = activity.findViewById(R.id.welcome_subtitle);
        TextView getStartedText = activity.findViewById(R.id.btn_get_started_text);
        View getStartedButton = activity.findViewById(R.id.btn_get_started);

        assertEquals("Welcome to Tutor Earn", welcomeHeadline.getText().toString());
        assertEquals("Tutor", tutorText.getText().toString());
        assertEquals("Earn", earnText.getText().toString());
        assertEquals("Learn • Complete • Earn", subtitleText.getText().toString());
        assertEquals("Get Started", getStartedText.getText().toString());

        // Ensure Welcome screen does not auto-skip without tapping Get Started
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3000));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());

        // Tapping Get Started navigates to LoginActivity
        getStartedButton.performClick();
        Intent nextIntent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(nextIntent);
        assertEquals(LoginActivity.class.getName(), nextIntent.getComponent().getClassName());
    }

    @Test
    public void loginScreenValidatesInputsAndNavigates() {
        LoginActivity activity = Robolectric.buildActivity(LoginActivity.class)
                .create()
                .start()
                .resume()
                .get();

        TextView heading = activity.findViewById(R.id.login_heading);
        TextView subtitle = activity.findViewById(R.id.login_subtitle);
        com.google.android.material.textfield.TextInputLayout tilEmail = activity.findViewById(R.id.til_login_email);
        com.google.android.material.textfield.TextInputLayout tilPassword = activity.findViewById(R.id.til_login_password);
        android.widget.EditText etEmail = activity.findViewById(R.id.et_login_email);
        android.widget.EditText etPassword = activity.findViewById(R.id.et_login_password);
        View loginBtn = activity.findViewById(R.id.btn_login);
        View goToSignup = activity.findViewById(R.id.tv_go_to_signup);

        assertEquals("Welcome Back!", heading.getText().toString());
        assertEquals("Login to continue your journey.", subtitle.getText().toString());

        // Empty submission is blocked
        loginBtn.performClick();
        assertNotNull(tilEmail.getError());
        assertNotNull(tilPassword.getError());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());

        // Invalid email is blocked
        etEmail.setText("invalid-email");
        etPassword.setText("123456");
        loginBtn.performClick();
        assertEquals("Please enter a valid email address.", tilEmail.getError().toString());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());

        // Switch to SignUpActivity works
        goToSignup.performClick();
        Intent signupIntent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(signupIntent);
        assertEquals(SignUpActivity.class.getName(), signupIntent.getComponent().getClassName());

        // Valid input navigates to HomeActivity
        etEmail.setText("student@tutorearn.com");
        etPassword.setText("secret123");
        loginBtn.performClick();
        Intent homeIntent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(homeIntent);
        assertEquals(HomeActivity.class.getName(), homeIntent.getComponent().getClassName());
    }

    @Test
    public void signUpScreenValidatesInputsAndNavigates() {
        SignUpActivity activity = Robolectric.buildActivity(SignUpActivity.class)
                .create()
                .start()
                .resume()
                .get();

        TextView heading = activity.findViewById(R.id.signup_heading);
        TextView subtitle = activity.findViewById(R.id.signup_subtitle);
        com.google.android.material.textfield.TextInputLayout tilName = activity.findViewById(R.id.til_signup_name);
        com.google.android.material.textfield.TextInputLayout tilEmail = activity.findViewById(R.id.til_signup_email);
        com.google.android.material.textfield.TextInputLayout tilPassword = activity.findViewById(R.id.til_signup_password);
        com.google.android.material.textfield.TextInputLayout tilConfirm = activity.findViewById(R.id.til_signup_confirm_password);
        android.widget.EditText etName = activity.findViewById(R.id.et_signup_name);
        android.widget.EditText etEmail = activity.findViewById(R.id.et_signup_email);
        android.widget.EditText etPassword = activity.findViewById(R.id.et_signup_password);
        android.widget.EditText etConfirm = activity.findViewById(R.id.et_signup_confirm_password);
        View createAccountBtn = activity.findViewById(R.id.btn_create_account);
        View goToLogin = activity.findViewById(R.id.tv_go_to_login);

        assertEquals("Create Account", heading.getText().toString());
        assertEquals("Join Tutor Earn and start your journey.", subtitle.getText().toString());

        // Empty submission is blocked
        createAccountBtn.performClick();
        assertNotNull(tilName.getError());
        assertNotNull(tilEmail.getError());
        assertNotNull(tilPassword.getError());
        assertNotNull(tilConfirm.getError());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());

        // Password mismatch is caught
        etName.setText("Alex Johnson");
        etEmail.setText("alex@tutorearn.com");
        etPassword.setText("secret123");
        etConfirm.setText("different");
        createAccountBtn.performClick();
        assertEquals("Passwords do not match.", tilConfirm.getError().toString());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());

        // Switch back to LoginActivity works
        goToLogin.performClick();
        Intent loginIntent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(loginIntent);
        assertEquals(LoginActivity.class.getName(), loginIntent.getComponent().getClassName());
    }

    @Test
    public void signUpValidSubmissionNavigatesToHome() {
        SignUpActivity activity = Robolectric.buildActivity(SignUpActivity.class)
                .create()
                .start()
                .resume()
                .get();

        android.widget.EditText etName = activity.findViewById(R.id.et_signup_name);
        android.widget.EditText etEmail = activity.findViewById(R.id.et_signup_email);
        android.widget.EditText etPassword = activity.findViewById(R.id.et_signup_password);
        android.widget.EditText etConfirm = activity.findViewById(R.id.et_signup_confirm_password);
        View createAccountBtn = activity.findViewById(R.id.btn_create_account);

        etName.setText("Alex Johnson");
        etEmail.setText("alex@tutorearn.com");
        etPassword.setText("secret123");
        etConfirm.setText("secret123");
        createAccountBtn.performClick();

        Intent homeIntent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(homeIntent);
        assertEquals(HomeActivity.class.getName(), homeIntent.getComponent().getClassName());
    }

    @Test
    public void homeScreenDisplaysExactFourFeatureCards() {
        HomeActivity activity = Robolectric.buildActivity(HomeActivity.class)
                .create()
                .start()
                .resume()
                .get();

        TextView primeSurvey = activity.findViewById(R.id.title_prime_survey);
        TextView surveyRewards = activity.findViewById(R.id.title_survey_rewards);
        TextView readAndRewards = activity.findViewById(R.id.title_read_and_rewards);
        TextView earnWithAi = activity.findViewById(R.id.title_earn_with_ai);

        assertEquals("Prime Survey", primeSurvey.getText().toString());
        assertEquals("Survey Rewards", surveyRewards.getText().toString());
        assertEquals("Read and Rewards", readAndRewards.getText().toString());
        assertEquals("Earn With AI", earnWithAi.getText().toString());
    }

    @Test
    public void promoCarouselDisplaysThreeSlidesAndAutoLoopsEveryThreeSeconds() {
        HomeActivity activity = Robolectric.buildActivity(HomeActivity.class)
                .create()
                .start()
                .resume()
                .get();

        androidx.viewpager2.widget.ViewPager2 viewPager = activity.findViewById(R.id.promo_view_pager);
        View dot0 = activity.findViewById(R.id.promo_dot_0);
        View dot1 = activity.findViewById(R.id.promo_dot_1);
        View dot2 = activity.findViewById(R.id.promo_dot_2);

        assertNotNull(viewPager);
        assertNotNull(dot0);
        assertNotNull(dot1);
        assertNotNull(dot2);
        assertEquals(PromoSlideAdapter.START_POSITION, viewPager.getCurrentItem());

        // Verify all 3 exact slide strings in resources
        assertEquals("Learn & Earn Smarter!", activity.getString(R.string.promo_slide_1_title));
        assertEquals("Complete tasks and earn rewards", activity.getString(R.string.promo_slide_1_subtitle));
        assertEquals("Your Time, Your Rewards!", activity.getString(R.string.promo_slide_2_title));
        assertEquals("Discover exciting survey opportunities", activity.getString(R.string.promo_slide_2_subtitle));
        assertEquals("Explore the Power of AI!", activity.getString(R.string.promo_slide_3_title));
        assertEquals("Learn something new every day", activity.getString(R.string.promo_slide_3_subtitle));

        // Verify auto-slide advances 1 -> 2 -> 3 -> 1 continuously every 3 seconds
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3050));
        assertEquals(PromoSlideAdapter.START_POSITION + 1, viewPager.getCurrentItem());
        assertEquals(1, viewPager.getCurrentItem() % PromoSlideAdapter.SLIDE_COUNT);

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3050));
        assertEquals(PromoSlideAdapter.START_POSITION + 2, viewPager.getCurrentItem());
        assertEquals(2, viewPager.getCurrentItem() % PromoSlideAdapter.SLIDE_COUNT);

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3050));
        assertEquals(PromoSlideAdapter.START_POSITION + 3, viewPager.getCurrentItem());
        assertEquals(0, viewPager.getCurrentItem() % PromoSlideAdapter.SLIDE_COUNT);
    }

    @Test
    public void homePrimeSurveyCardOpensPrimeSurveyActivity() {
        HomeActivity activity = Robolectric.buildActivity(HomeActivity.class)
                .create()
                .start()
                .resume()
                .get();

        View primeSurveyCard = activity.findViewById(R.id.card_prime_survey);
        assertNotNull(primeSurveyCard);
        primeSurveyCard.performClick();

        Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(PrimeSurveyActivity.class.getName(), intent.getComponent().getClassName());
    }

    @Test
    public void primeSurveyQuestionBankHas15UniqueMcqs() {
        java.util.List<PrimeSurveyQuestion> all = PrimeSurveyQuestionBank.getAllQuestions();
        assertEquals(15, all.size());

        java.util.Set<String> seenIds = new java.util.HashSet<>();
        for (PrimeSurveyQuestion q : all) {
            org.junit.Assert.assertTrue(seenIds.add(q.getId()));
            assertEquals(4, q.getOptions().size());
            org.junit.Assert.assertTrue(q.getCorrectOptionIndex() >= 0 && q.getCorrectOptionIndex() < 4);
            org.junit.Assert.assertFalse(q.getQuestionText().trim().isEmpty());
        }

        java.util.List<PrimeSurveyQuestion> session = PrimeSurveyQuestionBank.buildSessionQuestions(
                new java.util.HashSet<>(),
                true
        );
        assertEquals(15, session.size());
        java.util.Set<String> sessionIds = new java.util.HashSet<>();
        for (PrimeSurveyQuestion q : session) {
            org.junit.Assert.assertTrue(sessionIds.add(q.getId()));
        }
    }

    @Test
    public void primeSurveyAdScheduleMatchesExactAlternatingRule() {
        int[] adQuestions = {2, 4, 6, 8, 10, 12, 14};
        int[] noAdQuestions = {1, 3, 5, 7, 9, 11, 13, 15};

        for (int q : adQuestions) {
            org.junit.Assert.assertTrue(
                    "Question " + q + " should show interstitial",
                    PrimeSurveyAdSchedule.shouldShowInterstitialAfterQuestion(q)
            );
        }
        for (int q : noAdQuestions) {
            org.junit.Assert.assertFalse(
                    "Question " + q + " must NOT show interstitial",
                    PrimeSurveyAdSchedule.shouldShowInterstitialAfterQuestion(q)
            );
        }
        org.junit.Assert.assertFalse(PrimeSurveyAdSchedule.shouldShowAutomaticAdOnCompletion());
    }

    @Test
    public void primeSurveyUsesOfficialGoogleTestAdIdsInDebugAndDistinctFormats() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        android.content.pm.ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(
                context.getPackageName(),
                PackageManager.GET_META_DATA
        );
        String manifestAppId = appInfo.metaData.getString("com.google.android.gms.ads.APPLICATION_ID");
        assertEquals("ca-app-pub-3940256099942544~3347511713", manifestAppId);
        assertEquals(
                "ca-app-pub-8972616814126419/2306700688",
                BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID
        );
        assertEquals(
                "ca-app-pub-3940256099942544/5224354917",
                BuildConfig.ADMOB_REWARDED_AD_UNIT_ID
        );
        assertEquals(
                "ca-app-pub-8972616814126419/5563489220",
                BuildConfig.ADMOB_SURVEY_REWARDS_AD_UNIT_ID
        );
        org.junit.Assert.assertTrue(BuildConfig.IS_TEST_AD_MODE);
        PrimeSurveyAdManager manager = new PrimeSurveyAdManager(context);
        SurveyRewardsAdManager surveyRewardsManager = new SurveyRewardsAdManager(context);
        org.junit.Assert.assertTrue(manager.areAdUnitIdsDistinct());
        org.junit.Assert.assertTrue(surveyRewardsManager.isUsingDistinctRewardedUnit());
        assertEquals(
                "ca-app-pub-8972616814126419/2306700688",
                manager.getInterstitialAdUnitId()
        );
        assertEquals(
                "ca-app-pub-8972616814126419/5563489220",
                surveyRewardsManager.getRewardedAdUnitId()
        );
        org.junit.Assert.assertFalse(
                manager.getInterstitialAdUnitId().equals(manager.getRewardedAdUnitId())
        );
        org.junit.Assert.assertFalse(
                surveyRewardsManager.getRewardedAdUnitId().equals(manager.getInterstitialAdUnitId())
        );
        org.junit.Assert.assertFalse(
                surveyRewardsManager.getRewardedAdUnitId().equals(manager.getRewardedAdUnitId())
        );
        manager.destroy();
        surveyRewardsManager.destroy();
    }

    @Test
    public void primeSurveyQuizFlowProgressesFrom1To15WithoutRepeats() {
        final java.util.List<String> requestedAdUnits = new java.util.ArrayList<>();
        final java.util.List<PrimeSurveyAdManager.InterstitialLoadCallback> pendingCallbacks =
                new java.util.ArrayList<>();
        final java.util.List<com.google.android.gms.ads.FullScreenContentCallback> shownCallbacks =
                new java.util.ArrayList<>();

        PrimeSurveyAdManager.Holder.setInterstitialDriverOverride(new PrimeSurveyAdManager.InterstitialDriver() {
            @Override
            public void initialize(@androidx.annotation.NonNull Context context, @androidx.annotation.NonNull Runnable onInitialized) {
                onInitialized.run();
            }

            @Override
            public void loadInterstitial(
                    @androidx.annotation.NonNull Context context,
                    @androidx.annotation.NonNull String adUnitId,
                    @androidx.annotation.NonNull PrimeSurveyAdManager.InterstitialLoadCallback callback
            ) {
                requestedAdUnits.add(adUnitId);
                pendingCallbacks.add(callback);
            }
        });

        PrimeSurveyActivity activity = Robolectric.buildActivity(PrimeSurveyActivity.class)
                .create()
                .start()
                .resume()
                .get();

        // Preload starts immediately using configured Prime Survey Interstitial Ad Unit ID
        org.junit.Assert.assertFalse(requestedAdUnits.isEmpty());
        assertEquals("ca-app-pub-8972616814126419/2306700688", requestedAdUnits.get(0));

        TextView title = activity.findViewById(R.id.tv_survey_title);
        TextView totalQuestions = activity.findViewById(R.id.tv_total_questions);
        TextView questionCounter = activity.findViewById(R.id.tv_question_counter);
        TextView questionText = activity.findViewById(R.id.tv_question_text);
        View optionA = activity.findViewById(R.id.option_card_a);
        View nextBtn = activity.findViewById(R.id.btn_next_question);
        View feedbackBanner = activity.findViewById(R.id.feedback_banner);
        View adStatusContainer = activity.findViewById(R.id.ad_status_container);
        View adLoadingSpinner = activity.findViewById(R.id.ad_loading_spinner);
        TextView adStatusMessage = activity.findViewById(R.id.tv_ad_status_message);
        View completionContainer = activity.findViewById(R.id.completion_container);

        assertEquals("Prime Survey", title.getText().toString());
        assertEquals("Total Questions: 15", totalQuestions.getText().toString());
        assertEquals(View.GONE, completionContainer.getVisibility());

        java.util.Set<String> seenQuestionTexts = new java.util.HashSet<>();
        for (int qNum = 1; qNum <= 15; qNum++) {
            assertEquals("Question " + qNum + " of 15", questionCounter.getText().toString());
            org.junit.Assert.assertTrue(
                    "Duplicate question text in daily session!",
                    seenQuestionTexts.add(questionText.getText().toString())
            );
            org.junit.Assert.assertFalse(nextBtn.isEnabled());

            optionA.performClick();
            assertEquals(View.VISIBLE, feedbackBanner.getVisibility());
            org.junit.Assert.assertTrue(nextBtn.isEnabled());

            nextBtn.performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            if (PrimeSurveyAdSchedule.shouldShowInterstitialAfterQuestion(qNum)) {
                // While required ad is still loading: Next disabled, loading message shown, still on qNum
                assertEquals("Question " + qNum + " of 15", questionCounter.getText().toString());
                org.junit.Assert.assertFalse(nextBtn.isEnabled());
                assertEquals(View.VISIBLE, adStatusContainer.getVisibility());
                assertEquals(View.VISIBLE, adLoadingSpinner.getVisibility());
                assertEquals("Loading Advertisement...", adStatusMessage.getText().toString());

                // Complete load & show interstitial
                PrimeSurveyAdManager.InterstitialLoadCallback loadCb =
                        pendingCallbacks.remove(pendingCallbacks.size() - 1);
                loadCb.onLoaded(new PrimeSurveyAdManager.LoadedInterstitialHandle() {
                    private com.google.android.gms.ads.FullScreenContentCallback fsCallback;

                    @Override
                    public void setFullScreenContentCallback(
                            @androidx.annotation.Nullable com.google.android.gms.ads.FullScreenContentCallback callback
                    ) {
                        this.fsCallback = callback;
                    }

                    @Override
                    public void show(@androidx.annotation.NonNull android.app.Activity act) {
                        shownCallbacks.add(fsCallback);
                        if (fsCallback != null) {
                            fsCallback.onAdShowedFullScreenContent();
                        }
                    }
                });
                Shadows.shadowOf(Looper.getMainLooper()).idle();

                // Ad shown -> must still stay on qNum until dismissed
                assertEquals("Question " + qNum + " of 15", questionCounter.getText().toString());
                org.junit.Assert.assertTrue(
                        activity.getAdManagerForTest().wasInterstitialDisplayedForQuestion(qNum)
                );

                // Dismiss interstitial -> hides status bar & enables Next button; tapping Next advances to qNum + 1
                com.google.android.gms.ads.FullScreenContentCallback activeFs =
                        shownCallbacks.get(shownCallbacks.size() - 1);
                activeFs.onAdDismissedFullScreenContent();
                Shadows.shadowOf(Looper.getMainLooper()).idle();

                assertEquals(View.GONE, adStatusContainer.getVisibility());
                org.junit.Assert.assertTrue(nextBtn.isEnabled());
                assertEquals("Question " + qNum + " of 15", questionCounter.getText().toString());

                nextBtn.performClick();
                Shadows.shadowOf(Looper.getMainLooper()).idle();
            }
        }

        assertEquals(7, activity.getAdManagerForTest().getDisplayedInterstitialCount());
        assertEquals(View.VISIBLE, completionContainer.getVisibility());
        TextView completionTitle = activity.findViewById(R.id.tv_completion_title);
        TextView completionCongrats = activity.findViewById(R.id.tv_completion_congrats);
        TextView claimBtnText = activity.findViewById(R.id.btn_claim_coins_text);

        assertEquals("Prime Survey Completed!", completionTitle.getText().toString());
        assertEquals("Congratulations!", completionCongrats.getText().toString());
        assertEquals("CLAIM 1050 COINS", claimBtnText.getText().toString());
    }

    @Test
    public void primeSurveyInterstitialLoadFailureShowsCompactRetryWithoutBypassAndNeverFakesDisplay() {
        final java.util.List<PrimeSurveyAdManager.InterstitialLoadCallback> callbacks =
                new java.util.ArrayList<>();
        final int[] showInvocations = {0};

        PrimeSurveyAdManager.Holder.setInterstitialDriverOverride(new PrimeSurveyAdManager.InterstitialDriver() {
            @Override
            public void initialize(@androidx.annotation.NonNull Context context, @androidx.annotation.NonNull Runnable onInitialized) {
                onInitialized.run();
            }

            @Override
            public void loadInterstitial(
                    @androidx.annotation.NonNull Context context,
                    @androidx.annotation.NonNull String adUnitId,
                    @androidx.annotation.NonNull PrimeSurveyAdManager.InterstitialLoadCallback callback
            ) {
                assertEquals("ca-app-pub-8972616814126419/2306700688", adUnitId);
                callbacks.add(callback);
            }
        });

        PrimeSurveyActivity activity = Robolectric.buildActivity(PrimeSurveyActivity.class)
                .create()
                .start()
                .resume()
                .get();

        // Fail initial preload so Q2 needs an on-demand load
        callbacks.get(0).onFailed("No fill internal error 3", 3);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        TextView questionCounter = activity.findViewById(R.id.tv_question_counter);
        View optionA = activity.findViewById(R.id.option_card_a);
        View nextBtn = activity.findViewById(R.id.btn_next_question);
        View adStatusContainer = activity.findViewById(R.id.ad_status_container);
        View adLoadingSpinner = activity.findViewById(R.id.ad_loading_spinner);
        TextView adStatusMessage = activity.findViewById(R.id.tv_ad_status_message);
        com.google.android.material.button.MaterialButton retryBtn = activity.findViewById(R.id.btn_retry_ad);

        // Complete Q1 (No Ad)
        optionA.performClick();
        nextBtn.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("Question 2 of 15", questionCounter.getText().toString());

        // Complete Q2 (Ad required) and click Next
        optionA.performClick();
        nextBtn.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals(View.VISIBLE, adStatusContainer.getVisibility());
        assertEquals(View.VISIBLE, adLoadingSpinner.getVisibility());
        assertEquals(View.GONE, retryBtn.getVisibility());
        assertEquals("Loading Advertisement...", adStatusMessage.getText().toString());
        org.junit.Assert.assertFalse(nextBtn.isEnabled());

        // Simulate AdMob load failure on Q2
        callbacks.get(callbacks.size() - 1).onFailed("No fill internal error 3", 3);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        // Must stay on Q2, never fake ad display, show compact message + single RETRY button (no Continue Survey)
        assertEquals("Question 2 of 15", questionCounter.getText().toString());
        org.junit.Assert.assertFalse(activity.getAdManagerForTest().wasInterstitialDisplayedForQuestion(2));
        assertEquals(0, activity.getAdManagerForTest().getDisplayedInterstitialCount());
        assertEquals(View.GONE, adLoadingSpinner.getVisibility());
        assertEquals(View.VISIBLE, retryBtn.getVisibility());
        assertEquals("RETRY", retryBtn.getText().toString());
        assertEquals("Advertisement unavailable. Please try again.", adStatusMessage.getText().toString());
        org.junit.Assert.assertFalse(nextBtn.isEnabled());

        // Tap RETRY -> loading UI resumes and succeeds
        int countBeforeRetry = callbacks.size();
        retryBtn.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(countBeforeRetry + 1, callbacks.size());
        assertEquals(View.VISIBLE, adLoadingSpinner.getVisibility());
        assertEquals(View.GONE, retryBtn.getVisibility());
        assertEquals("Loading Advertisement...", adStatusMessage.getText().toString());
        org.junit.Assert.assertFalse(nextBtn.isEnabled());

        final com.google.android.gms.ads.FullScreenContentCallback[] holder = {null};
        callbacks.get(callbacks.size() - 1).onLoaded(new PrimeSurveyAdManager.LoadedInterstitialHandle() {
            @Override
            public void setFullScreenContentCallback(
                    @androidx.annotation.Nullable com.google.android.gms.ads.FullScreenContentCallback callback
            ) {
                holder[0] = callback;
            }

            @Override
            public void show(@androidx.annotation.NonNull android.app.Activity act) {
                showInvocations[0]++;
                if (holder[0] != null) {
                    holder[0].onAdShowedFullScreenContent();
                }
            }
        });
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals(1, showInvocations[0]);
        org.junit.Assert.assertTrue(activity.getAdManagerForTest().wasInterstitialDisplayedForQuestion(2));
        holder[0].onAdDismissedFullScreenContent();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        // Ad dismissed -> ad bar hidden, Next button enabled; user taps Next to advance to Q3
        assertEquals(View.GONE, adStatusContainer.getVisibility());
        org.junit.Assert.assertTrue(nextBtn.isEnabled());
        assertEquals("Question 2 of 15", questionCounter.getText().toString());

        nextBtn.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals("Question 3 of 15", questionCounter.getText().toString());

        // Duplicate check: requesting Q2 again never invokes show() a second time
        activity.getAdManagerForTest().showOrLoadInterstitialForQuestion(
                activity,
                2,
                new PrimeSurveyAdManager.InterstitialFlowListener() {
                    @Override
                    public void onAdLoading() {
                    }

                    @Override
                    public void onAdDismissed() {
                    }

                    @Override
                    public void onAdLoadFailed(@androidx.annotation.NonNull String errorMessage, int attemptCount) {
                    }
                }
        );
        assertEquals(1, showInvocations[0]);
    }

    @Test
    public void primeSurveyBoundedAutoRetryRetriesWithDelaysWithoutInfiniteLoop() {
        final java.util.List<PrimeSurveyAdManager.InterstitialLoadCallback> callbacks =
                new java.util.ArrayList<>();

        PrimeSurveyAdManager.Holder.setInterstitialDriverOverride(new PrimeSurveyAdManager.InterstitialDriver() {
            @Override
            public void initialize(@androidx.annotation.NonNull Context context, @androidx.annotation.NonNull Runnable onInitialized) {
                onInitialized.run();
            }

            @Override
            public void loadInterstitial(
                    @androidx.annotation.NonNull Context context,
                    @androidx.annotation.NonNull String adUnitId,
                    @androidx.annotation.NonNull PrimeSurveyAdManager.InterstitialLoadCallback callback
            ) {
                callbacks.add(callback);
            }
        });

        PrimeSurveyActivity activity = Robolectric.buildActivity(PrimeSurveyActivity.class)
                .create()
                .start()
                .resume()
                .get();

        // Fail initial preload
        callbacks.get(0).onFailed("Network error", 2);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        TextView questionCounter = activity.findViewById(R.id.tv_question_counter);
        View optionA = activity.findViewById(R.id.option_card_a);
        View nextBtn = activity.findViewById(R.id.btn_next_question);
        View adLoadingSpinner = activity.findViewById(R.id.ad_loading_spinner);
        TextView adStatusMessage = activity.findViewById(R.id.tv_ad_status_message);
        View retryBtn = activity.findViewById(R.id.btn_retry_ad);

        // Q1 -> Q2
        optionA.performClick();
        nextBtn.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        // Q2 -> click Next -> initial on-demand load fails
        optionA.performClick();
        nextBtn.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        int requestsAfterQ2Next = callbacks.size();
        callbacks.get(callbacks.size() - 1).onFailed("No fill", 3);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals("Advertisement unavailable. Please try again.", adStatusMessage.getText().toString());
        assertEquals(View.VISIBLE, retryBtn.getVisibility());
        org.junit.Assert.assertFalse(nextBtn.isEnabled());

        // Does NOT rapid-retry immediately after 500ms
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        assertEquals(requestsAfterQ2Next, callbacks.size());

        // Auto-retry #1 fires after 3s delay
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2600));
        assertEquals(requestsAfterQ2Next + 1, callbacks.size());
        assertEquals(View.VISIBLE, adLoadingSpinner.getVisibility());
        assertEquals("Loading Advertisement...", adStatusMessage.getText().toString());
        callbacks.get(callbacks.size() - 1).onFailed("No fill", 3);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        // Auto-retry #2 fires after 6s delay
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(6100));
        assertEquals(requestsAfterQ2Next + 2, callbacks.size());
        callbacks.get(callbacks.size() - 1).onFailed("No fill", 3);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        // Auto-retry #3 fires after 10s delay
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10100));
        assertEquals(requestsAfterQ2Next + 3, callbacks.size());
        callbacks.get(callbacks.size() - 1).onFailed("No fill", 3);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        // Bounded limit reached: advancing another 30s triggers NO infinite auto-retry loop
        int requestsAtAutoLimit = callbacks.size();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(30000));
        assertEquals(requestsAtAutoLimit, callbacks.size());
        assertEquals(View.VISIBLE, retryBtn.getVisibility());
        org.junit.Assert.assertFalse(nextBtn.isEnabled());
        assertEquals("Question 2 of 15", questionCounter.getText().toString());

        // Manual exit remains possible without advancing past Q2
        activity.findViewById(R.id.btn_survey_back).performClick();
        org.junit.Assert.assertTrue(activity.isFinishing());
    }

    @Test
    public void primeSurveyEnforcesRewardVerificationAnd24HourAntiTamperCooldown() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("tutor_earn_prime_survey_ledger", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();

        final long[] serverTimeMs = {1_700_000_000_000L};
        final long[] elapsedMs = {10_000L};
        final long[] wallClockMs = {1_700_000_000_000L};
        final int[] bootCount = {5};

        RewardTimeProvider fakeTime = new RewardTimeProvider() {
            @Override
            public long elapsedRealtimeMs() {
                return elapsedMs[0];
            }

            @Override
            public long wallClockMs() {
                return wallClockMs[0];
            }

            @Override
            public int getBootCount(@androidx.annotation.NonNull Context ctx) {
                return bootCount[0];
            }

            @Override
            public void fetchAuthoritativeServerTimeMs(@androidx.annotation.NonNull ServerTimeCallback callback) {
                callback.onResult(serverTimeMs[0]);
            }
        };

        FirebasePrimeSurveyBackend disabledFirebase = new FirebasePrimeSurveyBackend(context) {
            @Override
            public boolean isConfiguredAndAuthenticated() {
                return false;
            }
        };

        PrimeSurveyRepository repo = new PrimeSurveyRepository(context, fakeTime, disabledFirebase);

        // 1. Rewarded Ad NOT completed -> coins must NOT be added
        final boolean[] unverifiedResult = {true};
        repo.commitVerifiedReward(false, "tx_unverified", (success, msg, state) -> {
            unverifiedResult[0] = success;
            assertEquals(0L, state.totalVerifiedCoins);
        });
        org.junit.Assert.assertFalse(unverifiedResult[0]);

        // 2. Rewarded Ad verified -> 1050 coins added & 24h cooldown started
        final boolean[] firstClaimResult = {false};
        repo.commitVerifiedReward(true, "tx_first", (success, msg, state) -> {
            firstClaimResult[0] = success;
            assertEquals("1050 Coins Added Successfully!", msg);
            assertEquals(1050L, state.totalVerifiedCoins);
            org.junit.Assert.assertFalse(state.canClaim);
            assertEquals(PrimeSurveyRepository.COOLDOWN_DURATION_MS, state.remainingCooldownMs);
        });
        org.junit.Assert.assertTrue(firstClaimResult[0]);

        // 3. Duplicate / second claim immediately rejected
        final boolean[] duplicateResult = {true};
        repo.commitVerifiedReward(true, "tx_second", (success, msg, state) -> {
            duplicateResult[0] = success;
            assertEquals(1050L, state.totalVerifiedCoins);
        });
        org.junit.Assert.assertFalse(duplicateResult[0]);

        // 4. App restart + device wall clock pushed +36 hours forward (offline) -> STILL BLOCKED
        serverTimeMs[0] = -1L; // offline
        wallClockMs[0] += 36L * 60L * 60L * 1000L; // tampered forward
        elapsedMs[0] += 5L * 60L * 1000L; // only 5 real minutes passed

        PrimeSurveyRepository restartedRepo = new PrimeSurveyRepository(context, fakeTime, disabledFirebase);
        final boolean[] tamperCheckCanClaim = {true};
        restartedRepo.verifyClaimEligibility(state -> tamperCheckCanClaim[0] = state.canClaim);
        org.junit.Assert.assertFalse("Forward device clock tamper must NOT bypass 24h cooldown", tamperCheckCanClaim[0]);

        // 5. Device clock moved backward -> STILL BLOCKED
        serverTimeMs[0] = 1_700_000_000_000L + (2L * 60L * 60L * 1000L);
        wallClockMs[0] = 1_600_000_000_000L;
        restartedRepo.verifyClaimEligibility(state -> tamperCheckCanClaim[0] = state.canClaim);
        org.junit.Assert.assertFalse(tamperCheckCanClaim[0]);

        // 6. After genuine 24 hours on authoritative server time -> re-enabled & claim succeeds
        serverTimeMs[0] = 1_700_000_000_000L + PrimeSurveyRepository.COOLDOWN_DURATION_MS;
        elapsedMs[0] += PrimeSurveyRepository.COOLDOWN_DURATION_MS;
        restartedRepo.verifyClaimEligibility(state -> {
            org.junit.Assert.assertTrue(state.canClaim);
            assertEquals(0L, state.remainingCooldownMs);
        });

        final boolean[] secondDayResult = {false};
        restartedRepo.commitVerifiedReward(true, "tx_day2", (success, msg, state) -> {
            secondDayResult[0] = success;
            assertEquals(2100L, state.totalVerifiedCoins);
        });
        org.junit.Assert.assertTrue(secondDayResult[0]);
    }

    @Test
    public void firebaseConfigMatchesUploadedGoogleServicesJson() {
        Context context = ApplicationProvider.getApplicationContext();
        com.google.firebase.FirebaseApp app = TutorEarnApplication.ensureFirebaseInitialized(context);
        assertNotNull(app);

        com.google.firebase.FirebaseOptions options = app.getOptions();
        assertEquals("proti-pro-tutor", options.getProjectId());
        assertEquals("1067440189580", options.getGcmSenderId());
        assertEquals("https://proti-pro-tutor-default-rtdb.firebaseio.com", options.getDatabaseUrl());
        assertEquals("proti-pro-tutor.firebasestorage.app", options.getStorageBucket());
        assertEquals("1:1067440189580:android:9b766a475ec6a63ebd4ba9", options.getApplicationId());
        assertEquals("AIzaSyDXf9cW35UpSaz-d_EYJD9Clu6HORtKe4k", options.getApiKey());
        assertEquals(
                "1067440189580-vf9p9hlq8pb5sh4d8psca28feafbjl2l.apps.googleusercontent.com",
                context.getString(R.string.tutor_firebase_web_client_id)
        );
    }

    @Test
    public void firebaseAuthAndRealtimeDatabaseBindToProtiProTutorProject() {
        Context context = ApplicationProvider.getApplicationContext();
        TutorEarnApplication.ensureFirebaseInitialized(context);

        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
        assertNotNull(auth);
        assertEquals("proti-pro-tutor", auth.getApp().getOptions().getProjectId());

        String dbUrl = context.getString(R.string.tutor_firebase_database_url);
        com.google.firebase.database.FirebaseDatabase db =
                com.google.firebase.database.FirebaseDatabase.getInstance(dbUrl);
        assertNotNull(db);
        assertEquals(
                "https://proti-pro-tutor-default-rtdb.firebaseio.com/users",
                db.getReference("users").toString()
        );
    }

    @Test
    public void loginScreenDisplaysFirebaseAuthErrorWithoutNavigating() {
        AuthGateway.Holder.setTestOverride(new AuthGateway() {
            @Override
            public void signIn(
                    @androidx.annotation.NonNull String email,
                    @androidx.annotation.NonNull String password,
                    @androidx.annotation.NonNull AuthCallback callback
            ) {
                callback.onError("Invalid password or account does not exist.");
            }

            @Override
            public void signUp(
                    @androidx.annotation.NonNull String fullName,
                    @androidx.annotation.NonNull String email,
                    @androidx.annotation.NonNull String password,
                    @androidx.annotation.NonNull AuthCallback callback
            ) {
            }

            @Override
            public boolean isUserSignedIn() {
                return false;
            }
        });

        LoginActivity activity = Robolectric.buildActivity(LoginActivity.class)
                .create()
                .start()
                .resume()
                .get();

        android.widget.EditText etEmail = activity.findViewById(R.id.et_login_email);
        android.widget.EditText etPassword = activity.findViewById(R.id.et_login_password);
        com.google.android.material.textfield.TextInputLayout tilPassword =
                activity.findViewById(R.id.til_login_password);
        View loginBtn = activity.findViewById(R.id.btn_login);

        etEmail.setText("student@tutorearn.com");
        etPassword.setText("wrongpass");
        loginBtn.performClick();

        assertEquals(
                "Invalid password or account does not exist.",
                tilPassword.getError().toString()
        );
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void homeScreenDisplaysTopMyCoinsBalanceCardWithZeroInitially() {
        HomeActivity activity = Robolectric.buildActivity(HomeActivity.class)
                .create()
                .start()
                .resume()
                .get();

        View coinCard = activity.findViewById(R.id.card_coin_balance);
        View coinIcon = activity.findViewById(R.id.iv_coin_balance_icon);
        TextView coinLabel = activity.findViewById(R.id.tv_coin_balance_label);
        TextView coinValue = activity.findViewById(R.id.tv_coin_balance_value);

        assertNotNull(coinCard);
        assertNotNull(coinIcon);
        assertEquals(View.VISIBLE, coinCard.getVisibility());
        assertEquals(View.VISIBLE, coinIcon.getVisibility());
        assertEquals("My Coins", coinLabel.getText().toString());
        assertEquals("0 Coins", coinValue.getText().toString());
    }

    @Test
    public void centralizedCoinBalanceUpdatesHomeScreenAcrossNavigationAndRestart() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("tutor_earn_prime_survey_ledger", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();

        org.robolectric.android.controller.ActivityController<HomeActivity> homeController =
                Robolectric.buildActivity(HomeActivity.class).create().start().resume();
        HomeActivity homeActivity = homeController.get();

        TextView coinValue = homeActivity.findViewById(R.id.tv_coin_balance_value);
        assertEquals("0 Coins", coinValue.getText().toString());

        // Simulate Prime Survey verified reward (1050 Coins) committed through PrimeSurveyRepository
        RewardTimeProvider fakeTime = new RewardTimeProvider() {
            @Override
            public long elapsedRealtimeMs() {
                return 5_000L;
            }

            @Override
            public long wallClockMs() {
                return 1_700_000_000_000L;
            }

            @Override
            public int getBootCount(@androidx.annotation.NonNull Context ctx) {
                return 1;
            }

            @Override
            public void fetchAuthoritativeServerTimeMs(@androidx.annotation.NonNull ServerTimeCallback callback) {
                callback.onResult(1_700_000_000_000L);
            }
        };
        FirebasePrimeSurveyBackend disabledFirebase = new FirebasePrimeSurveyBackend(context) {
            @Override
            public boolean isConfiguredAndAuthenticated() {
                return false;
            }
        };

        PrimeSurveyRepository surveyRepo = new PrimeSurveyRepository(context, fakeTime, disabledFirebase);
        final boolean[] claimed = {false};
        surveyRepo.commitVerifiedReward(true, "tx_central_ps_1", (success, msg, state) -> {
            claimed[0] = success;
            assertEquals(1050L, state.totalVerifiedCoins);
        });
        org.junit.Assert.assertTrue(claimed[0]);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        // Home Screen updates automatically to "1,050 Coins"
        assertEquals("1,050 Coins", coinValue.getText().toString());

        // Duplicate transaction ID must NOT credit extra coins
        CoinBalanceRepository centralRepo = CoinBalanceRepository.getInstance(context);
        final boolean[] dupCommitted = {true};
        centralRepo.creditVerifiedEarning(
                CoinEarningSource.PRIME_SURVEY,
                "tx_central_ps_1",
                1050,
                true,
                (success, updatedBalance, message) -> {
                    dupCommitted[0] = success;
                    assertEquals(1050L, updatedBalance);
                }
        );
        org.junit.Assert.assertFalse(dupCommitted[0]);

        // Unverified earning credit must be rejected
        final boolean[] unverifiedCommitted = {true};
        centralRepo.creditVerifiedEarning(
                CoinEarningSource.SURVEY_REWARDS,
                "tx_unverified_sr",
                500,
                false,
                (success, updatedBalance, message) -> {
                    unverifiedCommitted[0] = success;
                    assertEquals(1050L, updatedBalance);
                }
        );
        org.junit.Assert.assertFalse(unverifiedCommitted[0]);

        // Another verified earning source (Survey Rewards = 500 coins) credits into the SAME central balance
        final boolean[] srCommitted = {false};
        centralRepo.creditVerifiedEarning(
                CoinEarningSource.SURVEY_REWARDS,
                "tx_sr_valid_1",
                500,
                true,
                (success, updatedBalance, message) -> {
                    srCommitted[0] = success;
                    assertEquals(1550L, updatedBalance);
                }
        );
        org.junit.Assert.assertTrue(srCommitted[0]);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("1,550 Coins", coinValue.getText().toString());

        // Returning to Home (pause -> resume) preserves balance
        homeController.pause().resume();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("1,550 Coins", coinValue.getText().toString());

        // Simulating full app restart (new singleton + new HomeActivity) preserves balance
        homeController.stop().destroy();
        CoinBalanceRepository.Holder.setDriverOverride(null);
        // Re-create HomeActivity without clearing SharedPreferences
        HomeActivity restartedHome = Robolectric.buildActivity(HomeActivity.class)
                .create()
                .start()
                .resume()
                .get();
        TextView restartedCoinValue = restartedHome.findViewById(R.id.tv_coin_balance_value);
        assertEquals("1,550 Coins", restartedCoinValue.getText().toString());
    }

    @Test
    public void centralizedCoinBalanceIsolatesUsersAndReceivesRealtimeFirebaseUpdates() {
        final String[] activeUid = {"uid_alpha"};
        final java.util.Map<String, Long> remoteBalances = new java.util.HashMap<>();
        remoteBalances.put("uid_alpha", 0L);
        remoteBalances.put("uid_beta", 500L);
        final java.util.Set<String> remoteTxIds = new java.util.HashSet<>();
        final java.util.Map<String, CoinBalanceRepository.RemoteBalanceObserver> liveObservers =
                new java.util.HashMap<>();

        CoinBalanceRepository.Holder.setDriverOverride(new CoinBalanceRepository.CoinDatabaseDriver() {
            @Override
            public boolean isAuthenticated(@androidx.annotation.NonNull Context context) {
                return activeUid[0] != null;
            }

            @androidx.annotation.Nullable
            @Override
            public String getAuthenticatedUid(@androidx.annotation.NonNull Context context) {
                return activeUid[0];
            }

            @Override
            public void observeBalance(
                    @androidx.annotation.NonNull Context context,
                    @androidx.annotation.NonNull String uid,
                    @androidx.annotation.NonNull CoinBalanceRepository.RemoteBalanceObserver observer
            ) {
                liveObservers.put(uid, observer);
                Long bal = remoteBalances.get(uid);
                if (bal != null) {
                    observer.onBalanceChanged(bal);
                }
            }

            @Override
            public void stopObserving(@androidx.annotation.NonNull String uid) {
                liveObservers.remove(uid);
            }

            @Override
            public void fetchBalanceOnce(
                    @androidx.annotation.NonNull Context context,
                    @androidx.annotation.NonNull String uid,
                    @androidx.annotation.NonNull CoinBalanceRepository.RemoteBalanceObserver observer
            ) {
                Long bal = remoteBalances.get(uid);
                if (bal != null) {
                    observer.onBalanceChanged(bal);
                }
            }

            @Override
            public void commitVerifiedTransaction(
                    @androidx.annotation.NonNull Context context,
                    @androidx.annotation.NonNull String uid,
                    @androidx.annotation.NonNull CoinEarningSource source,
                    @androidx.annotation.NonNull String transactionId,
                    int rewardCoins,
                    @androidx.annotation.NonNull CoinBalanceRepository.RemoteTransactionCallback callback
            ) {
                String scopedTx = uid + ":" + transactionId;
                if (!remoteTxIds.add(scopedTx)) {
                    callback.onComplete(false, -1L, "Duplicate transaction");
                    return;
                }
                long current = remoteBalances.containsKey(uid) ? remoteBalances.get(uid) : 0L;
                long updated = current + rewardCoins;
                remoteBalances.put(uid, updated);
                callback.onComplete(true, updated, null);
            }
        });

        HomeActivity homeActivity = Robolectric.buildActivity(HomeActivity.class)
                .create()
                .start()
                .resume()
                .get();
        TextView coinValue = homeActivity.findViewById(R.id.tv_coin_balance_value);
        assertEquals("0 Coins", coinValue.getText().toString());

        // Real-time Firebase RTDB push for uid_alpha updates Home Screen immediately
        remoteBalances.put("uid_alpha", 1050L);
        liveObservers.get("uid_alpha").onBalanceChanged(1050L);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("1,050 Coins", coinValue.getText().toString());

        // Commit another verified reward via atomic driver -> balance reaches 1,350 Coins
        CoinBalanceRepository repo = CoinBalanceRepository.getInstance(homeActivity);
        repo.creditVerifiedEarning(
                CoinEarningSource.EARN_WITH_AI,
                "tx_ai_reward_1",
                300,
                true,
                (success, updatedBalance, message) -> {
                    org.junit.Assert.assertTrue(success);
                    assertEquals(1350L, updatedBalance);
                }
        );
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("1,350 Coins", coinValue.getText().toString());

        // Switch authenticated user to uid_beta -> separate balance (500 Coins), never leaks uid_alpha's balance
        activeUid[0] = "uid_beta";
        repo.refreshBalance();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("500 Coins", coinValue.getText().toString());
    }

    @Test
    public void mobileAdsSdkInitializesOnlyOnceAcrossRepeatedStartupAndManagerCalls() {
        Context context = ApplicationProvider.getApplicationContext();
        final int[] initInvocationCount = {0};
        final Runnable[] deferredCompletion = {null};

        TutorEarnApplication.resetMobileAdsForTest();
        TutorEarnApplication.AdInitHolder.setDriverOverride((ctx, onInitialized) -> {
            initInvocationCount[0]++;
            deferredCompletion[0] = onInitialized;
        });

        final int[] callbacksFired = {0};
        // First call (e.g., Application startup) initiates SDK initialization once
        TutorEarnApplication.ensureMobileAdsInitialized(context, () -> callbacksFired[0]++);
        // Second concurrent call (e.g., Activity opening) must queue without re-initializing
        TutorEarnApplication.ensureMobileAdsInitialized(context, () -> callbacksFired[0]++);

        assertEquals(1, initInvocationCount[0]);
        assertEquals(0, callbacksFired[0]);

        // Complete SDK init -> queued callbacks run, subsequent PrimeSurveyAdManager calls do NOT re-init
        deferredCompletion[0].run();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, initInvocationCount[0]);
        assertEquals(2, callbacksFired[0]);
        org.junit.Assert.assertTrue(TutorEarnApplication.isMobileAdsInitialized());

        PrimeSurveyAdManager manager1 = new PrimeSurveyAdManager(context);
        PrimeSurveyAdManager manager2 = new PrimeSurveyAdManager(context);
        manager1.initializeAndPreload();
        manager2.initializeAndPreload();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        // Still initialized only once across multiple managers
        assertEquals(1, initInvocationCount[0]);
        manager1.destroy();
        manager2.destroy();
    }

    @Test
    public void primeSurveyRewardedAdVerifiesOnUserEarnedRewardOnlyAndSupportsRetry() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("tutor_earn_prime_survey_ledger", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();

        final java.util.List<String> rewardedUnitIds = new java.util.ArrayList<>();
        final java.util.List<PrimeSurveyAdManager.RewardedLoadCallback> rewardedCallbacks =
                new java.util.ArrayList<>();

        PrimeSurveyAdManager.Holder.setRewardedDriverOverride((ctx, adUnitId, callback) -> {
            rewardedUnitIds.add(adUnitId);
            rewardedCallbacks.add(callback);
        });

        android.app.Activity hostActivity = Robolectric.buildActivity(HomeActivity.class)
                .create()
                .start()
                .resume()
                .get();

        PrimeSurveyAdManager adManager = new PrimeSurveyAdManager(hostActivity);
        final java.util.List<String> events = new java.util.ArrayList<>();

        PrimeSurveyAdManager.RewardedAdOutcomeListener listener = new PrimeSurveyAdManager.RewardedAdOutcomeListener() {
            @Override
            public void onLoadingStarted() {
                events.add("loading");
            }

            @Override
            public void onRewardVerified() {
                events.add("verified");
            }

            @Override
            public void onRewardNotEarned() {
                events.add("not_earned");
            }

            @Override
            public void onAdUnavailableOrFailed(@androidx.annotation.NonNull String reason) {
                events.add("failed:" + reason);
            }
        };

        // 1. First attempt fails (no-fill) -> uses official test Rewarded ID and triggers failure callback cleanly
        adManager.loadAndShowRewardedAd(hostActivity, listener);
        assertEquals(1, rewardedUnitIds.size());
        assertEquals("ca-app-pub-3940256099942544/5224354917", rewardedUnitIds.get(0));
        rewardedCallbacks.get(0).onFailed("No fill", 3);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("loading", events.get(0));
        assertEquals("failed:No fill", events.get(1));

        // 2. Retry succeeds in loading, but user closes ad BEFORE earning reward -> onRewardNotEarned ONLY
        adManager.loadAndShowRewardedAd(hostActivity, listener);
        assertEquals(2, rewardedCallbacks.size());
        rewardedCallbacks.get(1).onLoaded(new PrimeSurveyAdManager.LoadedRewardedHandle() {
            private com.google.android.gms.ads.FullScreenContentCallback fsCb;

            @Override
            public void setFullScreenContentCallback(
                    @androidx.annotation.Nullable com.google.android.gms.ads.FullScreenContentCallback callback
            ) {
                fsCb = callback;
            }

            @Override
            public void show(@androidx.annotation.NonNull android.app.Activity activity, @androidx.annotation.NonNull Runnable onUserEarnedReward) {
                fsCb.onAdShowedFullScreenContent();
                // User closes early without triggering onUserEarnedReward
                fsCb.onAdDismissedFullScreenContent();
            }
        });
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("not_earned", events.get(events.size() - 1));
        org.junit.Assert.assertFalse(events.contains("verified"));

        // Note: closing early triggered automatic preloadRewardedAd() -> rewardedCallbacks.size() == 3!
        assertEquals(3, rewardedCallbacks.size());

        // 3. Complete preload, tap Claim -> OnUserEarnedRewardListener fires before dismissal -> verified!
        rewardedCallbacks.get(2).onLoaded(new PrimeSurveyAdManager.LoadedRewardedHandle() {
            private com.google.android.gms.ads.FullScreenContentCallback fsCb;

            @Override
            public void setFullScreenContentCallback(
                    @androidx.annotation.Nullable com.google.android.gms.ads.FullScreenContentCallback callback
            ) {
                fsCb = callback;
            }

            @Override
            public void show(@androidx.annotation.NonNull android.app.Activity activity, @androidx.annotation.NonNull Runnable onUserEarnedReward) {
                fsCb.onAdShowedFullScreenContent();
                onUserEarnedReward.run();
                fsCb.onAdDismissedFullScreenContent();
            }
        });
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        adManager.loadAndShowRewardedAd(hostActivity, listener);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("verified", events.get(events.size() - 1));
        adManager.destroy();
    }

    @Test
    public void homeSurveyRewardsCardOpensSurveyRewardsActivity() {
        HomeActivity homeActivity = Robolectric.buildActivity(HomeActivity.class)
                .create()
                .start()
                .resume()
                .get();
        View surveyRewardsCard = homeActivity.findViewById(R.id.card_survey_rewards);
        assertNotNull(surveyRewardsCard);
        surveyRewardsCard.performClick();

        Intent nextIntent = Shadows.shadowOf(homeActivity).getNextStartedActivity();
        assertNotNull(nextIntent);
        assertNotNull(nextIntent.getComponent());
        assertEquals(SurveyRewardsActivity.class.getName(), nextIntent.getComponent().getClassName());
    }

    @Test
    public void surveyRewardsScreenDisplaysSingleCardAndCredits500CoinsOnlyAfterRewardCallback() {
        final long[] remoteBalance = {0L};
        final java.util.Set<String> processedTxIds = new java.util.HashSet<>();
        CoinBalanceRepository.Holder.setDriverOverride(new CoinBalanceRepository.CoinDatabaseDriver() {
            @Override
            public boolean isAuthenticated(@androidx.annotation.NonNull Context context) {
                return true;
            }

            @androidx.annotation.Nullable
            @Override
            public String getAuthenticatedUid(@androidx.annotation.NonNull Context context) {
                return "uid_survey_rewards_test";
            }

            @Override
            public void observeBalance(
                    @androidx.annotation.NonNull Context context,
                    @androidx.annotation.NonNull String uid,
                    @androidx.annotation.NonNull CoinBalanceRepository.RemoteBalanceObserver observer
            ) {
                observer.onBalanceChanged(remoteBalance[0]);
            }

            @Override
            public void stopObserving(@androidx.annotation.NonNull String uid) {
            }

            @Override
            public void fetchBalanceOnce(
                    @androidx.annotation.NonNull Context context,
                    @androidx.annotation.NonNull String uid,
                    @androidx.annotation.NonNull CoinBalanceRepository.RemoteBalanceObserver observer
            ) {
                observer.onBalanceChanged(remoteBalance[0]);
            }

            @Override
            public void commitVerifiedTransaction(
                    @androidx.annotation.NonNull Context context,
                    @androidx.annotation.NonNull String uid,
                    @androidx.annotation.NonNull CoinEarningSource source,
                    @androidx.annotation.NonNull String transactionId,
                    int rewardCoins,
                    @androidx.annotation.NonNull CoinBalanceRepository.RemoteTransactionCallback callback
            ) {
                if (!processedTxIds.add(transactionId)) {
                    callback.onComplete(false, -1L, "Duplicate transaction");
                    return;
                }
                remoteBalance[0] += rewardCoins;
                callback.onComplete(true, remoteBalance[0], null);
            }
        });

        final long[] fakeServerNow = {1_700_000_000_000L};
        SurveyRewardsRepository.Holder.setTimeProviderOverride(new RewardTimeProvider() {
            @Override
            public long elapsedRealtimeMs() {
                return 10_000L;
            }

            @Override
            public long wallClockMs() {
                return fakeServerNow[0];
            }

            @Override
            public int getBootCount(@androidx.annotation.NonNull Context ctx) {
                return 1;
            }

            @Override
            public void fetchAuthoritativeServerTimeMs(@androidx.annotation.NonNull ServerTimeCallback callback) {
                callback.onResult(fakeServerNow[0]);
            }
        });

        TutorEarnApplication.resetMobileAdsForTest();
        TutorEarnApplication.AdInitHolder.setDriverOverride((ctx, onInitialized) -> onInitialized.run());

        final java.util.List<String> requestedAdUnits = new java.util.ArrayList<>();
        final java.util.List<SurveyRewardsAdManager.RewardedLoadCallback> callbacks =
                new java.util.ArrayList<>();
        SurveyRewardsAdManager.Holder.setRewardedDriverOverride((ctx, adUnitId, callback) -> {
            requestedAdUnits.add(adUnitId);
            callbacks.add(callback);
        });

        SurveyRewardsActivity activity = Robolectric.buildActivity(SurveyRewardsActivity.class)
                .create()
                .start()
                .resume()
                .get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        // Single reward card with exact required title, sentence, coin icon, and CLAIM CTA
        View singleCard = activity.findViewById(R.id.card_survey_reward);
        View coinIcon = activity.findViewById(R.id.iv_survey_reward_coin);
        TextView titleView = activity.findViewById(R.id.tv_survey_rewards_title);
        TextView sentenceView = activity.findViewById(R.id.tv_survey_rewards_sentence);
        TextView statusView = activity.findViewById(R.id.tv_survey_reward_status);
        View claimButton = activity.findViewById(R.id.btn_claim_survey_reward);
        TextView claimButtonText = activity.findViewById(R.id.btn_claim_survey_reward_text);
        View claimProgress = activity.findViewById(R.id.claim_survey_reward_progress);

        assertEquals(View.VISIBLE, singleCard.getVisibility());
        assertEquals(View.VISIBLE, coinIcon.getVisibility());
        assertEquals("Survey Rewards", titleView.getText().toString());
        assertEquals(
                "Watch a rewarded advertisement and claim your 500 coins!",
                sentenceView.getText().toString()
        );
        assertEquals("CLAIM 500 COINS", claimButtonText.getText().toString());
        assertEquals(View.GONE, statusView.getVisibility());
        assertEquals(View.GONE, claimProgress.getVisibility());

        // Preload on launch used configured Survey Rewards Rewarded Ad Unit ID
        org.junit.Assert.assertFalse(requestedAdUnits.isEmpty());
        assertEquals("ca-app-pub-8972616814126419/5563489220", requestedAdUnits.get(0));

        // 1. Fail preload (No fill) -> user taps CLAIM -> retry fails -> central balance stays 0
        callbacks.get(0).onFailed("No fill", 3);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        claimButton.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(2, callbacks.size());
        org.junit.Assert.assertFalse(claimButton.isEnabled());
        assertEquals(View.VISIBLE, claimProgress.getVisibility());
        assertEquals("Loading Advertisement...", claimButtonText.getText().toString());

        callbacks.get(1).onFailed("No fill", 3);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0L, remoteBalance[0]);
        assertEquals(View.VISIBLE, statusView.getVisibility());
        assertEquals(
                "Advertisement unavailable. Please try again.",
                statusView.getText().toString()
        );
        org.junit.Assert.assertTrue(claimButton.isEnabled());

        // 2. Tap CLAIM -> loads, opens, and closes early WITHOUT OnUserEarnedRewardListener -> stays 0
        claimButton.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(3, callbacks.size());
        callbacks.get(2).onLoaded(new SurveyRewardsAdManager.LoadedRewardedHandle() {
            private com.google.android.gms.ads.FullScreenContentCallback fsCb;

            @Override
            public void setFullScreenContentCallback(
                    @androidx.annotation.Nullable com.google.android.gms.ads.FullScreenContentCallback callback
            ) {
                fsCb = callback;
            }

            @Override
            public void show(@androidx.annotation.NonNull android.app.Activity host, @androidx.annotation.NonNull Runnable onUserEarnedReward) {
                fsCb.onAdShowedFullScreenContent();
                fsCb.onAdDismissedFullScreenContent();
            }
        });
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0L, remoteBalance[0]);
        assertEquals(
                "Rewarded video was closed early. Complete the video to claim 500 coins.",
                statusView.getText().toString()
        );

        // Dismissing early automatically queued a preload at callbacks.get(3); complete it with full reward callback
        assertEquals(4, callbacks.size());
        callbacks.get(3).onLoaded(new SurveyRewardsAdManager.LoadedRewardedHandle() {
            private com.google.android.gms.ads.FullScreenContentCallback fsCb;

            @Override
            public void setFullScreenContentCallback(
                    @androidx.annotation.Nullable com.google.android.gms.ads.FullScreenContentCallback callback
            ) {
                fsCb = callback;
            }

            @Override
            public void show(@androidx.annotation.NonNull android.app.Activity host, @androidx.annotation.NonNull Runnable onUserEarnedReward) {
                fsCb.onAdShowedFullScreenContent();
                onUserEarnedReward.run();
                fsCb.onAdDismissedFullScreenContent();
            }
        });
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        // 3. Tap CLAIM -> preloaded rewarded ad shows, OnUserEarnedRewardListener fires -> credits 500 coins + exact message + enforces 24h cooldown
        claimButton.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(500L, remoteBalance[0]);
        assertEquals(500L, CoinBalanceRepository.getInstance(activity).getCurrentBalance());
        assertEquals("500 Coins Added Successfully!", statusView.getText().toString());
        assertEquals(View.VISIBLE, statusView.getVisibility());
        org.junit.Assert.assertFalse(claimButton.isEnabled());
        TextView cooldownView = activity.findViewById(R.id.tv_survey_rewards_cooldown);
        assertEquals(View.VISIBLE, cooldownView.getVisibility());
        org.junit.Assert.assertTrue(cooldownView.getText().toString().startsWith("Available again in "));
        assertEquals(View.GONE, claimProgress.getVisibility());
        assertEquals("CLAIM 500 COINS", claimButtonText.getText().toString());

        // 4. Repository rejects wrong reward amount for SURVEY_REWARDS (!= 500)
        final boolean[] invalidRejected = {true};
        CoinBalanceRepository.getInstance(activity).creditVerifiedEarning(
                CoinEarningSource.SURVEY_REWARDS,
                "tx_invalid_amount",
                250,
                true,
                (success, updatedBalance, message) -> invalidRejected[0] = success
        );
        org.junit.Assert.assertFalse(invalidRejected[0]);
        assertEquals(500L, remoteBalance[0]);
    }

    @Test
    public void surveyRewardsEnforces24HourAccountCooldownAcrossRestartsLogoutAndClockTampering() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("tutor_earn_survey_rewards_ledger", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();

        final long[] serverTimeMs = {1_700_000_000_000L};
        final long[] elapsedMs = {10_000L};
        final long[] wallClockMs = {1_700_000_000_000L};
        final int[] bootCount = {5};
        final String[] activeUid = {"uid_alpha"};
        final java.util.Map<String, Long> remoteLastClaimMap = new java.util.HashMap<>();
        final java.util.Map<String, Long> remoteClaimsCount = new java.util.HashMap<>();
        final java.util.Map<String, Long> remoteCoinsEarned = new java.util.HashMap<>();
        final java.util.Map<String, Long> remoteBalances = new java.util.HashMap<>();
        final java.util.Set<String> processedTxIds = new java.util.HashSet<>();

        RewardTimeProvider fakeTime = new RewardTimeProvider() {
            @Override
            public long elapsedRealtimeMs() {
                return elapsedMs[0];
            }

            @Override
            public long wallClockMs() {
                return wallClockMs[0];
            }

            @Override
            public int getBootCount(@androidx.annotation.NonNull Context ctx) {
                return bootCount[0];
            }

            @Override
            public void fetchAuthoritativeServerTimeMs(@androidx.annotation.NonNull ServerTimeCallback callback) {
                callback.onResult(serverTimeMs[0]);
            }
        };

        FirebaseSurveyRewardsBackend fakeBackend = new FirebaseSurveyRewardsBackend(context) {
            @Override
            public boolean isConfiguredAndAuthenticated() {
                return activeUid[0] != null;
            }

            @androidx.annotation.Nullable
            @Override
            public String getAuthenticatedUid() {
                return activeUid[0];
            }

            @Override
            public void fetchRemoteClaimStatus(@androidx.annotation.NonNull RemoteEligibilityCallback callback) {
                String uid = activeUid[0];
                long lastClaim = uid != null && remoteLastClaimMap.containsKey(uid) ? remoteLastClaimMap.get(uid) : 0L;
                long claims = uid != null && remoteClaimsCount.containsKey(uid) ? remoteClaimsCount.get(uid) : 0L;
                long coins = uid != null && remoteCoinsEarned.containsKey(uid) ? remoteCoinsEarned.get(uid) : 0L;
                callback.onResult(uid != null, lastClaim, claims, coins, serverTimeMs[0], null);
            }

            @Override
            public void recordVerifiedRewardTransaction(
                    @androidx.annotation.NonNull String transactionId,
                    int rewardCoins,
                    long cooldownDurationMs,
                    long estimatedServerNowMs,
                    @androidx.annotation.NonNull RemoteClaimCallback callback
            ) {
                String uid = activeUid[0];
                if (uid == null) {
                    callback.onComplete(false, -1L, -1L, "Unauthenticated");
                    return;
                }
                if (!processedTxIds.add(uid + ":" + transactionId)) {
                    callback.onComplete(false, -1L, -1L, "Duplicate transaction rejected");
                    return;
                }
                long lastClaim = remoteLastClaimMap.containsKey(uid) ? remoteLastClaimMap.get(uid) : 0L;
                if (lastClaim > 0L && (estimatedServerNowMs - lastClaim) < cooldownDurationMs) {
                    callback.onComplete(false, -1L, -1L, "24-hour cooldown active");
                    return;
                }
                long updatedBal = (remoteBalances.containsKey(uid) ? remoteBalances.get(uid) : 0L) + rewardCoins;
                remoteBalances.put(uid, updatedBal);
                remoteLastClaimMap.put(uid, estimatedServerNowMs);
                remoteClaimsCount.put(uid, (remoteClaimsCount.containsKey(uid) ? remoteClaimsCount.get(uid) : 0L) + 1L);
                remoteCoinsEarned.put(uid, (remoteCoinsEarned.containsKey(uid) ? remoteCoinsEarned.get(uid) : 0L) + rewardCoins);
                callback.onComplete(true, estimatedServerNowMs, updatedBal, null);
            }
        };

        CoinBalanceRepository coinRepo = CoinBalanceRepository.getInstance(context);
        SurveyRewardsRepository repo = new SurveyRewardsRepository(context, fakeTime, fakeBackend, coinRepo);

        // 1. Rewarded Ad NOT earned -> rejected, 0 coins
        final boolean[] unverifiedOk = {true};
        repo.commitVerifiedReward(false, "tx_sr_unverified", (success, msg, state) -> unverifiedOk[0] = success);
        org.junit.Assert.assertFalse(unverifiedOk[0]);
        assertEquals(0L, remoteBalances.containsKey("uid_alpha") ? remoteBalances.get("uid_alpha") : 0L);

        // 2. First successful claim -> +500 coins, cooldown active
        final boolean[] firstOk = {false};
        repo.commitVerifiedReward(true, "tx_sr_1", (success, msg, state) -> {
            firstOk[0] = success;
            assertEquals("500 Coins Added Successfully!", msg);
            org.junit.Assert.assertFalse(state.canClaim);
            assertEquals(SurveyRewardsRepository.COOLDOWN_DURATION_MS, state.remainingCooldownMs);
        });
        org.junit.Assert.assertTrue(firstOk[0]);
        assertEquals(500L, (long) remoteBalances.get("uid_alpha"));
        assertEquals(1L, (long) remoteClaimsCount.get("uid_alpha"));
        assertEquals(500L, (long) remoteCoinsEarned.get("uid_alpha"));

        // 3. Duplicate transaction / second claim before 24h -> rejected
        final boolean[] dupOk = {true};
        repo.commitVerifiedReward(true, "tx_sr_1", (success, msg, state) -> dupOk[0] = success);
        org.junit.Assert.assertFalse(dupOk[0]);

        final boolean[] secondEarlyOk = {true};
        repo.commitVerifiedReward(true, "tx_sr_2", (success, msg, state) -> secondEarlyOk[0] = success);
        org.junit.Assert.assertFalse(secondEarlyOk[0]);
        assertEquals(500L, (long) remoteBalances.get("uid_alpha"));

        // 4. Device clock pushed +36h offline while only 5 real minutes passed -> STILL blocked
        serverTimeMs[0] = -1L;
        wallClockMs[0] += 36L * 60L * 60L * 1000L;
        elapsedMs[0] += 5L * 60L * 1000L;
        final boolean[] offlineTamperCanClaim = {true};
        repo.verifyClaimEligibility(state -> offlineTamperCanClaim[0] = state.canClaim);
        org.junit.Assert.assertFalse(offlineTamperCanClaim[0]);

        // 5. Simulate app reinstall / login on another device (clear local prefs) -> Firebase uid_alpha still blocked
        context.getSharedPreferences("tutor_earn_survey_rewards_ledger", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        serverTimeMs[0] = 1_700_000_000_000L + (12L * 60L * 60L * 1000L);
        SurveyRewardsRepository reinstalledRepo = new SurveyRewardsRepository(context, fakeTime, fakeBackend, coinRepo);
        final boolean[] reinstalledCanClaim = {true};
        reinstalledRepo.verifyClaimEligibility(state -> {
            reinstalledCanClaim[0] = state.canClaim;
            assertEquals(12L * 60L * 60L * 1000L, state.remainingCooldownMs);
        });
        org.junit.Assert.assertFalse(reinstalledCanClaim[0]);

        // 6. Logout and login as a different account (uid_beta) -> independent eligibility
        activeUid[0] = "uid_beta";
        final boolean[] betaCanClaim = {false};
        reinstalledRepo.verifyClaimEligibility(state -> betaCanClaim[0] = state.canClaim);
        org.junit.Assert.assertTrue(betaCanClaim[0]);

        // 7. Switch back to uid_alpha after 24h+1s -> eligible and credits next 500 coins
        activeUid[0] = "uid_alpha";
        serverTimeMs[0] = 1_700_000_000_000L + SurveyRewardsRepository.COOLDOWN_DURATION_MS + 1000L;
        final boolean[] after24hOk = {false};
        reinstalledRepo.commitVerifiedReward(true, "tx_sr_after_24h", (success, msg, state) -> {
            after24hOk[0] = success;
            org.junit.Assert.assertFalse(state.canClaim);
        });
        org.junit.Assert.assertTrue(after24hOk[0]);
        assertEquals(1000L, (long) remoteBalances.get("uid_alpha"));
        assertEquals(2L, (long) remoteClaimsCount.get("uid_alpha"));
        assertEquals(1000L, (long) remoteCoinsEarned.get("uid_alpha"));
    }

    @Test
    public void releaseManifestRemovesMobileAdsInitProviderAndHandlesEmptyAdMobAppIdSafely() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        PackageManager pm = context.getPackageManager();
        android.content.pm.PackageInfo pkgInfo = pm.getPackageInfo(
                context.getPackageName(),
                PackageManager.GET_PROVIDERS | PackageManager.GET_META_DATA
        );

        // Verify MobileAdsInitProvider was removed from merged manifest so release builds never crash before Application.onCreate()
        if (pkgInfo.providers != null) {
            for (android.content.pm.ProviderInfo provider : pkgInfo.providers) {
                org.junit.Assert.assertFalse(
                        "MobileAdsInitProvider must be removed to prevent pre-startup crash when ADMOB_APP_ID is empty",
                        "com.google.android.gms.ads.MobileAdsInitProvider".equals(provider.name)
                );
            }
        }

        org.junit.Assert.assertTrue(TutorEarnApplication.hasConfiguredAdMobAppId(context));

        android.content.pm.PackageInfo mutablePkgInfo =
                Shadows.shadowOf(pm).getInternalMutablePackageInfo(context.getPackageName());
        String originalAppId = mutablePkgInfo.applicationInfo.metaData.getString("com.google.android.gms.ads.APPLICATION_ID");
        try {
            // Simulate Release build where ADMOB_APP_ID secret is empty ("")
            mutablePkgInfo.applicationInfo.metaData.putString("com.google.android.gms.ads.APPLICATION_ID", "");
            org.junit.Assert.assertFalse(TutorEarnApplication.hasConfiguredAdMobAppId(context));

            org.robolectric.shadows.ShadowLog.clear();
            TutorEarnApplication.resetMobileAdsForTest();
            TutorEarnApplication app = ApplicationProvider.getApplicationContext();
            app.onCreate();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            org.junit.Assert.assertTrue(TutorEarnApplication.isMobileAdsInitialized());

            // Verify real AdMob ad managers fail gracefully instead of crashing when App ID is unconfigured
            final String[] interstitialFailure = {null};
            PrimeSurveyAdManager primeAdManager = new PrimeSurveyAdManager(context);
            WelcomeActivity welcomeActivity = Robolectric.buildActivity(WelcomeActivity.class)
                    .create()
                    .start()
                    .resume()
                    .get();
            primeAdManager.showOrLoadInterstitialForQuestion(
                    welcomeActivity,
                    2,
                    new PrimeSurveyAdManager.InterstitialFlowListener() {
                        @Override
                        public void onAdLoading() {
                        }

                        @Override
                        public void onAdDismissed() {
                        }

                        @Override
                        public void onAdLoadFailed(@androidx.annotation.NonNull String errorMessage, int attemptCount) {
                            interstitialFailure[0] = errorMessage;
                        }
                    }
            );
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNotNull(interstitialFailure[0]);
            primeAdManager.destroy();

            final String[] rewardedFailure = {null};
            SurveyRewardsAdManager surveyRewardsAdManager = new SurveyRewardsAdManager(context);
            surveyRewardsAdManager.loadAndShowRewardedAd(
                    welcomeActivity,
                    new SurveyRewardsAdManager.RewardedAdOutcomeListener() {
                        @Override
                        public void onLoadingStarted() {
                        }

                        @Override
                        public void onRewardVerified() {
                        }

                        @Override
                        public void onRewardNotEarned() {
                        }

                        @Override
                        public void onAdUnavailableOrFailed(@androidx.annotation.NonNull String reason) {
                            rewardedFailure[0] = reason;
                        }
                    }
            );
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNotNull(rewardedFailure[0]);
            surveyRewardsAdManager.destroy();

            // Verify WelcomeActivity -> LoginActivity -> HomeActivity flow completes cleanly without crashing
            welcomeActivity.findViewById(R.id.btn_get_started).performClick();
            Intent nextIntent = Shadows.shadowOf(welcomeActivity).getNextStartedActivity();
            assertNotNull(nextIntent);
            assertEquals(LoginActivity.class.getName(), nextIntent.getComponent().getClassName());

            LoginActivity loginActivity = Robolectric.buildActivity(LoginActivity.class)
                    .create()
                    .start()
                    .resume()
                    .get();
            ((android.widget.EditText) loginActivity.findViewById(R.id.et_login_email))
                    .setText("student@tutorearn.com");
            ((android.widget.EditText) loginActivity.findViewById(R.id.et_login_password))
                    .setText("secret123");
            loginActivity.findViewById(R.id.btn_login).performClick();
            Intent homeIntent = Shadows.shadowOf(loginActivity).getNextStartedActivity();
            assertNotNull(homeIntent);
            assertEquals(HomeActivity.class.getName(), homeIntent.getComponent().getClassName());

            HomeActivity homeActivity = Robolectric.buildActivity(HomeActivity.class)
                    .create()
                    .start()
                    .resume()
                    .get();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNotNull(homeActivity.findViewById(R.id.card_prime_survey));

            java.util.List<org.robolectric.shadows.ShadowLog.LogItem> startupLogs =
                    org.robolectric.shadows.ShadowLog.getLogsForTag(TutorEarnApplication.STARTUP_LOG_TAG);
            java.util.List<String> messages = new java.util.ArrayList<>();
            for (org.robolectric.shadows.ShadowLog.LogItem item : startupLogs) {
                messages.add(item.msg);
            }
            String combinedLogs = String.join("\n", messages);
            org.junit.Assert.assertTrue(combinedLogs.contains("Stage 1: Application.onCreate() started"));
            org.junit.Assert.assertTrue(combinedLogs.contains("Stage 2: Firebase initialized"));
            org.junit.Assert.assertTrue(combinedLogs.contains("Stage 3: AdMob APPLICATION_ID meta-data is missing or invalid"));
            org.junit.Assert.assertTrue(combinedLogs.contains("Stage 4: WelcomeActivity.onCreate() started"));
            org.junit.Assert.assertTrue(combinedLogs.contains("Stage 5: WelcomeActivity.onResume() active"));
            org.junit.Assert.assertTrue(combinedLogs.contains("Stage 6: WelcomeActivity navigating to LoginActivity"));
            org.junit.Assert.assertTrue(combinedLogs.contains("Stage 7: HomeActivity.onCreate() started"));
            org.junit.Assert.assertTrue(combinedLogs.contains("Stage 8: HomeActivity.onResume() active"));
        } finally {
            mutablePkgInfo.applicationInfo.metaData.putString("com.google.android.gms.ads.APPLICATION_ID", originalAppId);
            TutorEarnApplication.resetMobileAdsForTest();
        }
    }
}

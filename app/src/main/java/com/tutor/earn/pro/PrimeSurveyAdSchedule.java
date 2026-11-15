package com.tutor.earn.pro;

/**
 * Defines the exact alternating AdMob advertisement schedule for Prime Survey:
 * - Interstitial Ads ONLY after completing questions 2, 4, 6, 8, 10, 12, and 14.
 * - No ads on questions 1, 3, 5, 7, 9, 11, and 13.
 * - No automatic ad on question 15 completion (rewarded ad only on Claim button tap).
 */
public final class PrimeSurveyAdSchedule {

    public static final int TOTAL_QUESTIONS = PrimeSurveyQuestionBank.TOTAL_QUESTIONS;

    private PrimeSurveyAdSchedule() {
    }

    /**
     * Returns true strictly for 1-based question numbers 2, 4, 6, 8, 10, 12, and 14.
     */
    public static boolean shouldShowInterstitialAfterQuestion(int questionNumber) {
        return questionNumber >= 2
                && questionNumber <= 14
                && (questionNumber % 2 == 0);
    }

    /**
     * Returns true when we should ensure an interstitial ad is preloaded in the background
     * ahead of the upcoming even question.
     */
    public static boolean shouldPreloadInterstitialForQuestion(int questionNumber) {
        return questionNumber >= 1 && questionNumber < 14;
    }

    /**
     * Returns false: Question 15 completion must never show an automatic interstitial ad.
     */
    public static boolean shouldShowAutomaticAdOnCompletion() {
        return false;
    }
}

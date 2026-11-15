package com.example;

import androidx.annotation.NonNull;

/**
 * Defines all authorized earning features that credit coins into the single
 * centralized Tutor Earn user coin balance.
 */
public enum CoinEarningSource {
    PRIME_SURVEY("prime_survey"),
    SURVEY_REWARDS("survey_rewards"),
    READ_AND_REWARDS("read_and_rewards"),
    EARN_WITH_AI("earn_with_ai");

    private final String id;

    CoinEarningSource(@NonNull String id) {
        this.id = id;
    }

    @NonNull
    public String getId() {
        return id;
    }
}

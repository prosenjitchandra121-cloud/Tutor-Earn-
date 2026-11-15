package com.tutor.earn.pro;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable model representing one English multiple-choice question in Prime Survey.
 */
public final class PrimeSurveyQuestion {

    private final String id;
    private final String category;
    private final String questionText;
    private final List<String> options;
    private final int correctOptionIndex;
    private final String explanation;

    public PrimeSurveyQuestion(
            @NonNull String id,
            @NonNull String category,
            @NonNull String questionText,
            @NonNull List<String> options,
            int correctOptionIndex,
            @NonNull String explanation
    ) {
        if (options.size() != 4) {
            throw new IllegalArgumentException("PrimeSurveyQuestion must have exactly 4 options.");
        }
        if (correctOptionIndex < 0 || correctOptionIndex >= 4) {
            throw new IllegalArgumentException("correctOptionIndex must be between 0 and 3.");
        }
        this.id = id;
        this.category = category;
        this.questionText = questionText;
        this.options = Collections.unmodifiableList(new ArrayList<>(options));
        this.correctOptionIndex = correctOptionIndex;
        this.explanation = explanation;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getCategory() {
        return category;
    }

    @NonNull
    public String getQuestionText() {
        return questionText;
    }

    @NonNull
    public List<String> getOptions() {
        return options;
    }

    public int getCorrectOptionIndex() {
        return correctOptionIndex;
    }

    @NonNull
    public String getExplanation() {
        return explanation;
    }

    public boolean isCorrectSelection(int selectedIndex) {
        return selectedIndex == correctOptionIndex;
    }
}

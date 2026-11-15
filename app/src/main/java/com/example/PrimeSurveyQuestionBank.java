package com.example;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides 15 original English multiple-choice questions for Prime Survey
 * and ensures no question is repeated within the same daily session.
 */
public final class PrimeSurveyQuestionBank {

    public static final int TOTAL_QUESTIONS = 15;

    private PrimeSurveyQuestionBank() {
    }

    @NonNull
    public static List<PrimeSurveyQuestion> getAllQuestions() {
        List<PrimeSurveyQuestion> questions = new ArrayList<>(TOTAL_QUESTIONS);

        questions.add(new PrimeSurveyQuestion(
                "ps_q01",
                "Grammar",
                "Which sentence uses the subjunctive mood correctly?",
                Arrays.asList(
                        "I wish I was taller so I could reach the shelf.",
                        "If she were in charge, the policy would change.",
                        "He suggested that she arrives early tomorrow.",
                        "It is vital that he studies every single night."
                ),
                1,
                "\"If she were\" expresses a hypothetical condition using the subjunctive \"were\"."
        ));

        questions.add(new PrimeSurveyQuestion(
                "ps_q02",
                "Vocabulary",
                "What is the closest synonym for the word \"ephemeral\"?",
                Arrays.asList(
                        "Fleeting",
                        "Permanent",
                        "Stubborn",
                        "Elaborate"
                ),
                0,
                "\"Ephemeral\" describes something lasting for a very short time, matching \"fleeting\"."
        ));

        questions.add(new PrimeSurveyQuestion(
                "ps_q03",
                "Prepositions",
                "Choose the correct preposition to complete the sentence: \"She is proficient ___ three languages.\"",
                Arrays.asList(
                        "on",
                        "at",
                        "in",
                        "with"
                ),
                2,
                "The adjective \"proficient\" is standardly paired with the preposition \"in\"."
        ));

        questions.add(new PrimeSurveyQuestion(
                "ps_q04",
                "Grammar",
                "Identify the sentence with correct subject-verb agreement.",
                Arrays.asList(
                        "Neither the students nor the teacher were ready.",
                        "The bouquet of fresh roses smell wonderful.",
                        "Each of the participants has received a certificate.",
                        "Ten dollars are too much for a bottle of water."
                ),
                2,
                "\"Each\" is singular and requires the singular verb \"has\"."
        ));

        questions.add(new PrimeSurveyQuestion(
                "ps_q05",
                "Idioms",
                "What does the idiom \"bite the bullet\" mean?",
                Arrays.asList(
                        "To speak harshly without thinking",
                        "To endure a painful or difficult situation bravely",
                        "To give up on a challenging project early",
                        "To celebrate an unexpected victory"
                ),
                1,
                "\"Bite the bullet\" means facing an unpleasant or difficult necessity with courage."
        ));

        questions.add(new PrimeSurveyQuestion(
                "ps_q06",
                "Word Usage",
                "Choose the correct option: \"The new regulations had a positive ___ on student attendance.\"",
                Arrays.asList(
                        "affect",
                        "effect",
                        "affection",
                        "effective"
                ),
                1,
                "\"Effect\" is the noun meaning a result or outcome, whereas \"affect\" is usually a verb."
        ));

        questions.add(new PrimeSurveyQuestion(
                "ps_q07",
                "Antonyms",
                "Which word is the opposite in meaning to \"scarce\"?",
                Arrays.asList(
                        "Fragile",
                        "Abundant",
                        "Narrow",
                        "Obscure"
                ),
                1,
                "\"Scarce\" means insufficient or rare, so its direct antonym is \"abundant\"."
        ));

        questions.add(new PrimeSurveyQuestion(
                "ps_q08",
                "Punctuation",
                "Which sentence uses the apostrophe correctly?",
                Arrays.asList(
                        "The company revised it's annual report yesterday.",
                        "Its' cover was damaged during shipping.",
                        "The dog wagged its tail when it saw its owner.",
                        "Who's notebook was left on the desk?"
                ),
                2,
                "\"Its\" is the possessive pronoun without an apostrophe; \"it's\" means \"it is\"."
        ));

        questions.add(new PrimeSurveyQuestion(
                "ps_q09",
                "Sentence Structure",
                "Which option converts \"The committee approved the proposal\" into the passive voice?",
                Arrays.asList(
                        "The proposal was approved by the committee.",
                        "The committee is approving the proposal.",
                        "The proposal approves the committee.",
                        "The committee had approved the proposal."
                ),
                0,
                "In passive voice, the object \"the proposal\" becomes the subject with \"was approved\"."
        ));

        questions.add(new PrimeSurveyQuestion(
                "ps_q10",
                "Vocabulary",
                "What does the word \"pragmatic\" mean?",
                Arrays.asList(
                        "Overly dramatic and emotional",
                        "Focused on practical results rather than theory",
                        "Unwilling to listen to advice",
                        "Based purely on ancient traditions"
                ),
                1,
                "\"Pragmatic\" means dealing with matters sensibly and realistically in a practical way."
        ));

        questions.add(new PrimeSurveyQuestion(
                "ps_q11",
                "Grammar",
                "Select the sentence that uses \"fewer\" vs. \"less\" accurately.",
                Arrays.asList(
                        "There are less mistakes in your second essay.",
                        "We had fewer time to finish the survey today.",
                        "Fewer applicants attended the morning workshop.",
                        "She drank fewer water after the race."
                ),
                2,
                "\"Fewer\" is used for countable plural nouns like \"applicants\"; \"less\" is for uncountable nouns."
        ));

        questions.add(new PrimeSurveyQuestion(
                "ps_q12",
                "Idioms",
                "If someone \"hits the nail on the head,\" what have they done?",
                Arrays.asList(
                        "Made an avoidable mistake",
                        "Identified the exact cause or truth of a matter",
                        "Delayed an important decision",
                        "Finished manual labor ahead of schedule"
                ),
                1,
                "\"Hit the nail on the head\" means to state or pinpoint something with complete accuracy."
        ));

        questions.add(new PrimeSurveyQuestion(
                "ps_q13",
                "Pronouns",
                "Complete the sentence correctly: \"Between you and ___, this study guide is remarkably clear.\"",
                Arrays.asList(
                        "I",
                        "me",
                        "myself",
                        "mine"
                ),
                1,
                "\"Between\" is a preposition, so it takes the object pronoun \"me\"."
        ));

        questions.add(new PrimeSurveyQuestion(
                "ps_q14",
                "Spelling & Usage",
                "Which word correctly completes the sentence: \"Every researcher must ___ credible sources\"?",
                Arrays.asList(
                        "site",
                        "sight",
                        "cite",
                        "slight"
                ),
                2,
                "\"Cite\" means to quote or reference an authority or source."
        ));

        questions.add(new PrimeSurveyQuestion(
                "ps_q15",
                "Comprehension",
                "Read the statement: \"Although the forecast predicted rain, the outdoor ceremony proceeded smoothly under clear skies.\" What can be inferred?",
                Arrays.asList(
                        "The ceremony was moved indoors at the last minute.",
                        "The weather turned out better than expected.",
                        "The organizers ignored safety guidelines.",
                        "Rain interrupted the middle of the ceremony."
                ),
                1,
                "\"Clear skies\" despite a rain forecast indicates the weather was better than predicted."
        ));

        return Collections.unmodifiableList(questions);
    }

    /**
     * Builds a non-repeating ordered list of questions for the current daily session.
     * Unanswered questions appear first (shuffled), followed by any remaining unique
     * questions only if a completed session is revisited for practice, guaranteeing
     * zero duplicate IDs in the 15-question session.
     */
    @NonNull
    public static List<PrimeSurveyQuestion> buildSessionQuestions(
            @NonNull Set<String> answeredIdsInSession,
            boolean shuffleOrder
    ) {
        List<PrimeSurveyQuestion> all = new ArrayList<>(getAllQuestions());
        if (shuffleOrder) {
            Collections.shuffle(all);
        }

        List<PrimeSurveyQuestion> remaining = new ArrayList<>(TOTAL_QUESTIONS);
        List<PrimeSurveyQuestion> alreadyAnswered = new ArrayList<>(TOTAL_QUESTIONS);
        Set<String> dedupe = new HashSet<>();

        for (PrimeSurveyQuestion q : all) {
            if (!dedupe.add(q.getId())) {
                continue;
            }
            if (answeredIdsInSession.contains(q.getId())) {
                alreadyAnswered.add(q);
            } else {
                remaining.add(q);
            }
        }

        List<PrimeSurveyQuestion> sessionList = new ArrayList<>(TOTAL_QUESTIONS);
        sessionList.addAll(remaining);
        if (sessionList.size() < TOTAL_QUESTIONS) {
            sessionList.addAll(alreadyAnswered);
        }
        return Collections.unmodifiableList(sessionList);
    }
}

package com.bajaj.quiz.model;

public record SubmitResponse(Boolean isCorrect,
                             Boolean isIdempotent,
                             Integer submittedTotal,
                             Integer expectedTotal,
                             String message,
                             String regNo,
                             Integer totalPollsMade,
                             Integer attemptCount) {

    public boolean hasValidationVerdict() {
        return isCorrect != null || isIdempotent != null || expectedTotal != null || message != null;
    }
}

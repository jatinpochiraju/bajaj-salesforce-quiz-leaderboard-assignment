package com.bajaj.quiz.api;

import com.bajaj.quiz.model.PollResponse;
import com.bajaj.quiz.model.SubmitRequest;
import com.bajaj.quiz.model.SubmitResponse;

import java.io.IOException;

public interface QuizApiClient {
    PollResponse fetchMessages(String regNo, int pollIndex) throws IOException, InterruptedException;

    SubmitResponse submitLeaderboard(SubmitRequest request) throws IOException, InterruptedException;
}

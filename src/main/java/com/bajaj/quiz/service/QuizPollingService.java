package com.bajaj.quiz.service;

import com.bajaj.quiz.api.QuizApiClient;
import com.bajaj.quiz.core.AggregationResult;
import com.bajaj.quiz.core.LeaderboardAggregator;
import com.bajaj.quiz.model.PollResponse;
import com.bajaj.quiz.model.SubmitRequest;
import com.bajaj.quiz.model.SubmitResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class QuizPollingService {
    private static final int POLL_COUNT = 10;
    private static final long POLL_DELAY_MILLIS = 5_000L;

    private final QuizApiClient apiClient;
    private final LeaderboardAggregator aggregator;
    private final Sleeper sleeper;
    private final AtomicBoolean submitGuard = new AtomicBoolean(false);

    public QuizPollingService(QuizApiClient apiClient, LeaderboardAggregator aggregator, Sleeper sleeper) {
        this.apiClient = Objects.requireNonNull(apiClient, "apiClient must not be null");
        this.aggregator = Objects.requireNonNull(aggregator, "aggregator must not be null");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper must not be null");
    }

    public QuizRunResult runOnce(String regNo) throws IOException, InterruptedException {
        if (!submitGuard.compareAndSet(false, true)) {
            throw new IllegalStateException("Submission already performed for this service instance");
        }

        List<PollResponse> responses = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        for (int pollIndex = 0; pollIndex < POLL_COUNT; pollIndex++) {
            PollResponse response = apiClient.fetchMessages(regNo, pollIndex);
            responses.add(response);
            indices.add(pollIndex);

            if (pollIndex < POLL_COUNT - 1) {
                sleeper.sleepMillis(POLL_DELAY_MILLIS);
            }
        }

        AggregationResult aggregationResult = aggregator.aggregate(responses);
        SubmitRequest request = new SubmitRequest(regNo, aggregationResult.leaderboard());
        SubmitResponse submitResponse = apiClient.submitLeaderboard(request);

        return new QuizRunResult(indices, aggregationResult, submitResponse);
    }
}

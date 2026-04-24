package com.bajaj.quiz.service;

import com.bajaj.quiz.api.QuizApiClient;
import com.bajaj.quiz.core.LeaderboardAggregator;
import com.bajaj.quiz.model.Event;
import com.bajaj.quiz.model.PollResponse;
import com.bajaj.quiz.model.SubmitRequest;
import com.bajaj.quiz.model.SubmitResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuizPollingServiceTest {

    @Test
    void runOnce_shouldPollAllIndicesDelayAndSubmitOneTime() throws IOException, InterruptedException {
        FakeQuizApiClient client = new FakeQuizApiClient();
        FakeSleeper sleeper = new FakeSleeper();
        QuizPollingService service = new QuizPollingService(client, new LeaderboardAggregator(), sleeper);

        QuizRunResult result = service.runOnce("2024CS101");

        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), client.requestedPolls);
        assertEquals(9, sleeper.calls.size());
        assertEquals(5_000L, sleeper.calls.get(0));
        assertEquals(2, result.aggregationResult().leaderboard().size());
        assertEquals(50, result.aggregationResult().totalScore());
        assertEquals(1, client.submitRequests.size());

        assertThrows(IllegalStateException.class, () -> service.runOnce("2024CS101"));
    }

    private static class FakeSleeper implements Sleeper {
        private final List<Long> calls = new ArrayList<>();

        @Override
        public void sleepMillis(long millis) {
            calls.add(millis);
        }
    }

    private static class FakeQuizApiClient implements QuizApiClient {
        private final List<Integer> requestedPolls = new ArrayList<>();
        private final List<SubmitRequest> submitRequests = new ArrayList<>();

        @Override
        public PollResponse fetchMessages(String regNo, int pollIndex) {
            requestedPolls.add(pollIndex);

            if (pollIndex % 2 == 0) {
                return new PollResponse(regNo, "SET_1", pollIndex, List.of(
                        new Event("R1", "Alice", 10),
                        new Event("R1", "Bob", 20)
                ));
            }

            return new PollResponse(regNo, "SET_1", pollIndex, List.of(
                    new Event("R2", "Alice", 5),
                    new Event("R2", "Bob", 15)
            ));
        }

        @Override
        public SubmitResponse submitLeaderboard(SubmitRequest request) {
            submitRequests.add(request);
            int total = request.leaderboard().stream().mapToInt(entry -> entry.totalScore()).sum();
            return new SubmitResponse(true, true, total, total, "Correct!", "2024CS101", 10, 1);
        }
    }
}

package com.bajaj.quiz.core;

import com.bajaj.quiz.model.Event;
import com.bajaj.quiz.model.LeaderboardEntry;
import com.bajaj.quiz.model.PollResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeaderboardAggregatorTest {

    private final LeaderboardAggregator aggregator = new LeaderboardAggregator();

    @Test
    void aggregate_shouldDeduplicateByRoundAndParticipant() {
        PollResponse poll0 = new PollResponse("2024CS101", "SET_1", 0, List.of(
                new Event("R1", "Alice", 10),
                new Event("R1", "Bob", 20)
        ));

        PollResponse poll1 = new PollResponse("2024CS101", "SET_1", 1, List.of(
                new Event("R1", "Alice", 10),
                new Event("R2", "Alice", 15),
                new Event("R2", "Bob", 25)
        ));

        AggregationResult result = aggregator.aggregate(List.of(poll0, poll1));

        assertEquals(List.of(
                new LeaderboardEntry("Bob", 45),
                new LeaderboardEntry("Alice", 25)
        ), result.leaderboard());
        assertEquals(70, result.totalScore());
    }

    @Test
    void aggregate_shouldUseParticipantAscendingTieBreaker() {
        PollResponse poll = new PollResponse("2024CS101", "SET_1", 0, List.of(
                new Event("R1", "Charlie", 10),
                new Event("R2", "Alice", 10)
        ));

        AggregationResult result = aggregator.aggregate(List.of(poll));

        assertEquals(List.of(
                new LeaderboardEntry("Alice", 10),
                new LeaderboardEntry("Charlie", 10)
        ), result.leaderboard());
    }
}

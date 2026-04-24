package com.bajaj.quiz.core;

import com.bajaj.quiz.model.Event;
import com.bajaj.quiz.model.LeaderboardEntry;
import com.bajaj.quiz.model.PollResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class LeaderboardAggregator {

    public AggregationResult aggregate(List<PollResponse> responses) {
        Objects.requireNonNull(responses, "responses must not be null");

        Set<EventKey> seen = new HashSet<>();
        Map<String, Integer> totalsByParticipant = new HashMap<>();

        for (PollResponse response : responses) {
            if (response == null || response.events() == null) {
                continue;
            }
            for (Event event : response.events()) {
                if (event == null || event.roundId() == null || event.participant() == null) {
                    continue;
                }
                EventKey key = new EventKey(event.roundId(), event.participant());
                if (seen.add(key)) {
                    totalsByParticipant.merge(event.participant(), event.score(), Integer::sum);
                }
            }
        }

        List<LeaderboardEntry> leaderboard = new ArrayList<>(totalsByParticipant.entrySet().stream()
                .map(entry -> new LeaderboardEntry(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(LeaderboardEntry::totalScore)
                        .reversed()
                        .thenComparing(LeaderboardEntry::participant))
                .toList());

        int totalScore = leaderboard.stream().mapToInt(LeaderboardEntry::totalScore).sum();
        return new AggregationResult(leaderboard, totalScore);
    }

    private record EventKey(String roundId, String participant) {
    }
}

package com.bajaj.quiz.core;

import com.bajaj.quiz.model.LeaderboardEntry;

import java.util.List;

public record AggregationResult(List<LeaderboardEntry> leaderboard, int totalScore) {
}

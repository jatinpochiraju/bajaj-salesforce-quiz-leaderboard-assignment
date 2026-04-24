package com.bajaj.quiz.model;

import java.util.List;

public record SubmitRequest(String regNo, List<LeaderboardEntry> leaderboard) {
}

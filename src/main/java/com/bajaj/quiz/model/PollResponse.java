package com.bajaj.quiz.model;

import java.util.List;

public record PollResponse(String regNo, String setId, int pollIndex, List<Event> events) {
}

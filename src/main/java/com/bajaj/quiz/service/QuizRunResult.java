package com.bajaj.quiz.service;

import com.bajaj.quiz.core.AggregationResult;
import com.bajaj.quiz.model.SubmitResponse;

import java.util.List;

public record QuizRunResult(List<Integer> polledIndices,
                            AggregationResult aggregationResult,
                            SubmitResponse submitResponse) {
}

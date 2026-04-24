package com.bajaj.quiz;

import com.bajaj.quiz.api.HttpQuizApiClient;
import com.bajaj.quiz.api.HttpQuizApiClient.RetryConfig;
import com.bajaj.quiz.core.LeaderboardAggregator;
import com.bajaj.quiz.service.QuizPollingService;
import com.bajaj.quiz.service.QuizRunResult;
import com.bajaj.quiz.service.ThreadSleeper;

import java.time.Duration;
import java.util.Arrays;

public class Main {

    private static final String DEFAULT_BASE_URL = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";
    private static final long DEFAULT_TIMEOUT_SECONDS = 30L;
    private static final int DEFAULT_RETRY_MAX_ATTEMPTS = 3;
    private static final long DEFAULT_INITIAL_BACKOFF_MILLIS = 500L;
    private static final long DEFAULT_MAX_BACKOFF_MILLIS = 4_000L;

    public static void main(String[] args) throws Exception {
        String regNo = resolveRegNo(args);
        String baseUrl = System.getenv().getOrDefault("QUIZ_BASE_URL", DEFAULT_BASE_URL);
        long timeoutSeconds = resolveTimeoutSeconds();
        RetryConfig retryConfig = resolveRetryConfig();

        HttpQuizApiClient apiClient = new HttpQuizApiClient(baseUrl, Duration.ofSeconds(timeoutSeconds), retryConfig);
        QuizPollingService service = new QuizPollingService(apiClient, new LeaderboardAggregator(), new ThreadSleeper());

        QuizRunResult result = service.runOnce(regNo);

        System.out.println("Polled indices: " + result.polledIndices());
        System.out.println("Leaderboard: " + result.aggregationResult().leaderboard());
        System.out.println("Total score: " + result.aggregationResult().totalScore());
        System.out.println("Submission response: " + result.submitResponse());
        if (!result.submitResponse().hasValidationVerdict()) {
            System.out.println("Note: submit API returned acknowledgement fields only (no correctness verdict fields).");
        }
    }

    private static String resolveRegNo(String[] args) {
        String fromArgs = Arrays.stream(args)
                .filter(arg -> arg.startsWith("--regNo="))
                .map(arg -> arg.substring("--regNo=".length()))
                .findFirst()
                .orElse(null);

        if (fromArgs != null && !fromArgs.isBlank()) {
            return fromArgs;
        }

        String fromEnv = System.getenv("REG_NO");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }

        throw new IllegalArgumentException("Missing regNo. Pass --regNo=<your_reg_no> or set REG_NO environment variable.");
    }

    private static long resolveTimeoutSeconds() {
        String timeout = System.getenv("QUIZ_API_TIMEOUT_SECONDS");
        if (timeout == null || timeout.isBlank()) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        return Long.parseLong(timeout.trim());
    }

    private static RetryConfig resolveRetryConfig() {
        int maxAttempts = resolveInt("QUIZ_API_MAX_ATTEMPTS", DEFAULT_RETRY_MAX_ATTEMPTS);
        long initialBackoffMillis = resolveLong("QUIZ_API_INITIAL_BACKOFF_MILLIS", DEFAULT_INITIAL_BACKOFF_MILLIS);
        long maxBackoffMillis = resolveLong("QUIZ_API_MAX_BACKOFF_MILLIS", DEFAULT_MAX_BACKOFF_MILLIS);
        return new RetryConfig(maxAttempts, initialBackoffMillis, maxBackoffMillis);
    }

    private static int resolveInt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }

    private static long resolveLong(String key, long defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Long.parseLong(value.trim());
    }
}

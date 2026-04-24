package com.bajaj.quiz.api;

import com.bajaj.quiz.model.PollResponse;
import com.bajaj.quiz.model.SubmitRequest;
import com.bajaj.quiz.model.SubmitResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpQuizApiClient implements QuizApiClient {
    private static final Logger LOGGER = Logger.getLogger(HttpQuizApiClient.class.getName());
    private static final int MAX_LOG_BODY_CHARS = 400;

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RetryConfig retryConfig;

    public HttpQuizApiClient(String baseUrl, Duration timeout) {
        this(baseUrl, timeout, RetryConfig.defaults());
    }

    public HttpQuizApiClient(String baseUrl, Duration timeout, RetryConfig retryConfig) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null").replaceAll("/$", "");
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.retryConfig = Objects.requireNonNull(retryConfig, "retryConfig must not be null");
    }

    @Override
    public PollResponse fetchMessages(String regNo, int pollIndex) throws IOException, InterruptedException {
        String encodedRegNo = URLEncoder.encode(regNo, StandardCharsets.UTF_8);
        URI uri = URI.create(baseUrl + "/quiz/messages?regNo=" + encodedRegNo + "&poll=" + pollIndex);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = sendWithRetry(request, "GET /quiz/messages", null);

        return objectMapper.readValue(response.body(), PollResponse.class);
    }

    @Override
    public SubmitResponse submitLeaderboard(SubmitRequest requestPayload) throws IOException, InterruptedException {
        URI uri = URI.create(baseUrl + "/quiz/submit");
        String requestJson = objectMapper.writeValueAsString(requestPayload);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = sendWithRetry(request, "POST /quiz/submit", requestJson);

        return objectMapper.readValue(response.body(), SubmitResponse.class);
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request,
                                               String operation,
                                               String requestBodyPreview) throws IOException, InterruptedException {
        IOException lastIoException = null;
        IllegalStateException lastHttpException = null;

        for (int attempt = 1; attempt <= retryConfig.maxAttempts(); attempt++) {
            Instant startedAt = Instant.now();
            logRequest(operation, request, attempt, requestBodyPreview);
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis();
                logResponse(operation, request, attempt, response.statusCode(), response.body(), elapsedMillis);

                if (isSuccess(response.statusCode())) {
                    return response;
                }

                String message = operation + " failed with status " + response.statusCode() + ". Body: " + response.body();
                lastHttpException = new IllegalStateException(message);
                if (!isTransientStatus(response.statusCode()) || attempt == retryConfig.maxAttempts()) {
                    throw lastHttpException;
                }
            } catch (IOException ioException) {
                lastIoException = ioException;
                LOGGER.log(Level.WARNING, operation + " attempt " + attempt + " failed due to I/O error: " + ioException.getMessage());
                if (attempt == retryConfig.maxAttempts()) {
                    throw ioException;
                }
            }

            long backoffMillis = computeBackoffMillis(attempt);
            LOGGER.info(operation + " retrying in " + backoffMillis + " ms (attempt " + (attempt + 1) + "/" + retryConfig.maxAttempts() + ")");
            Thread.sleep(backoffMillis);
        }

        if (lastIoException != null) {
            throw lastIoException;
        }
        if (lastHttpException != null) {
            throw lastHttpException;
        }
        throw new IllegalStateException(operation + " failed after retry attempts");
    }

    private boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private boolean isTransientStatus(int statusCode) {
        return statusCode == 408 || statusCode == 429 || (statusCode >= 500 && statusCode <= 599);
    }

    private long computeBackoffMillis(int attempt) {
        long exponential = retryConfig.initialBackoffMillis() * (1L << Math.max(0, attempt - 1));
        return Math.min(exponential, retryConfig.maxBackoffMillis());
    }

    private void logRequest(String operation, HttpRequest request, int attempt, String requestBodyPreview) {
        String bodyMessage = requestBodyPreview == null ? "" : ", body=" + abbreviate(requestBodyPreview);
        LOGGER.info(() -> operation + " request attempt=" + attempt + " method=" + request.method() + " uri=" + request.uri() + bodyMessage);
    }

    private void logResponse(String operation,
                             HttpRequest request,
                             int attempt,
                             int status,
                             String responseBody,
                             long elapsedMillis) {
        LOGGER.info(() -> operation + " response attempt=" + attempt
                + " method=" + request.method()
                + " uri=" + request.uri()
                + " status=" + status
                + " durationMs=" + elapsedMillis
                + " body=" + abbreviate(responseBody));
    }

    private String abbreviate(String body) {
        if (body == null) {
            return "null";
        }
        if (body.length() <= MAX_LOG_BODY_CHARS) {
            return body;
        }
        return body.substring(0, MAX_LOG_BODY_CHARS) + "...";
    }

    public record RetryConfig(int maxAttempts, long initialBackoffMillis, long maxBackoffMillis) {
        public RetryConfig {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be at least 1");
            }
            if (initialBackoffMillis < 0 || maxBackoffMillis < 0) {
                throw new IllegalArgumentException("backoff values must be non-negative");
            }
            if (initialBackoffMillis > maxBackoffMillis) {
                throw new IllegalArgumentException("initialBackoffMillis must be <= maxBackoffMillis");
            }
        }

        public static RetryConfig defaults() {
            return new RetryConfig(3, 500L, 4_000L);
        }
    }
}

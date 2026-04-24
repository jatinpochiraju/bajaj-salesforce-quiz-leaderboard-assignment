package com.bajaj.quiz.api;

import com.bajaj.quiz.model.PollResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpQuizApiClientTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchMessages_shouldRetryOnTransientHttpStatus() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        server.createContext("/quiz/messages", exchange -> {
            int attempt = requestCount.incrementAndGet();
            if (attempt < 3) {
                writeJson(exchange, 500, "{\"error\":\"temporary\"}");
                return;
            }
            writeJson(exchange, 200,
                    "{\"regNo\":\"2024CS101\",\"setId\":\"SET_1\",\"pollIndex\":0,\"events\":[{\"roundId\":\"R1\",\"participant\":\"Alice\",\"score\":10}]}");
        });

        HttpQuizApiClient client = new HttpQuizApiClient(
                baseUrl,
                Duration.ofSeconds(2),
                new HttpQuizApiClient.RetryConfig(3, 1, 4)
        );

        PollResponse response = client.fetchMessages("2024CS101", 0);

        assertEquals(3, requestCount.get());
        assertEquals("2024CS101", response.regNo());
        assertEquals(1, response.events().size());
    }

    @Test
    void fetchMessages_shouldNotRetryOnNonTransientHttpStatus() {
        AtomicInteger requestCount = new AtomicInteger();
        server.createContext("/quiz/messages", exchange -> {
            requestCount.incrementAndGet();
            writeJson(exchange, 400, "{\"error\":\"bad request\"}");
        });

        HttpQuizApiClient client = new HttpQuizApiClient(
                baseUrl,
                Duration.ofSeconds(2),
                new HttpQuizApiClient.RetryConfig(3, 1, 4)
        );

        assertThrows(IllegalStateException.class, () -> client.fetchMessages("2024CS101", 0));
        assertEquals(1, requestCount.get());
    }

    private void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }
}

package com.spendwise.categorization;

import com.spendwise.categorization.dto.MlNormalizeEntry;
import com.spendwise.categorization.dto.MlNormalizeRecipientsRequest;
import com.spendwise.categorization.dto.MlNormalizeRecipientsResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression coverage for the 2026-07-19 "content type [application/octet-stream]" failure: a real
 * server (ml/, uvicorn) closes idle keep-alive sockets after its default 5s timeout without telling
 * the client; JDK HttpURLConnection's KeepAliveCache (used by SimpleClientHttpRequestFactory, which
 * backs {@link MlClient}) doesn't validate a pooled socket before reusing it, so any admin-triggered
 * call spaced more than a few seconds after the previous one reused an already-closed socket and blew
 * up with an unreadable response. The fix is the "Connection: close" default header in {@link
 * MlClient}'s constructor, which makes HttpURLConnection skip its keep-alive cache for these calls
 * entirely. This test reproduces the failure mode directly against a raw socket server that mimics
 * uvicorn's behavior (accept, respond, then actually close the TCP connection) rather than mocking it,
 * so it fails if the header regresses.
 */
class MlClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void everyRequestCarriesConnectionCloseSoTheJdkNeverPoolsTheSocket() throws IOException {
        var receivedHeaders = new java.util.concurrent.atomic.AtomicReference<String>();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/normalize-recipients", exchange -> {
            receivedHeaders.set(exchange.getRequestHeaders().getFirst("Connection"));
            byte[] body = "{\"canonical_names\":{},\"ambiguous_groups\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();

        MlClient client = new MlClient(
                RestClient.builder(), "http://localhost:" + server.getAddress().getPort(), "test-internal-key");

        MlNormalizeRecipientsResponse response = client.normalizeRecipients(
                new MlNormalizeRecipientsRequest(List.of(new MlNormalizeEntry("k1", "SOME MERCHANT", null))));

        assertThat(response.canonicalNames()).isEmpty();
        assertThat(receivedHeaders.get()).isEqualToIgnoringCase("close");
    }

    @Test
    void survivesCallingAfterTheServerHasAlreadyClosedTheUnderlyingSocket() throws IOException {
        // Mimics uvicorn's real behavior: accept a connection, answer exactly one request on it,
        // then actually close the TCP socket -- the exact scenario a pooled, reused HttpURLConnection
        // would previously write into and get the unreadable response that surfaced as the
        // "content type [application/octet-stream]" error.
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/normalize-recipients", exchange -> {
            byte[] body = "{\"canonical_names\":{},\"ambiguous_groups\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();

        MlClient client = new MlClient(
                RestClient.builder(), "http://localhost:" + server.getAddress().getPort(), "test-internal-key");
        MlNormalizeRecipientsRequest request = new MlNormalizeRecipientsRequest(
                List.of(new MlNormalizeEntry("k1", "SOME MERCHANT", null)));

        client.normalizeRecipients(request);

        // A raw socket left connected long enough to be closed by most idle-timeout policies --
        // proves nothing about MlClient by itself, it's just establishing the same timing gap the
        // real failure needed. The actual proof is the second MlClient call below succeeding: with
        // "Connection: close" in place it never touches a pooled socket at all, so there is nothing
        // for a server-side close to break.
        try (Socket probe = new Socket("localhost", server.getAddress().getPort())) {
            probe.setSoTimeout(50);
        }

        assertThatCode(() -> client.normalizeRecipients(request)).doesNotThrowAnyException();
    }
}

package com.pqcenter.tarsmobile;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

final class TarsApiClient {
    interface EventCallback {
        void onOpen();
        void onEvent(SseEvent event);
        void onError(Exception exception);
    }

    interface StreamHandle {
        void cancel();
    }

    private final String baseUrl;
    private final String relayToken;
    private final String relayAgentId;
    private final String relayClientId;

    TarsApiClient(AppSettings settings) {
        this.baseUrl = settings.normalizedRelayBaseUrl();
        this.relayToken = nullToEmpty(settings.relayToken).trim();
        this.relayAgentId = defaultIfBlank(settings.relayAgentId, "default");
        this.relayClientId = nullToEmpty(settings.relayClientId).trim();
    }

    void submitMessage(String message, String sessionId) throws IOException {
        if (relayClientId.isEmpty()) {
            throw new IOException("Relay client ID is required.");
        }

        String body = "{"
            + "\"clientId\":" + JSONObject.quote(relayClientId) + ","
            + "\"message\":" + JSONObject.quote(message)
            + "}";
        String path = "/integrations/mobile/relay/agents/"
            + pathEscape(relayAgentId)
            + "/sessions/"
            + pathEscape(sessionId)
            + "/messages";
        HttpURLConnection connection = openConnection(path, "POST", "application/json");

        try {
            connection.setDoOutput(true);
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8))) {
                writer.write(body);
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw httpError(status);
            }
        } finally {
            connection.disconnect();
        }
    }

    void requestSessionSnapshot(String sessionId) throws IOException {
        if (relayClientId.isEmpty()) {
            throw new IOException("Relay client ID is required.");
        }

        String body = "{"
            + "\"clientId\":" + JSONObject.quote(relayClientId)
            + "}";
        String path = "/integrations/mobile/relay/agents/"
            + pathEscape(relayAgentId)
            + "/sessions/"
            + pathEscape(sessionId)
            + "/snapshot";
        HttpURLConnection connection = openConnection(path, "POST", "application/json");

        try {
            connection.setDoOutput(true);
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8))) {
                writer.write(body);
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw httpError(status);
            }
        } finally {
            connection.disconnect();
        }
    }

    StreamHandle streamSessionEvents(EventCallback callback) {
        RelayStreamHandle handle = new RelayStreamHandle();
        Thread thread = new Thread(() -> {
            while (!handle.cancelled.get()) {
                HttpURLConnection connection = null;

                try {
                    String path = "/integrations/mobile/relay/clients/"
                        + pathEscape(relayClientId)
                        + "/events";
                    connection = openConnection(path, "GET", "text/event-stream");
                    handle.connection = connection;
                    connection.setReadTimeout(0);

                    int status = connection.getResponseCode();
                    if (status < 200 || status >= 300) {
                        throw httpError(status);
                    }

                    callback.onOpen();
                    readSseStream(connection.getInputStream(), handle.cancelled, callback);
                } catch (Exception exception) {
                    if (!handle.cancelled.get()) {
                        callback.onError(exception);
                        sleepBeforeReconnect(handle.cancelled);
                    }
                } finally {
                    handle.connection = null;
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        }, "tars-relay-sse");

        handle.thread = thread;
        thread.start();
        return handle;
    }

    private void readSseStream(InputStream inputStream, AtomicBoolean cancelled, EventCallback callback) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            SseAccumulator accumulator = new SseAccumulator();
            String line;

            while (!cancelled.get() && (line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    SseEvent event = accumulator.makeEventOrNull();
                    if (event != null) {
                        callback.onEvent(event);
                    }
                    accumulator = new SseAccumulator();
                    continue;
                }

                accumulator.appendLine(line);
            }

            SseEvent trailingEvent = accumulator.makeEventOrNull();
            if (trailingEvent != null && !cancelled.get()) {
                callback.onEvent(trailingEvent);
            }
        }
    }

    private HttpURLConnection openConnection(String path, String method, String accept) throws IOException {
        if (baseUrl.isEmpty()) {
            throw new IOException("Set a valid Relay URL in Settings.");
        }

        HttpURLConnection connection = (HttpURLConnection) URI.create(baseUrl + path).toURL().openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        if (!relayToken.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + relayToken);
        }

        return connection;
    }

    private static IOException httpError(int status) {
        if (status == 404) {
            return new IOException("The relay returned HTTP 404. Redeploy tars-relay with mobile relay endpoints or check the Relay URL.");
        }

        return new IOException("The relay returned HTTP " + status + ".");
    }

    private static String pathEscape(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                .replace("+", "%20");
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String defaultIfBlank(String value, String fallback) {
        String trimmed = nullToEmpty(value).trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void sleepBeforeReconnect(AtomicBoolean cancelled) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {
            cancelled.set(true);
            Thread.currentThread().interrupt();
        }
    }

    private static final class RelayStreamHandle implements StreamHandle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile Thread thread;
        private volatile HttpURLConnection connection;

        @Override
        public void cancel() {
            cancelled.set(true);

            HttpURLConnection currentConnection = connection;
            if (currentConnection != null) {
                currentConnection.disconnect();
            }

            Thread currentThread = thread;
            if (currentThread != null) {
                currentThread.interrupt();
            }
        }
    }
}

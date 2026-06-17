package com.pqcenter.tarsmobile;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

final class AppSettings {
    static final String RELAY_MODE_CLOUD = "cloud";
    static final String RELAY_MODE_LOCAL_LAN = "local-lan";
    static final String RELAY_MODE_EMULATOR = "emulator";
    static final String RELAY_MODE_CUSTOM = "custom";
    static final String DEFAULT_CLOUD_RELAY_BASE_URL = "https://tarsrelay.pqcenter.cn";
    static final String DEFAULT_LOCAL_LAN_RELAY_BASE_URL = "http://192.168.1.20:18992";
    static final String DEFAULT_EMULATOR_RELAY_BASE_URL = "http://10.0.2.2:18992";
    private static final String PREFS_NAME = "tars_mobile_settings";
    private static final String KEY_RELAY_MODE = "relayMode";
    private static final String KEY_RELAY_BASE_URL = "relayBaseURL";
    private static final String KEY_SESSION_ID = "sessionID";
    private static final String KEY_RELAY_TOKEN = "relayToken";
    private static final String KEY_RELAY_AGENT_ID = "relayAgentID";
    private static final String KEY_RELAY_CLIENT_ID = "relayClientID";

    private final SharedPreferences preferences;

    String relayMode;
    String relayBaseUrl;
    String sessionId;
    String relayToken;
    String relayAgentId;
    String relayClientId;

    AppSettings(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        relayBaseUrl = preferences.getString(KEY_RELAY_BASE_URL, DEFAULT_CLOUD_RELAY_BASE_URL);
        relayMode = preferences.getString(KEY_RELAY_MODE, inferRelayMode(relayBaseUrl));
        sessionId = preferences.getString(KEY_SESSION_ID, "mobile-main");
        relayToken = preferences.getString(KEY_RELAY_TOKEN, "");
        relayAgentId = preferences.getString(KEY_RELAY_AGENT_ID, "default");
        relayClientId = preferences.getString(KEY_RELAY_CLIENT_ID, null);

        if (relayClientId == null || relayClientId.trim().isEmpty()) {
            relayClientId = "android-" + UUID.randomUUID();
            preferences.edit().putString(KEY_RELAY_CLIENT_ID, relayClientId).apply();
        }

        if (!preferences.contains(KEY_RELAY_BASE_URL)) {
            preferences.edit().putString(KEY_RELAY_BASE_URL, relayBaseUrl).apply();
        }

        if (!preferences.contains(KEY_RELAY_MODE)) {
            preferences.edit().putString(KEY_RELAY_MODE, relayMode).apply();
        }

        relayToken = relayToken == null ? "" : relayToken;
    }

    void save() {
        relayMode = normalizeRelayMode(relayMode);
        preferences.edit()
            .putString(KEY_RELAY_MODE, relayMode)
            .putString(KEY_RELAY_BASE_URL, relayBaseUrl.trim())
            .putString(KEY_SESSION_ID, sessionId.trim())
            .putString(KEY_RELAY_TOKEN, relayToken.trim())
            .putString(KEY_RELAY_AGENT_ID, relayAgentId.trim())
            .putString(KEY_RELAY_CLIENT_ID, relayClientId.trim())
            .apply();
    }

    String normalizedRelayBaseUrl() {
        String trimmed = relayBaseUrl == null ? "" : relayBaseUrl.trim();

        while (trimmed.length() > 1 && trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        return trimmed;
    }

    String relayModeLabel() {
        switch (normalizeRelayMode(relayMode)) {
            case RELAY_MODE_LOCAL_LAN:
                return "Local LAN relay";
            case RELAY_MODE_EMULATOR:
                return "Android emulator relay";
            case RELAY_MODE_CUSTOM:
                return "Custom relay";
            case RELAY_MODE_CLOUD:
            default:
                return "Cloud relay";
        }
    }

    static String relayUrlForMode(String mode, String currentUrl) {
        String normalizedMode = normalizeRelayMode(mode);
        String normalizedCurrent = nullToEmpty(currentUrl).trim();

        if (RELAY_MODE_CLOUD.equals(normalizedMode)) {
            return DEFAULT_CLOUD_RELAY_BASE_URL;
        }

        if (RELAY_MODE_EMULATOR.equals(normalizedMode)) {
            return DEFAULT_EMULATOR_RELAY_BASE_URL;
        }

        if (RELAY_MODE_LOCAL_LAN.equals(normalizedMode)) {
            if (
                normalizedCurrent.isEmpty()
                || DEFAULT_CLOUD_RELAY_BASE_URL.equals(normalizedCurrent)
                || DEFAULT_EMULATOR_RELAY_BASE_URL.equals(normalizedCurrent)
            ) {
                return DEFAULT_LOCAL_LAN_RELAY_BASE_URL;
            }

            return normalizedCurrent;
        }

        return normalizedCurrent;
    }

    static String normalizeRelayMode(String value) {
        if (
            RELAY_MODE_LOCAL_LAN.equals(value)
            || RELAY_MODE_EMULATOR.equals(value)
            || RELAY_MODE_CUSTOM.equals(value)
            || RELAY_MODE_CLOUD.equals(value)
        ) {
            return value;
        }

        return RELAY_MODE_CLOUD;
    }

    String fingerprint() {
        return String.join(
            "\u001f",
            nullToEmpty(relayMode),
            nullToEmpty(relayBaseUrl),
            nullToEmpty(sessionId),
            nullToEmpty(relayToken),
            nullToEmpty(relayAgentId),
            nullToEmpty(relayClientId)
        );
    }

    private static String inferRelayMode(String baseUrl) {
        String normalized = nullToEmpty(baseUrl).trim();

        if (DEFAULT_CLOUD_RELAY_BASE_URL.equals(normalized)) {
            return RELAY_MODE_CLOUD;
        }

        if (DEFAULT_EMULATOR_RELAY_BASE_URL.equals(normalized)) {
            return RELAY_MODE_EMULATOR;
        }

        if (normalized.startsWith("http://")) {
            return RELAY_MODE_LOCAL_LAN;
        }

        return RELAY_MODE_CUSTOM;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

package com.pqcenter.tarsmobile;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

final class AppSettings {
    private static final String DEFAULT_RELAY_TOKEN = "27099ea0126b1a55330ae4a9f5d3af5d890e532035d233adfb9893b5a9206c32";
    private static final String PREFS_NAME = "tars_mobile_settings";
    private static final String KEY_RELAY_BASE_URL = "relayBaseURL";
    private static final String KEY_SESSION_ID = "sessionID";
    private static final String KEY_RELAY_TOKEN = "relayToken";
    private static final String KEY_RELAY_AGENT_ID = "relayAgentID";
    private static final String KEY_RELAY_CLIENT_ID = "relayClientID";

    private final SharedPreferences preferences;

    String relayBaseUrl;
    String sessionId;
    String relayToken;
    String relayAgentId;
    String relayClientId;

    AppSettings(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        relayBaseUrl = preferences.getString(KEY_RELAY_BASE_URL, "https://tarsrelay.pqcenter.cn");
        sessionId = preferences.getString(KEY_SESSION_ID, "mobile-main");
        relayToken = preferences.getString(KEY_RELAY_TOKEN, DEFAULT_RELAY_TOKEN);
        relayAgentId = preferences.getString(KEY_RELAY_AGENT_ID, "default");
        relayClientId = preferences.getString(KEY_RELAY_CLIENT_ID, null);

        if (relayClientId == null || relayClientId.trim().isEmpty()) {
            relayClientId = "android-" + UUID.randomUUID();
            preferences.edit().putString(KEY_RELAY_CLIENT_ID, relayClientId).apply();
        }

        if (!preferences.contains(KEY_RELAY_BASE_URL)) {
            preferences.edit().putString(KEY_RELAY_BASE_URL, relayBaseUrl).apply();
        }

        if (relayToken.trim().isEmpty()) {
            relayToken = DEFAULT_RELAY_TOKEN;
            preferences.edit().putString(KEY_RELAY_TOKEN, relayToken).apply();
        }
    }

    void save() {
        preferences.edit()
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

    String fingerprint() {
        return String.join(
            "\u001f",
            nullToEmpty(relayBaseUrl),
            nullToEmpty(sessionId),
            nullToEmpty(relayToken),
            nullToEmpty(relayAgentId),
            nullToEmpty(relayClientId)
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

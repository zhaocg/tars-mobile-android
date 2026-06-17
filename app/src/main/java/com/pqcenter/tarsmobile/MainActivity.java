package com.pqcenter.tarsmobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int INITIAL_RENDERED_MESSAGES = 10;
    private static final int HISTORY_PAGE_SIZE = 10;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<ChatMessage> transcriptMessages = new ArrayList<>();
    private final Map<String, ChatMessage> streamingMessagesByRunId = new HashMap<>();

    private AppSettings settings;
    private TarsApiClient apiClient;
    private TarsApiClient.StreamHandle streamHandle;
    private LinearLayout messageContainer;
    private ScrollView messageScrollView;
    private TextView activityText;
    private TextView sessionText;
    private TextView relayModeText;
    private TextView errorText;
    private View connectionDot;
    private EditText composerInput;
    private Button sendButton;
    private boolean connected;
    private boolean sending;
    private boolean awaitingResponse;
    private boolean renderQueued;
    private boolean loadingOlderMessages;
    private boolean pendingScrollToBottom;
    private boolean pendingPreserveScroll;
    private boolean transcriptLoaded;
    private int pendingPreviousContentHeight;
    private int pendingPreviousScrollY;
    private int renderedTranscriptCount = INITIAL_RENDERED_MESSAGES;
    private long responseWaitToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        settings = new AppSettings(this);
        setContentView(createContentView());
        configureClient();
    }

    @Override
    protected void onDestroy() {
        if (streamHandle != null) {
            streamHandle.cancel();
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    private View createContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(243, 245, 247));
        root.setLayoutParams(matchParent());
        applySystemBarInsets(root);

        root.addView(createToolbar());
        root.addView(createConnectionStrip());

        messageScrollView = new ScrollView(this);
        messageScrollView.setFillViewport(true);
        messageScrollView.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY <= dp(24) && oldScrollY > scrollY) {
                loadOlderMessages();
            }
        });
        messageScrollView.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ));

        messageContainer = new LinearLayout(this);
        messageContainer.setOrientation(LinearLayout.VERTICAL);
        messageContainer.setPadding(0, dp(12), 0, dp(12));
        messageScrollView.addView(messageContainer, matchParentWidthWrapContent());
        root.addView(messageScrollView);

        errorText = new TextView(this);
        errorText.setTextColor(Color.rgb(180, 35, 24));
        errorText.setTextSize(13);
        errorText.setPadding(dp(16), dp(4), dp(16), dp(4));
        errorText.setVisibility(View.GONE);
        root.addView(errorText, matchParentWidthWrapContent());

        root.addView(createComposer());
        return root;
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(Color.WHITE);
        window.setNavigationBarColor(Color.rgb(243, 245, 247));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private void applySystemBarInsets(View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset;
            int bottomInset;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets systemBars = insets.getInsets(WindowInsets.Type.systemBars());
                topInset = systemBars.top;
                bottomInset = systemBars.bottom;
            } else {
                topInset = insets.getSystemWindowInsetTop();
                bottomInset = insets.getSystemWindowInsetBottom();
            }

            view.setPadding(0, topInset, 0, bottomInset);
            return insets;
        });
        root.requestApplyInsets();
    }

    private View createToolbar() {
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(16), dp(10), dp(10), dp(8));
        toolbar.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Tars");
        title.setTextColor(Color.rgb(31, 41, 51));
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button reconnect = compactButton("Reconnect");
        reconnect.setOnClickListener(view -> reconnect());
        toolbar.addView(reconnect);

        Button settingsButton = compactButton("Settings");
        settingsButton.setOnClickListener(view -> showSettingsDialog());
        toolbar.addView(settingsButton);

        return toolbar;
    }

    private View createConnectionStrip() {
        LinearLayout strip = new LinearLayout(this);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(dp(16), dp(8), dp(16), dp(8));
        strip.setBackgroundColor(Color.rgb(250, 251, 252));

        connectionDot = new View(this);
        connectionDot.setBackground(circle(Color.rgb(245, 158, 11)));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotParams.setMargins(0, 0, dp(8), 0);
        strip.addView(connectionDot, dotParams);

        activityText = new TextView(this);
        activityText.setText("Disconnected");
        activityText.setTextColor(Color.rgb(99, 112, 131));
        activityText.setTextSize(13);
        strip.addView(activityText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        sessionText = new TextView(this);
        sessionText.setText(settings.sessionId);
        sessionText.setTextColor(Color.rgb(99, 112, 131));
        sessionText.setTextSize(13);
        sessionText.setSingleLine(true);
        strip.addView(sessionText);

        relayModeText = new TextView(this);
        relayModeText.setText("  " + settings.relayModeLabel());
        relayModeText.setTextColor(Color.rgb(99, 112, 131));
        relayModeText.setTypeface(Typeface.DEFAULT_BOLD);
        relayModeText.setTextSize(13);
        strip.addView(relayModeText);

        return strip;
    }

    private View createComposer() {
        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.BOTTOM);
        composer.setPadding(dp(12), dp(10), dp(12), dp(10));
        composer.setBackgroundColor(Color.WHITE);

        composerInput = new EditText(this);
        composerInput.setHint("Message Tars");
        composerInput.setMinLines(1);
        composerInput.setMaxLines(5);
        composerInput.setSingleLine(false);
        composerInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        composerInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        composerInput.setBackground(rounded(Color.rgb(246, 248, 250), dp(18), 0));
        composerInput.setPadding(dp(14), dp(9), dp(14), dp(9));
        composer.addView(composerInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        sendButton = compactButton("Send");
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        sendParams.setMargins(dp(10), 0, 0, 0);
        sendButton.setOnClickListener(view -> sendDraft());
        composer.addView(sendButton, sendParams);

        return composer;
    }

    private void configureClient() {
        if (streamHandle != null) {
            streamHandle.cancel();
        }

        settings.save();
        apiClient = new TarsApiClient(settings);
        sessionText.setText(settings.sessionId);
        relayModeText.setText("  " + settings.relayModeLabel());
        renderedTranscriptCount = INITIAL_RENDERED_MESSAGES;
        loadingOlderMessages = false;
        transcriptLoaded = false;
        setConnected(false);
        setActivity("Connecting");
        setError(null);

        streamHandle = apiClient.streamSessionEvents(new TarsApiClient.EventCallback() {
            @Override
            public void onOpen() {
                mainHandler.post(() -> {
                    setConnected(true);
                    setActivity("Connected via Relay");
                    requestSessionSnapshot();
                });
            }

            @Override
            public void onEvent(SseEvent event) {
                mainHandler.post(() -> handleEvent(event));
            }

            @Override
            public void onError(Exception exception) {
                mainHandler.post(() -> {
                    setConnected(false);
                    if (TarsApiClient.isTransientStreamDisconnect(exception)) {
                        setActivity("Reconnecting relay");
                        setError(null);
                    } else {
                        setActivity("Disconnected");
                        setError(exception.getMessage());
                    }
                });
            }
        });
    }

    private void reconnect() {
        configureClient();
    }

    private void requestSessionSnapshot() {
        executor.execute(() -> {
            try {
                apiClient.requestSessionSnapshot(settings.sessionId);
            } catch (Exception ignored) {
                // Older relay deployments do not expose snapshot yet; live messaging still works.
            }
        });
    }

    private void sendDraft() {
        String text = composerInput.getText().toString().trim();
        if (text.isEmpty() || sending) {
            return;
        }

        composerInput.setText("");
        sending = true;
        sendButton.setEnabled(false);
        setActivity("Sending message");
        transcriptMessages.add(ChatMessage.user(text));
        renderedTranscriptCount = Math.min(
            transcriptMessages.size(),
            Math.max(renderedTranscriptCount + 1, INITIAL_RENDERED_MESSAGES)
        );
        requestRender(true);

        executor.execute(() -> {
            try {
                apiClient.submitMessage(text, settings.sessionId);
                mainHandler.post(() -> {
                    long waitToken = System.currentTimeMillis();
                    awaitingResponse = true;
                    responseWaitToken = waitToken;
                    setActivity(connected ? "Waiting for response" : "Waiting for response - reconnecting relay");
                    scheduleResponseReconnect(waitToken);
                });
            } catch (Exception exception) {
                mainHandler.post(() -> {
                    awaitingResponse = false;
                    setError(exception.getMessage());
                    setActivity("Send failed");
                });
            } finally {
                mainHandler.post(() -> {
                    sending = false;
                    sendButton.setEnabled(true);
                });
            }
        });
    }

    private void scheduleResponseReconnect(long waitToken) {
        mainHandler.postDelayed(() -> {
            if (!awaitingResponse || responseWaitToken != waitToken) {
                return;
            }

            setActivity("Waiting for response - reconnecting relay");
            configureClient();
        }, 4000);
    }

    private void handleEvent(SseEvent event) {
        try {
            JSONObject data = new JSONObject(event.data);
            String type = data.optString("type", event.event == null ? "" : event.event);
            JSONObject payload = unwrapRelayPayload(data.optJSONObject("payload"));

            switch (type) {
                case "session.connected":
                    setConnected(true);
                    setActivity("Connected via Relay");
                    break;
                case "session.snapshot":
                case "transcript.updated":
                    applyTranscript(payload);
                    break;
                case "message.delta":
                    applyDelta(payload);
                    break;
                case "message.completed":
                    completeMessage(payload);
                    awaitingResponse = false;
                    setActivity("Connected via Relay");
                    break;
                case "message.error":
                    awaitingResponse = false;
                    setError(payload == null ? "The model response failed." : payload.optString("message", "The model response failed."));
                    setActivity("Response failed");
                    break;
                case "tool.started":
                    setActivity(toolActivityText(payload));
                    break;
                case "run.updated":
                    if (payload != null && payload.has("run")) {
                        JSONObject run = payload.optJSONObject("run");
                        if (run != null) {
                            if (awaitingResponse) {
                                setActivity("Run " + run.optString("status", ""));
                            }
                            if ("completed".equals(run.optString("status", ""))) {
                                completeMessage(payload);
                                awaitingResponse = false;
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
        } catch (Exception exception) {
            setError(exception.getMessage());
        }
    }

    private JSONObject unwrapRelayPayload(JSONObject payload) {
        if (payload == null) {
            return null;
        }

        JSONObject nestedPayload = payload.optJSONObject("payload");
        if (nestedPayload != null && payload.has("sessionId") && payload.has("type")) {
            return nestedPayload;
        }

        return payload;
    }

    private void applyTranscript(JSONObject payload) {
        if (payload == null) {
            return;
        }

        JSONArray transcript = payload.optJSONArray("transcript");
        if (transcript == null) {
            return;
        }

        boolean scrollToBottom = !transcriptLoaded || isNearMessageBottom();
        transcriptMessages.clear();
        for (int index = 0; index < transcript.length(); index++) {
            JSONObject entry = transcript.optJSONObject(index);
            if (entry == null) {
                continue;
            }

            String content = entry.optString("content", "");
            if (content.trim().isEmpty()) {
                continue;
            }

            transcriptMessages.add(new ChatMessage(
                entry.optString("id", "transcript:" + index),
                entry.optString("runId", null),
                entry.optString("role", "assistant"),
                content,
                entry.optString("status", ""),
                parseCreatedAt(entry.optString("createdAt", "")),
                false
            ));
        }
        renderedTranscriptCount = Math.min(
            transcriptMessages.size(),
            Math.max(renderedTranscriptCount, INITIAL_RENDERED_MESSAGES)
        );

        for (ChatMessage message : transcriptMessages) {
            if ("assistant".equals(message.role) && message.runId != null) {
                streamingMessagesByRunId.remove(message.runId);
            }

            if ("assistant".equals(message.role) && message.content != null && !message.content.trim().isEmpty()) {
                awaitingResponse = false;
            }
        }

        transcriptLoaded = true;
        requestRender(scrollToBottom);
    }

    private void applyDelta(JSONObject payload) {
        if (payload == null) {
            return;
        }

        String runId = payload.optString("runId", "");
        String delta = payload.optString("delta", "");
        String content = payload.optString("content", "");
        if (runId.isEmpty() || (content.isEmpty() && delta.isEmpty())) {
            return;
        }

        ChatMessage message = streamingMessagesByRunId.get(runId);
        boolean liveResponse = awaitingResponse || (message != null && message.streaming);
        if (!liveResponse && message == null) {
            setActivity("Connected via Relay");
            return;
        }

        if (message == null) {
            message = ChatMessage.assistantStream(runId);
            streamingMessagesByRunId.put(runId, message);
        }

        if (!content.isEmpty()) {
            boolean looksLikeChunk = !message.content.isEmpty()
                && !content.startsWith(message.content)
                && content.equals(delta);
            message.content = looksLikeChunk ? message.content + delta : content;
        } else {
            message.content = message.content + delta;
        }

        message.streaming = liveResponse;
        awaitingResponse = false;

        if (liveResponse) {
            setActivity("Receiving response");
        } else {
            setActivity("Connected via Relay");
        }

        requestRender(true);
    }

    private void completeMessage(JSONObject payload) {
        String runId = extractRunId(payload);
        String content = extractAssistantContent(payload);

        ChatMessage message = streamingMessagesByRunId.get(runId);
        boolean liveResponse = awaitingResponse || message != null;
        if (!liveResponse) {
            return;
        }

        if (message == null && !content.isEmpty()) {
            message = ChatMessage.assistantFinal(runId, content);
            if (message.runId == null) {
                transcriptMessages.add(message);
            } else {
                streamingMessagesByRunId.put(message.runId, message);
            }
        }

        if (message != null) {
            if (!content.isEmpty()) {
                message.content = content;
            }
            message.streaming = false;
            requestRender(true);
        }
    }

    private String extractRunId(JSONObject payload) {
        if (payload == null) {
            return "";
        }

        JSONObject run = payload.optJSONObject("run");
        if (run != null) {
            return run.optString("id", payload.optString("runId", ""));
        }

        return payload.optString("runId", "");
    }

    private String extractAssistantContent(JSONObject payload) {
        if (payload == null) {
            return "";
        }

        String content = extractMessageContent(payload.optJSONObject("finalRuntimeResult"));
        if (!content.isEmpty()) {
            return content;
        }

        content = extractMessageContent(payload.optJSONObject("runtimeResult"));
        if (!content.isEmpty()) {
            return content;
        }

        JSONObject run = payload.optJSONObject("run");
        if (run != null) {
            JSONObject assistantMessage = run.optJSONObject("assistantMessage");
            if (assistantMessage != null) {
                content = assistantMessage.optString("content", "");
                if (!content.isEmpty()) {
                    return content;
                }
            }
        }

        JSONObject message = payload.optJSONObject("message");
        if (message != null) {
            return message.optString("content", "");
        }

        return payload.optString("content", "");
    }

    private String extractMessageContent(JSONObject result) {
        if (result == null) {
            return "";
        }

        JSONObject message = result.optJSONObject("message");
        if (message == null) {
            return "";
        }

        return message.optString("content", "");
    }

    private void renderMessages() {
        renderQueued = false;
        messageContainer.removeAllViews();

        List<ChatMessage> messages = new ArrayList<>();
        int transcriptStart = Math.max(0, transcriptMessages.size() - renderedTranscriptCount);
        messages.addAll(transcriptMessages.subList(transcriptStart, transcriptMessages.size()));
        messages.addAll(streamingMessagesByRunId.values());
        messages.sort(Comparator.comparingLong(message -> message.createdAtMillis));

        for (ChatMessage message : messages) {
            if (!message.streaming && (message.content == null || message.content.trim().isEmpty())) {
                continue;
            }

            messageContainer.addView(createMessageRow(message));
        }

        if (pendingPreserveScroll) {
            int previousHeight = pendingPreviousContentHeight;
            int previousScrollY = pendingPreviousScrollY;
            messageScrollView.post(() -> {
                int heightDelta = messageContainer.getHeight() - previousHeight;
                messageScrollView.scrollTo(0, Math.max(0, previousScrollY + heightDelta));
                loadingOlderMessages = false;
            });
        } else if (pendingScrollToBottom) {
            messageScrollView.post(() -> {
                messageScrollView.fullScroll(View.FOCUS_DOWN);
                loadingOlderMessages = false;
            });
        } else {
            loadingOlderMessages = false;
        }

        pendingPreserveScroll = false;
        pendingScrollToBottom = false;
    }

    private void requestRender(boolean scrollToBottom) {
        if (renderQueued) {
            pendingScrollToBottom = pendingScrollToBottom || scrollToBottom;
            return;
        }

        pendingScrollToBottom = scrollToBottom;
        pendingPreserveScroll = false;
        renderQueued = true;
        mainHandler.postDelayed(this::renderMessages, 50);
    }

    private void requestRenderPreservingScroll(int previousContentHeight, int previousScrollY) {
        pendingPreviousContentHeight = previousContentHeight;
        pendingPreviousScrollY = previousScrollY;

        if (renderQueued) {
            pendingPreserveScroll = true;
            pendingScrollToBottom = false;
            return;
        }

        pendingPreserveScroll = true;
        pendingScrollToBottom = false;
        renderQueued = true;
        mainHandler.postDelayed(this::renderMessages, 50);
    }

    private void loadOlderMessages() {
        if (loadingOlderMessages || transcriptMessages.isEmpty()) {
            return;
        }

        if (renderedTranscriptCount >= transcriptMessages.size()) {
            return;
        }

        loadingOlderMessages = true;
        int previousHeight = messageContainer.getHeight();
        int previousScrollY = messageScrollView.getScrollY();
        renderedTranscriptCount = Math.min(
            transcriptMessages.size(),
            renderedTranscriptCount + HISTORY_PAGE_SIZE
        );
        requestRenderPreservingScroll(previousHeight, previousScrollY);
    }

    private boolean isNearMessageBottom() {
        int contentHeight = messageContainer.getHeight();
        int viewportBottom = messageScrollView.getScrollY() + messageScrollView.getHeight();
        return contentHeight <= 0 || contentHeight - viewportBottom <= dp(96);
    }

    private View createMessageRow(ChatMessage message) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(message.isFromUser() ? Gravity.END : Gravity.START);
        row.setPadding(dp(16), dp(5), dp(16), dp(5));

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(13), dp(10), dp(13), dp(10));
        bubble.setBackground(rounded(
            message.isFromUser() ? Color.rgb(220, 244, 241) : Color.WHITE,
            dp(18),
            message.isFromUser() ? 0 : Color.rgb(230, 234, 239)
        ));

        if (message.isFromUser()) {
            TextView textView = new TextView(this);
            textView.setText(message.content);
            textView.setTextColor(Color.rgb(31, 41, 51));
            textView.setTextSize(16);
            textView.setLineSpacing(0, 1.08f);
            bubble.addView(textView, matchParentWidthWrapContent());
        } else {
            MarkdownChartWebView markdownView = new MarkdownChartWebView(this);
            markdownView.setMinimumHeight(dp(32));
            markdownView.renderMarkdown(message.content);
            bubble.addView(markdownView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(36)
            ));
        }

        if (message.streaming) {
            ProgressBar progressBar = new ProgressBar(this);
            progressBar.setIndeterminate(true);
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(28), dp(28));
            progressParams.setMargins(0, dp(4), 0, 0);
            bubble.addView(progressBar, progressParams);
        }

        int maxWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.86f);
        row.addView(bubble, new LinearLayout.LayoutParams(maxWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void showSettingsDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(4));

        Spinner relayMode = settingsModeField(form, settings.relayMode);
        EditText relayUrl = settingsField(form, "Relay URL", settings.relayBaseUrl, false);
        EditText sessionId = settingsField(form, "Session ID", settings.sessionId, false);
        EditText relayToken = settingsField(form, "Relay Token", settings.relayToken, true);
        EditText agentId = settingsField(form, "Agent ID", settings.relayAgentId, false);
        EditText clientId = settingsField(form, "Client ID", settings.relayClientId, false);

        TextView note = new TextView(this);
        note.setText("Local LAN mode connects directly to a relay on your PC. Use the LAN URL printed by start-local-relay.bat, not 127.0.0.1 on a physical phone.");
        note.setTextSize(13);
        note.setTextColor(Color.rgb(99, 112, 131));
        note.setPadding(0, dp(8), 0, 0);
        form.addView(note);

        relayMode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String mode = relayModeValueAt(position);
                relayUrl.setText(AppSettings.relayUrlForMode(mode, relayUrl.getText().toString()));
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // Keep the existing URL.
            }
        });

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(form);

        new AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(scrollView)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", (dialog, which) -> {
                settings.relayMode = relayModeValueAt(relayMode.getSelectedItemPosition());
                settings.relayBaseUrl = relayUrl.getText().toString();
                settings.sessionId = sessionId.getText().toString();
                settings.relayToken = relayToken.getText().toString();
                settings.relayAgentId = agentId.getText().toString();
                settings.relayClientId = clientId.getText().toString();
                configureClient();
            })
            .show();
    }

    private Spinner settingsModeField(LinearLayout form, String currentMode) {
        TextView title = new TextView(this);
        title.setText("Relay Mode");
        title.setTextColor(Color.rgb(31, 41, 51));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(13);
        title.setPadding(0, dp(10), 0, dp(4));
        form.addView(title);

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_item,
            new String[] {
                "Cloud relay",
                "Local LAN relay",
                "Android emulator relay",
                "Custom relay"
            }
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(relayModeIndex(AppSettings.normalizeRelayMode(currentMode)));
        form.addView(spinner, matchParentWidthWrapContent());
        return spinner;
    }

    private int relayModeIndex(String mode) {
        switch (mode) {
            case AppSettings.RELAY_MODE_LOCAL_LAN:
                return 1;
            case AppSettings.RELAY_MODE_EMULATOR:
                return 2;
            case AppSettings.RELAY_MODE_CUSTOM:
                return 3;
            case AppSettings.RELAY_MODE_CLOUD:
            default:
                return 0;
        }
    }

    private String relayModeValueAt(int position) {
        switch (position) {
            case 1:
                return AppSettings.RELAY_MODE_LOCAL_LAN;
            case 2:
                return AppSettings.RELAY_MODE_EMULATOR;
            case 3:
                return AppSettings.RELAY_MODE_CUSTOM;
            case 0:
            default:
                return AppSettings.RELAY_MODE_CLOUD;
        }
    }

    private EditText settingsField(LinearLayout form, String label, String value, boolean secure) {
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextColor(Color.rgb(31, 41, 51));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(13);
        title.setPadding(0, dp(10), 0, dp(4));
        form.addView(title);

        EditText input = new EditText(this);
        input.setText(value == null ? "" : value);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(false);
        input.setBackground(rounded(Color.rgb(246, 248, 250), dp(10), Color.rgb(225, 231, 238)));
        input.setPadding(dp(10), dp(8), dp(10), dp(8));

        if (secure) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        }

        form.addView(input, matchParentWidthWrapContent());
        return input;
    }

    private String toolActivityText(JSONObject payload) {
        if (payload == null) {
            return "Running tool";
        }

        JSONArray requests = payload.optJSONArray("requests");
        if (requests == null || requests.length() == 0) {
            return "Running tool";
        }

        JSONObject first = requests.optJSONObject(0);
        if (first == null) {
            return "Running tool";
        }

        String toolName = first.optString("toolName", "");
        if (!toolName.isEmpty()) {
            return "Running " + toolName;
        }

        String capability = first.optString("capability", "");
        if (!capability.isEmpty()) {
            return "Running " + capability;
        }

        return "Running tool";
    }

    private void setConnected(boolean isConnected) {
        connected = isConnected;
        connectionDot.setBackground(circle(connected ? Color.rgb(34, 197, 94) : Color.rgb(245, 158, 11)));
    }

    private void setActivity(String text) {
        activityText.setText(text == null ? "" : text);
    }

    private void setError(String message) {
        if (message == null || message.trim().isEmpty()) {
            errorText.setVisibility(View.GONE);
            errorText.setText("");
            return;
        }

        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    private Button compactButton(String text) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(13);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        return button;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private GradientDrawable circle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private LinearLayout.LayoutParams matchParent() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    private LinearLayout.LayoutParams matchParentWidthWrapContent() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private long parseCreatedAt(String value) {
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (Exception ignored) {
            return System.currentTimeMillis();
        }
    }
}

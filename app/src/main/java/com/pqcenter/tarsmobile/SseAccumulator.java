package com.pqcenter.tarsmobile;

import java.util.ArrayList;
import java.util.List;

final class SseAccumulator {
    private String id;
    private String event;
    private final List<String> dataLines = new ArrayList<>();

    void appendLine(String line) {
        if (line.startsWith(":")) {
            return;
        }

        if (line.startsWith("id:")) {
            id = valueAfterPrefix(line, "id:");
            return;
        }

        if (line.startsWith("event:")) {
            event = valueAfterPrefix(line, "event:");
            return;
        }

        if (line.startsWith("data:")) {
            dataLines.add(valueAfterPrefix(line, "data:"));
        }
    }

    SseEvent makeEventOrNull() {
        if (dataLines.isEmpty()) {
            return null;
        }

        return new SseEvent(id, event, String.join("\n", dataLines));
    }

    private static String valueAfterPrefix(String line, String prefix) {
        String value = line.substring(prefix.length());
        return value.startsWith(" ") ? value.substring(1) : value;
    }
}

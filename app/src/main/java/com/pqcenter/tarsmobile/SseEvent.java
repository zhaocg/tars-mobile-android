package com.pqcenter.tarsmobile;

final class SseEvent {
    final String id;
    final String event;
    final String data;

    SseEvent(String id, String event, String data) {
        this.id = id;
        this.event = event;
        this.data = data;
    }
}

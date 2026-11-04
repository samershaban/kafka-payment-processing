package com.example.orders.service;

import com.example.orders.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pushes order changes to connected browsers with Server-Sent Events (GET /orders/stream).
 *
 * Every time an order is created or its status changes, each subscriber receives an "order" event whose data
 * is the order as JSON. Browsers reconnect automatically (EventSource) if the connection drops.
 *
 * Note: subscribers are kept in memory, so with several Order Service instances a browser only hears about
 * changes made by the instance it's connected to. Fanning out across instances would need e.g. Redis pub/sub
 * or every instance consuming a Kafka topic of order changes.
 */
@Component
public class OrderUpdates {

    private static final Logger log = LoggerFactory.getLogger(OrderUpdates.class);

    private final List<SseEmitter> subscribers = new CopyOnWriteArrayList<>();

    /** A new browser connection. 0 = never time out; dead connections are dropped by the heartbeat. */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.add(emitter);
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> subscribers.remove(emitter));
        emitter.onError(e -> subscribers.remove(emitter));
        // Tell the client right away that the stream is open (also flushes the response headers)
        send(emitter, SseEmitter.event().comment("connected"));
        return emitter;
    }

    /** Sends the order's current state to every connected browser. */
    public void publish(Order order) {
        for (SseEmitter emitter : subscribers) {
            send(emitter, SseEmitter.event().name("order").data(order, MediaType.APPLICATION_JSON));
        }
    }

    /**
     * Every 15 s, a comment line to each subscriber. Keeps proxies from closing idle connections, and is how we
     * notice browsers that went away (the write fails and the subscriber is removed).
     */
    @Scheduled(fixedRate = 15_000)
    void heartbeat() {
        for (SseEmitter emitter : subscribers) {
            send(emitter, SseEmitter.event().comment("ping"));
        }
    }

    int subscriberCount() {
        return subscribers.size();
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            // The browser disconnected (or the emitter already completed): forget it
            subscribers.remove(emitter);
            emitter.completeWithError(e);
            log.debug("Dropped SSE subscriber: {}", e.getMessage());
        }
    }
}

package com.example.analytics.controller;

import com.example.analytics.service.BackfillService;
import com.example.analytics.service.StatusService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Operational endpoints. Local/admin use only: there's no authentication. */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final BackfillService backfill;
    private final StatusService status;

    public AdminController(BackfillService backfill, StatusService status) {
        this.backfill = backfill;
        this.status = status;
    }

    /** CDC state, records published, consumer lag. Try: curl localhost:8083/admin/status */
    @GetMapping("/status")
    public StatusService.Status status() {
        return status.status();
    }

    /** Republish all existing Cassandra orders as BACKFILL facts. Try: curl -X POST localhost:8083/admin/backfill */
    @PostMapping("/backfill")
    public Map<String, Object> backfill() {
        BackfillService.Result result = backfill.backfillOrders();
        return Map.of("published", result.published(), "tookMs", result.took().toMillis());
    }

    @ExceptionHandler(BackfillService.BackfillAlreadyRunningException.class)
    ResponseEntity<Map<String, String>> alreadyRunning(BackfillService.BackfillAlreadyRunningException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }
}

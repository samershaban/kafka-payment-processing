package com.example.analytics.controller;

import com.example.analytics.service.BackfillService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Operational endpoints. Local/admin use only: there's no authentication. */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final BackfillService backfill;

    public AdminController(BackfillService backfill) {
        this.backfill = backfill;
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

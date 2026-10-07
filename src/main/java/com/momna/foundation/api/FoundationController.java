package com.momna.foundation.api;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FoundationController {
    @GetMapping(value = "/", produces = "text/plain")
    public String identity() { return "Momna Backend"; }

    @GetMapping(value = "/health", produces = "text/plain")
    public String health() { return "OK"; }

    @GetMapping("/health/live")
    public Map<String, Object> live() { return Map.of("status", "UP"); }

    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping(value = "/version", produces = "text/plain")
    public String version() { return "0.6.0"; }
}

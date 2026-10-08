package com.momna.foundation.api;

import com.momna.platform.database.DatabaseReadinessProbe;
import com.momna.platform.health.ReadinessContract;
import com.momna.platform.health.ReadinessRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FoundationController {
    private final ReadinessRegistry readiness;

    public FoundationController(DataSource dataSource) {
        this.readiness = ReadinessRegistry.named(
            Map.of("database", new DatabaseReadinessProbe(dataSource)),
            ReadinessContract.DEFAULT_READINESS_TIMEOUT_MS
        );
    }

    @GetMapping(value = "/", produces = "text/plain")
    public String identity() {
        return "Momna Backend";
    }

    @GetMapping(value = "/health", produces = "text/plain")
    public String health() {
        return "OK";
    }

    @GetMapping("/health/live")
    public Map<String, Object> live() {
        return Map.of("status", "LIVE");
    }

    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        var report = readiness.report();
        var body = new LinkedHashMap<String, Object>();
        body.put("status", report.ready() ? "READY" : "NOT_READY");
        body.put(
            "checks",
            report.checks().stream().map(check -> Map.of(
                "name", check.name(),
                "status", check.status().name()
            )).toList()
        );
        return ResponseEntity
            .status(report.ready() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
            .body(body);
    }

    @GetMapping(value = "/version", produces = "text/plain")
    public String version() {
        return "0.6.0";
    }
}

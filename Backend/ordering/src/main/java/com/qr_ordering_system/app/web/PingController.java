package com.qr_ordering_system.app.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PingController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "status", "OK",
                "service", "qr-ordering-api",
                "phase", "0",
                "timestamp", Instant.now().toString()
        );
    }
}

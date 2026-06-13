package com.example.gateway.app;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class PublicController {

    @GetMapping("/public/info")
    public Mono<Map<String, String>> info() {
        return Mono.just(Map.of(
            "service", "gateway",
            "status", "UP"
        ));
    }
}

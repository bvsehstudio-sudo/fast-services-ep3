package com.fastservices.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class BackendCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendCoreApplication.class, args);
    }

    @GetMapping("/api/status")
    public String status() {
        return "{\"status\": \"MVP Fast Services Operativo y en la Nube\"}";
    }
}
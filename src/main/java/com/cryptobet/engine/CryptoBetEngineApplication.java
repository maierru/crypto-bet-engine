package com.cryptobet.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CryptoBetEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(CryptoBetEngineApplication.class, args);
    }
}

package com.cryptobet.engine.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.net.ServerSocket;

@Configuration
@Profile("test")
public class EmbeddedRedisConfig {

    @Value("${spring.data.redis.port}")
    private int redisPort;

    private Process redisProcess;

    @PostConstruct
    public void start() throws IOException {
        if (isPortAvailable(redisPort)) {
            redisProcess = new ProcessBuilder("redis-server", "--port", String.valueOf(redisPort), "--save", "", "--appendonly", "no")
                    .redirectErrorStream(true)
                    .start();
            waitForRedisReady();
        }
    }

    @PreDestroy
    public void stop() {
        if (redisProcess != null && redisProcess.isAlive()) {
            redisProcess.destroyForcibly();
        }
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void waitForRedisReady() {
        for (int i = 0; i < 50; i++) {
            if (!isPortAvailable(redisPort)) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}

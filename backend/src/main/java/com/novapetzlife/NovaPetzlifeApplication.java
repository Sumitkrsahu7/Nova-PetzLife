package com.novapetzlife;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
public class NovaPetzlifeApplication {

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(NovaPetzlifeApplication.class, args);
    }

    /**
     * Automatically loads environment variables from .env or .env.example
     * into JVM System properties if not already set in the host environment.
     */
    private static void loadDotEnv() {
        Path[] candidatePaths = new Path[]{
            Paths.get(".env"),
            Paths.get("../.env"),
            Paths.get("../../.env"),
            Paths.get(".env.example"),
            Paths.get("../.env.example")
        };

        for (Path path : candidatePaths) {
            if (Files.isRegularFile(path)) {
                try (BufferedReader reader = Files.newBufferedReader(path)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        int eq = line.indexOf('=');
                        if (eq > 0) {
                            String key = line.substring(0, eq).trim();
                            String val = line.substring(eq + 1).trim();
                            if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                                val = val.substring(1, val.length() - 1);
                            }
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, val);
                            }
                            // Map common DB environment variables to Spring datasource properties
                            if ("DB_PASSWORD".equals(key) && System.getProperty("spring.datasource.password") == null && System.getenv("SPRING_DATASOURCE_PASSWORD") == null) {
                                System.setProperty("spring.datasource.password", val);
                            }
                            if ("DB_USERNAME".equals(key) && System.getProperty("spring.datasource.username") == null && System.getenv("SPRING_DATASOURCE_USERNAME") == null) {
                                System.setProperty("spring.datasource.username", val);
                            }
                            if ("DB_URL".equals(key) && System.getProperty("spring.datasource.url") == null && System.getenv("SPRING_DATASOURCE_URL") == null) {
                                System.setProperty("spring.datasource.url", val);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                // If we found a real .env file, stop searching
                if (path.getFileName().toString().equals(".env")) {
                    break;
                }
            }
        }
    }
}

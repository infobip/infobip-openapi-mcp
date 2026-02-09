package com.infobip.openapi.mcp;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class TestApplication {
    public static void main(String... args) {
        new SpringApplicationBuilder(TestApplication.class).run(args);
    }
}

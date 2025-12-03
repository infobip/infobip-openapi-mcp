package com.infobip.openapi.mcp;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class TestApplication {
    public static void main(String... args) {
        new SpringApplicationBuilder(TestApplication.class).run(args);
    }
}

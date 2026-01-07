package com.infobip.mcp.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class Application {

    static void main(String[] args) {
        new SpringApplicationBuilder(Application.class).run(args);
    }

}

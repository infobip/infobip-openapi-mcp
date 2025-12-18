package com.infobip.mcp.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

    static void main(String[] args) {
        var application = new SpringApplication(Application.class);
        // Running MCP server over stdio transport means that
        // we do not need a web server:
        application.setWebApplicationType(WebApplicationType.NONE);
        application.run(args);
    }

}

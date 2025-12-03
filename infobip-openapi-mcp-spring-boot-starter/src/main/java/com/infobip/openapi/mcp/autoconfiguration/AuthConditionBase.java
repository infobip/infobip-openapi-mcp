package com.infobip.openapi.mcp.autoconfiguration;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;

abstract class AuthConditionBase implements Condition {

    protected boolean isPropertyEnabled(ConditionContext context, String propertyName) {
        var environment = context.getEnvironment();
        return environment.getProperty(propertyName + ".enabled", Boolean.class, false);
    }
}

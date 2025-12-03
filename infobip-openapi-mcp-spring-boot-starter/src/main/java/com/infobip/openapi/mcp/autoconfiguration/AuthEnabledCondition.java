package com.infobip.openapi.mcp.autoconfiguration;

import com.infobip.openapi.mcp.auth.AuthProperties;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

class AuthEnabledCondition extends AuthConditionBase {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return isPropertyEnabled(context, AuthProperties.PREFIX);
    }
}

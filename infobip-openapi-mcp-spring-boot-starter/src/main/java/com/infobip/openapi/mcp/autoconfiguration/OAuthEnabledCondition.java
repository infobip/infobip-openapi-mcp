package com.infobip.openapi.mcp.autoconfiguration;

import com.infobip.openapi.mcp.auth.OAuthProperties;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

class OAuthEnabledCondition extends AuthEnabledCondition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return super.matches(context, metadata) && isPropertyEnabled(context, OAuthProperties.PREFIX);
    }
}

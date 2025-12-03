package com.infobip.openapi.mcp.autoconfiguration;

import com.infobip.openapi.mcp.auth.ScopeProperties;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

class ScopeEnabledCondition extends OAuthEnabledCondition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return super.matches(context, metadata) && isPropertyEnabled(context, ScopeProperties.PREFIX);
    }
}

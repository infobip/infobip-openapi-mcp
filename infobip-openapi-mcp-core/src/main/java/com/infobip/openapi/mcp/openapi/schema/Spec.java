package com.infobip.openapi.mcp.openapi.schema;

import java.util.Set;

class Spec {
    private Spec() {}

    static Set<String> SUPPORTED_PARAMETER_TYPES = Set.of(
            DecomposedRequestData.ParametersByType.QUERY,
            DecomposedRequestData.ParametersByType.PATH,
            DecomposedRequestData.ParametersByType.HEADER,
            DecomposedRequestData.ParametersByType.COOKIE);
}

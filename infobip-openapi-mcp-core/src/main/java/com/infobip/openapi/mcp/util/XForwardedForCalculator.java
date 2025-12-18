package com.infobip.openapi.mcp.util;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;

public class XForwardedForCalculator {
    public @Nullable String calculateXFF(HttpServletRequest request) {
        var xForwardedForHeader = request.getHeader("X-Forwarded-For");
        var clientIp = request.getRemoteAddr();

        if (isXffPresent(xForwardedForHeader)) {
            if (isClientIpPresent(clientIp) && !xForwardedForHeader.contains(clientIp)) {
                return xForwardedForHeader + ", " + clientIp;
            }
            return xForwardedForHeader;
        }

        if (isClientIpPresent(clientIp)) {
            return clientIp;
        }

        return null;
    }

    private boolean isXffPresent(String xff) {
        return xff != null && !xff.isBlank();
    }

    private boolean isClientIpPresent(String clientIp) {
        return clientIp != null && !clientIp.isBlank();
    }
}

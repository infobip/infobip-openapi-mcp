package com.infobip.openapi.mcp.progress;

import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.McpRequestContext;
import org.junit.jupiter.api.Test;

class DefaultProgressUpdateProviderTest {

    private final DefaultProgressUpdateProvider provider = new DefaultProgressUpdateProvider();

    private final McpRequestContext context = new McpRequestContext();

    @Test
    void shouldReturnNullTotal() {
        // when
        var total = provider.total(context);

        // then
        then(total).isNull();
    }

    @Test
    void shouldReturnTickAsProgress() {
        // when
        var update = provider.next(5L, context);

        // then
        then(update.progress()).isEqualTo(5.0);
    }

    @Test
    void shouldReturnNullMessage() {
        // when
        var update = provider.next(0L, context);

        // then
        then(update.message()).isNull();
    }

    @Test
    void shouldReturnIncreasingProgressAcrossConsecutiveTicks() {
        // when
        var first = provider.next(0L, context);
        var second = provider.next(1L, context);
        var third = provider.next(2L, context);

        // then
        then(first.progress()).isLessThan(second.progress());
        then(second.progress()).isLessThan(third.progress());
    }
}

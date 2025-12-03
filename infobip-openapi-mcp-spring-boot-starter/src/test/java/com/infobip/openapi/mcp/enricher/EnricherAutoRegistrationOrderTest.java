package com.infobip.openapi.mcp.enricher;

import static org.assertj.core.api.BDDAssertions.then;

import com.infobip.openapi.mcp.openapi.OpenApiTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test verifying that enrichers are auto-registered by Spring in the correct order.
 * <p>
 * This test ensures that Spring's dependency injection respects the {@link org.springframework.core.Ordered}
 * interface implementation, providing enrichers to {@link ApiRequestEnricherChain} in ascending order
 * based on their {@code getOrder()} return values.
 * </p>
 */
@ActiveProfiles("test-http")
class EnricherAutoRegistrationOrderTest extends OpenApiTestBase {

    @Autowired
    private List<ApiRequestEnricher> enrichers;

    @Test
    void shouldAutoRegisterEnrichersInCorrectOrder() {
        // then - verify enrichers are registered in ascending order by getOrder()
        then(enrichers).isNotEmpty();
        then(enrichers).hasSizeGreaterThanOrEqualTo(3);

        // Verify enrichers are sorted by their order values
        for (var i = 0; i < enrichers.size() - 1; i++) {
            var current = enrichers.get(i);
            var next = enrichers.get(i + 1);
            then(current.getOrder())
                    .as(
                            "Enricher %s (order=%d) should come before %s (order=%d)",
                            current.name(), current.getOrder(), next.name(), next.getOrder())
                    .isLessThanOrEqualTo(next.getOrder());
        }
    }

    @Test
    void shouldRegisterXForwardedForEnricherFirst() {
        // then
        then(enrichers).isNotEmpty();
        var firstEnricher = enrichers.get(0);
        then(firstEnricher).isInstanceOf(XForwardedForEnricher.class);
        then(firstEnricher.getOrder()).isEqualTo(XForwardedForEnricher.ORDER);
    }

    @Test
    void shouldRegisterXForwardedHostEnricherSecond() {
        // then
        then(enrichers).hasSizeGreaterThanOrEqualTo(2);
        var secondEnricher = enrichers.get(1);
        then(secondEnricher).isInstanceOf(XForwardedHostEnricher.class);
        then(secondEnricher.getOrder()).isEqualTo(XForwardedHostEnricher.ORDER);
    }

    @Test
    void shouldRegisterUserAgentEnricherLast() {
        // then
        then(enrichers).hasSizeGreaterThanOrEqualTo(3);
        var lastEnricher = enrichers.get(enrichers.size() - 1);
        then(lastEnricher).isInstanceOf(UserAgentEnricher.class);
        then(lastEnricher.getOrder()).isEqualTo(UserAgentEnricher.ORDER);
    }

    @Test
    void shouldVerifyExpectedOrderValues() {
        // then - verify the order constants have expected values
        then(XForwardedForEnricher.ORDER).isEqualTo(100);
        then(XForwardedHostEnricher.ORDER).isEqualTo(200);
        then(UserAgentEnricher.ORDER).isEqualTo(1000);

        // Verify order values are ascending
        then(XForwardedForEnricher.ORDER).isLessThan(XForwardedHostEnricher.ORDER);
    }
}

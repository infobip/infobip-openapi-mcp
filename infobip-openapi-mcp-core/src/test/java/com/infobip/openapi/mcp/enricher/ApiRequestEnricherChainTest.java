package com.infobip.openapi.mcp.enricher;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.infobip.openapi.mcp.McpRequestContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class ApiRequestEnricherChainTest {

    @Mock
    private RestClient.RequestHeadersSpec spec;

    @Mock
    private RestClient.RequestHeadersSpec enrichedSpec1;

    @Mock
    private RestClient.RequestHeadersSpec enrichedSpec2;

    @Mock
    private McpRequestContext context;

    @Mock
    private ApiRequestEnricher enricher1;

    @Mock
    private ApiRequestEnricher enricher2;

    @Test
    void shouldApplyAllEnrichersInSequence() {
        // given
        given(enricher1.name()).willReturn("Enricher1");
        given(enricher2.name()).willReturn("Enricher2");
        given(enricher1.enrich(spec, context)).willReturn(enrichedSpec1);
        given(enricher2.enrich(enrichedSpec1, context)).willReturn(enrichedSpec2);

        var chain = new ApiRequestEnricherChain(List.of(enricher1, enricher2));

        // when
        var result = chain.enrich(spec, context);

        // then
        then(result).isEqualTo(enrichedSpec2);

        InOrder inOrder = inOrder(enricher1, enricher2);
        inOrder.verify(enricher1).enrich(spec, context);
        inOrder.verify(enricher2).enrich(enrichedSpec1, context);
    }

    @Test
    void shouldContinueWhenEnricherThrowsException() {
        // given
        given(enricher1.name()).willReturn("FailingEnricher");
        given(enricher2.name()).willReturn("WorkingEnricher");
        given(enricher1.enrich(spec, context)).willThrow(new RuntimeException("Enricher failed"));
        given(enricher2.enrich(spec, context)).willReturn(enrichedSpec1);

        var chain = new ApiRequestEnricherChain(List.of(enricher1, enricher2));

        // when
        var result = chain.enrich(spec, context);

        // then
        then(result).isEqualTo(enrichedSpec1);
        verify(enricher1).enrich(spec, context);
        verify(enricher2).enrich(spec, context);
    }

    @Test
    void shouldHandleEmptyEnricherList() {
        // given
        var chain = new ApiRequestEnricherChain(List.of());

        // when
        var result = chain.enrich(spec, context);

        // then
        then(result).isEqualTo(spec);
    }

    @Test
    void shouldPassContextThroughAllEnrichers() {
        // given
        given(enricher1.name()).willReturn("Enricher1");
        given(enricher2.name()).willReturn("Enricher2");
        given(enricher1.enrich(spec, context)).willReturn(enrichedSpec1);
        given(enricher2.enrich(enrichedSpec1, context)).willReturn(enrichedSpec2);

        var chain = new ApiRequestEnricherChain(List.of(enricher1, enricher2));

        // when
        chain.enrich(spec, context);

        // then
        verify(enricher1).enrich(spec, context);
        verify(enricher2).enrich(enrichedSpec1, context);
    }

    @Test
    void shouldApplyEnrichersInOrder() {
        // given
        var enricher3 = mock(ApiRequestEnricher.class);
        var enrichedSpec3 = mock(RestClient.RequestHeadersSpec.class);

        given(enricher1.name()).willReturn("Enricher1");
        given(enricher2.name()).willReturn("Enricher2");
        given(enricher3.name()).willReturn("Enricher3");
        given(enricher1.enrich(spec, context)).willReturn(enrichedSpec1);
        given(enricher2.enrich(enrichedSpec1, context)).willReturn(enrichedSpec2);
        given(enricher3.enrich(enrichedSpec2, context)).willReturn(enrichedSpec3);

        var chain = new ApiRequestEnricherChain(List.of(enricher1, enricher2, enricher3));

        // when
        var result = chain.enrich(spec, context);

        // then
        then(result).isEqualTo(enrichedSpec3);

        InOrder inOrder = inOrder(enricher1, enricher2, enricher3);
        inOrder.verify(enricher1).enrich(spec, context);
        inOrder.verify(enricher2).enrich(enrichedSpec1, context);
        inOrder.verify(enricher3).enrich(enrichedSpec2, context);
    }

    @Test
    void shouldReturnOriginalSpecWhenFirstEnricherFails() {
        // given
        given(enricher1.name()).willReturn("FailingEnricher");
        given(enricher1.enrich(spec, context)).willThrow(new RuntimeException("First enricher failed"));

        var chain = new ApiRequestEnricherChain(List.of(enricher1));

        // when
        var result = chain.enrich(spec, context);

        // then
        then(result).isEqualTo(spec);
        verify(enricher1).enrich(spec, context);
    }

    @Test
    void shouldContinueWithPreviousSpecWhenMiddleEnricherFails() {
        // given
        given(enricher1.name()).willReturn("WorkingEnricher1");
        given(enricher2.name()).willReturn("FailingEnricher");
        var enricher3 = mock(ApiRequestEnricher.class);
        given(enricher3.name()).willReturn("WorkingEnricher2");

        given(enricher1.enrich(spec, context)).willReturn(enrichedSpec1);
        given(enricher2.enrich(enrichedSpec1, context)).willThrow(new RuntimeException("Middle enricher failed"));
        given(enricher3.enrich(enrichedSpec1, context)).willReturn(enrichedSpec2);

        var chain = new ApiRequestEnricherChain(List.of(enricher1, enricher2, enricher3));

        // when
        var result = chain.enrich(spec, context);

        // then
        then(result).isEqualTo(enrichedSpec2);
        verify(enricher1).enrich(spec, context);
        verify(enricher2).enrich(enrichedSpec1, context);
        verify(enricher3).enrich(enrichedSpec1, context);
    }

    @Test
    void shouldHandleAllEnrichersFailing() {
        // given
        given(enricher1.name()).willReturn("FailingEnricher1");
        given(enricher2.name()).willReturn("FailingEnricher2");
        given(enricher1.enrich(spec, context)).willThrow(new RuntimeException("Enricher 1 failed"));
        given(enricher2.enrich(spec, context)).willThrow(new RuntimeException("Enricher 2 failed"));

        var chain = new ApiRequestEnricherChain(List.of(enricher1, enricher2));

        // when
        var result = chain.enrich(spec, context);

        // then
        then(result).isEqualTo(spec);
        verify(enricher1).enrich(spec, context);
        verify(enricher2).enrich(spec, context);
    }
}

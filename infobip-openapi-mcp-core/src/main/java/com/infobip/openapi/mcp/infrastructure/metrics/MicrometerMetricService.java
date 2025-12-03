package com.infobip.openapi.mcp.infrastructure.metrics;

import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import com.infobip.openapi.mcp.openapi.tool.naming.NamingStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;

public class MicrometerMetricService implements MetricService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MicrometerMetricService.class);

    private final MeterRegistry meterRegistry;
    private final NamingStrategy namingStrategy;

    public MicrometerMetricService(MeterRegistry meterRegistry, NamingStrategy namingStrategy) {
        this.meterRegistry = meterRegistry;
        this.namingStrategy = namingStrategy;
    }

    @Override
    public void recordToolCall(FullOperation fullOperation) {
        try {
            var toolName = namingStrategy.name(fullOperation);
            var operationId = fullOperation.operation().getOperationId();
            var tags = List.of(Tag.of("tool_name", toolName), Tag.of("operation_id", operationId));
            meterRegistry.counter("com.infobip.openapi.tool.call", tags).increment();
        } catch (Exception e) {
            LOGGER.error("Failed to record tool call metric: {}", e.getMessage(), e);
        }
    }

    @Override
    public void recordApiCall(FullOperation fullOperation, HttpStatusCode httpStatusCode) {
        try {
            var operationId = fullOperation.operation().getOperationId();
            var tags = List.of(
                    Tag.of("operation_id", operationId), Tag.of("status_code", String.valueOf(httpStatusCode.value())));
            meterRegistry.counter("com.infobip.openapi.api.call", tags).increment();
        } catch (Exception e) {
            LOGGER.error("Failed to record API call metric: {}", e.getMessage(), e);
        }
    }

    @Override
    public Timer startTimer() {
        var sample = io.micrometer.core.instrument.Timer.start(meterRegistry);
        return new Timer() {
            @Override
            public void timeToolCall(FullOperation fullOperation, boolean isError) {
                try {
                    var toolName = namingStrategy.name(fullOperation);
                    var operationId = fullOperation.operation().getOperationId();
                    var tags = List.of(
                            Tag.of("tool_name", toolName),
                            Tag.of("operation_id", operationId),
                            Tag.of("is_error", String.valueOf(isError)));
                    sample.stop(meterRegistry.timer("com.infobip.openapi.tool.call.duration", tags));
                } catch (Exception e) {
                    LOGGER.error("Failed to record tool call duration metric: {}", e.getMessage(), e);
                }
            }

            @Override
            public void timeApiCall(FullOperation fullOperation, HttpStatusCode httpStatusCode) {
                try {
                    var operationId = fullOperation.operation().getOperationId();
                    var tags = List.of(
                            Tag.of("operation_id", operationId),
                            Tag.of("status_code", String.valueOf(httpStatusCode.value())));
                    sample.stop(meterRegistry.timer("com.infobip.openapi.api.call.duration", tags));
                } catch (Exception e) {
                    LOGGER.error("Failed to record API call duration metric: {}", e.getMessage(), e);
                }
            }
        };
    }
}

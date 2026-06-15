package com.infobip.openapi.mcp.infrastructure.metrics;

import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import org.springframework.http.HttpStatusCode;

public class NoOpMetricService implements MetricService {
    @Override
    public void recordToolCall(FullOperation fullOperation) {}

    @Override
    public void recordApiCall(FullOperation fullOperation, HttpStatusCode httpStatusCode) {}

    @Override
    public Timer startTimer() {
        return new Timer() {
            @Override
            public void timeToolCall(FullOperation fullOperation, boolean isError) {}

            @Override
            public void timeApiCall(FullOperation fullOperation, HttpStatusCode httpStatusCode) {}
        };
    }

    @Override
    public void recordPromptCall(String promptName) {}

    @Override
    public void recordPromptResolveCall(String promptName, HttpStatusCode httpStatusCode) {}

    @Override
    public PromptTimer startPromptTimer() {
        return new PromptTimer() {
            @Override
            public void timePromptCall(String promptName, boolean isError) {}

            @Override
            public void timeResolveCall(String promptName, HttpStatusCode httpStatusCode) {}
        };
    }

    @Override
    public void recordLiveReloadExecution(String status) {}

    @Override
    public LiveReloadTimer startLiveReloadTimer() {
        return status -> {};
    }
}

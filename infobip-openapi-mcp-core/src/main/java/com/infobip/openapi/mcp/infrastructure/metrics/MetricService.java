package com.infobip.openapi.mcp.infrastructure.metrics;

import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.HttpStatusCode;

public interface MetricService {

    interface Timer {
        void timeToolCall(FullOperation fullOperation, boolean isError);

        void timeApiCall(FullOperation fullOperation, HttpStatusCode httpStatusCode);
    }

    interface PromptTimer {
        void timePromptCall(String promptName, boolean isError);

        void timeResolveCall(String promptName, HttpStatusCode httpStatusCode);
    }

    interface LiveReloadTimer {
        void record(String status);
    }

    void recordToolCall(FullOperation fullOperation);

    void recordApiCall(FullOperation fullOperation, HttpStatusCode httpStatusCode);

    Timer startTimer();

    void recordPromptCall(String promptName);

    void recordPromptResolveCall(String promptName, HttpStatusCode httpStatusCode);

    PromptTimer startPromptTimer();

    void recordLiveReloadExecution(String status);

    LiveReloadTimer startLiveReloadTimer();
}

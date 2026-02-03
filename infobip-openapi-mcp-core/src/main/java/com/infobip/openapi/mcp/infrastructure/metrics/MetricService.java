package com.infobip.openapi.mcp.infrastructure.metrics;

import com.infobip.openapi.mcp.openapi.tool.FullOperation;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.HttpStatusCode;

public interface MetricService {

    interface Timer {
        void timeToolCall(FullOperation fullOperation, boolean isError);

        void timeApiCall(FullOperation fullOperation, HttpStatusCode httpStatusCode);
    }

    interface LiveReloadTimer {
        void record(String status);
    }

    void recordToolCall(FullOperation fullOperation);

    void recordApiCall(FullOperation fullOperation, HttpStatusCode httpStatusCode);

    Timer startTimer();

    void recordLiveReloadExecution(String status);

    LiveReloadTimer startLiveReloadTimer();
}

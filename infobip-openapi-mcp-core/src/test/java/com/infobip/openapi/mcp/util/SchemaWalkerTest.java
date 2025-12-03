package com.infobip.openapi.mcp.util;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class SchemaWalkerTest {

    public static Stream<Arguments> testData() {
        return Stream.of(
                arguments("/openapi/minimal.json", 0),
                arguments("/openapi/petstore.json", 17),
                arguments("/openapi/all-schemas.json", 32));
    }

    @ParameterizedTest
    @MethodSource("testData")
    void shouldWalkTheSpec(String givenSpecResource, Integer expectedNumberOfSchemas) {
        // given
        var specUri = getClass().getResource(givenSpecResource).toString();
        var givenSpec = new OpenAPIV3Parser().read(specUri);
        var count = new AtomicInteger(0);
        var walker = new SchemaWalker(s -> count.getAndIncrement());

        // when
        walker.walk(givenSpec);

        // then
        then(count.get()).isEqualTo(expectedNumberOfSchemas);
    }
}

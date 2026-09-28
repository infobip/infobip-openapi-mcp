package com.infobip.openapi.mcp.util;

import static org.assertj.core.api.BDDAssertions.then;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PlusAwareUriEncoderTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
                    # value        | expected
                    hello           | hello
                    hello world     | hello%20world
                    a+b             | a%2Bb
                    'a&b=c'         | a%26b%3Dc
                    'a?b#c'         | a?b%23c
                    '100% sure'     | 100%25%20sure
                    path/seg        | path/seg
                    café            | caf%C3%A9
                    ''              | ''
                    """)
    void shouldEncodeQueryParam(String value, String expected) {
        then(PlusAwareUriEncoder.encodeQueryParam(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
                    # value        | expected
                    hello           | hello
                    hello world     | hello%20world
                    a+b             | a%2Bb
                    'a&b=c'         | a&b=c
                    'a?b#c'         | a%3Fb%23c
                    '100% sure'     | 100%25%20sure
                    path/seg        | path%2Fseg
                    café            | caf%C3%A9
                    ''              | ''
                    """)
    void shouldEncodePathSegment(String value, String expected) {
        then(PlusAwareUriEncoder.encodePathSegment(value)).isEqualTo(expected);
    }

    @Test
    void shouldReturnNullForNullQueryParam() {
        then(PlusAwareUriEncoder.encodeQueryParam(null)).isNull();
    }

    @Test
    void shouldReturnNullForNullPathSegment() {
        then(PlusAwareUriEncoder.encodePathSegment(null)).isNull();
    }
}

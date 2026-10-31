package com.cos.orchestration.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** Builds a per-service {@link RestClient} with that service's own connect/read timeout. */
public final class RestClients {

    private RestClients() {
    }

    public static RestClient create(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        // HTTP/1.1 explicitly: the JDK client's default h2c (HTTP/2 cleartext) upgrade attempt
        // against WireMock's Jetty 11 server intermittently fails with "RST_STREAM"/"EOF
        // reached" - every bureau in this workbook is plain HTTP/1.1 anyway.
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build());
        requestFactory.setReadTimeout(readTimeout);

        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .messageConverters(converters -> converters.add(0, new MappingJackson2HttpMessageConverter(objectMapper)))
                .build();
    }
}

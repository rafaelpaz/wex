package com.wex.purchasetransactions.client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@WireMockTest
class TreasuryExchangeRateClientTest {

    private TreasuryExchangeRateClient client;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(1));
        factory.setReadTimeout(java.time.Duration.ofSeconds(1));
        RestClient restClient = RestClient.builder().requestFactory(factory).build();
        client = new TreasuryExchangeRateClient(restClient, wmInfo.getHttpBaseUrl());
    }

    // Response parsing and empty-data cases covered by TreasuryResponseParsingProperties
    // and NoRateAvailableProperties respectively.

    @Test
    void throwsExceptionOnServerError() {
        stubFor(get(urlPathEqualTo("/")).willReturn(serverError()));
        assertThrows(RuntimeException.class, () -> client.getExchangeRate("Canada-Dollar", LocalDate.of(2024, 3, 15)));
    }

    @Test
    void throwsRuntimeExceptionOnTimeout() {
        stubFor(get(urlPathEqualTo("/")).willReturn(ok().withFixedDelay(3000)));

        // The client has a 1s read timeout, so a 3s delay should trigger it
        assertThrows(RuntimeException.class, () -> client.getExchangeRate("Canada-Dollar", LocalDate.of(2024, 3, 15)));
    }

    // Query parameter verification covered by TreasuryQueryParameterProperties.

}

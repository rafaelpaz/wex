package com.wex.purchasetransactions.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Value("${treasury.api.connect-timeout:5s}")
    private Duration connectTimeout;

    @Value("${treasury.api.read-timeout:10s}")
    private Duration readTimeout;

    @Bean
    public RestClient treasuryRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);

        return RestClient.builder().requestFactory(factory).build();
    }
}

package com.wex.purchasetransactions.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI purchaseTransactionsOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("WEX Purchase Transactions API")
                .description("REST API for storing purchase transactions in US dollars and retrieving them with currency conversion using Treasury exchange rates")
                .version("1.0.0"));
    }
}

package com.example.wallet.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI walletOpenApi() {
        return new OpenAPI().info(new Info()
            .title("Wallet Service API")
            .version("1.0")
            .description("Transactional wallet payments and scheduled funding requests. " +
                "Payment creation and confirmation are intentionally separate operations.")
            .contact(new Contact().name("Wallet Service Team")));
    }
}

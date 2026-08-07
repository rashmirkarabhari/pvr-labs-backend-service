package com.pvrlabs.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8085}")
    private String serverPort;

    @Bean
    public OpenAPI paymentServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PVR 3D Labs – Payment Service API")
                        .description("""
                                Independent Payment Microservice for the PVR 3D Labs Angular storefront.
                                Integrates with Cashfree Payment Gateway. Cashfree secrets never leave the backend.
                                Designed for future composition with Order, Product, and User microservices.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("PVR 3D Labs Engineering")
                                .email("engineering@pvrlabs.com"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local"),
                        new Server().url("https://api.pvrlabs.com").description("Production")
                ));
    }
}

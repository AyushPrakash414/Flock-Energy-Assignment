package com.flockenergy.meterops.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI metadata configuration.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Urja Meter Ops API")
                        .version("1.0.0")
                        .description("""
                                Clean REST API that wraps the legacy Urja Meter Ops portal,
                                exposing meter information, network hierarchy, consumption
                                readings, and distribution transformer data in structured JSON.
                                """)
                        .contact(new Contact()
                                .name("Flock Energy Engineering")
                                .url("https://flockenergy.tech")));
    }
}

package com.transitpulse.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI is served at /swagger-ui.html once the app is running. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI transitPulseOpenApi() {
        return new OpenAPI().info(new Info()
                .title("TransitPulse API")
                .version("1.0.0")
                .description("REST API for public transit delay propagation and "
                        + "passenger impact analysis. Passenger figures are modelled "
                        + "estimates, never confirmed counts.")
                .contact(new Contact().name("TransitPulse"))
                .license(new License().name("MIT")));
    }
}

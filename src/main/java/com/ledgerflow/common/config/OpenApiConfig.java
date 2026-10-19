package com.ledgerflow.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String API_KEY_SECURITY_SCHEME = "ApiKeyAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LedgerFlow Distributed Payment Engine API")
                        .version("v1.0.0")
                        .description("Production-inspired simulated payment-processing platform featuring double-entry ledger accounting, concurrency safety, idempotent payments, and reliable transactional outbox events.")
                        .contact(new Contact()
                                .name("space-timed")
                                .url("https://github.com/space-timed/ledgerflow"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SECURITY_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(API_KEY_SECURITY_SCHEME, new SecurityScheme()
                                .name("X-Api-Key")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Merchant API Key for authenticated requests. Format: lf_live_<secret>")));
    }
}

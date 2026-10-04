package de.verdox.currencyrates.currencyrates.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Optional;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI currencyRatesOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SolarMiner Currency Rates API")
                        .description("Versioned currency, coin-price and mining-network data used by SolarMiner products.")
                        .version(implementationVersion())
                        .license(new License().name("AGPL-3.0")))
                .tags(List.of(
                        new Tag().name("Market data").description("Historical currency and Bitcoin-network values."),
                        new Tag().name("Mining network data").description("Latest complete coin-keyed network snapshots.")
                ));
    }

    private String implementationVersion() {
        return Optional.ofNullable(OpenApiConfiguration.class.getPackage().getImplementationVersion())
                .orElse("development");
    }
}

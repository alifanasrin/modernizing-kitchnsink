package kitchen.sink.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    /** Defines the OpenAPI metadata shown in Swagger UI. */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Kitchen Sink Modernization")
                        .version("1.0.0")
                        .description("This is the API documentation for Demo application.")
                        .contact(new Contact()
                                .name("Developer Support")
                                .email("alifanasreen98@gmail.com")));
    }
}


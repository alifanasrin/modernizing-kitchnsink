package kitchen.sink.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {
    /** Provides the system UTC clock used for all time-based logic. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

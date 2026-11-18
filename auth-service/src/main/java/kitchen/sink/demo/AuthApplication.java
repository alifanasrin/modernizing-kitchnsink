package kitchen.sink.demo;

import io.mongock.runner.springboot.EnableMongock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableMongock
public class AuthApplication {

	/** Starts the Spring Boot application. */
	public static void main(String[] args) {
		SpringApplication.run(AuthApplication.class, args);
	}

}

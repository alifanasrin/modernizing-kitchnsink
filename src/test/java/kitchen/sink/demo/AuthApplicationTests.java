package kitchen.sink.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(locations = "classpath:jwt-test.properties")
class AuthApplicationTests {

	@Test
	void contextLoads() {
	}

}

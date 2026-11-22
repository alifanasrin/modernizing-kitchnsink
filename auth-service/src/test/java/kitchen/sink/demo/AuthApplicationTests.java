package kitchen.sink.demo;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Starts the whole application with no MongoDB and no Valkey: Mongock is off and the rate-limit store is mocked. */
@SpringBootTest(properties = "mongock.enabled=false")
@TestPropertySource(locations = "classpath:jwt-test.properties")
class AuthApplicationTests {

	@MockitoBean
	ProxyManager<String> rateLimitProxyManager;

	@Test
	void contextLoads() {
	}

}

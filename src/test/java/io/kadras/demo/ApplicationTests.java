package io.kadras.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import({IntegrationTestSetup.class, TestcontainersConfiguration.class})
class ApplicationTests {

	@Test
	void contextLoads() {
	}

}

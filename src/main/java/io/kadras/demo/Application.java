package io.kadras.demo;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	ApplicationListener<ApplicationReadyEvent> logbackOtelAppenderInitializer(OpenTelemetry openTelemetry) {
		return _ -> OpenTelemetryAppender.install(openTelemetry);
	}

}

@RestController
class ChatController {

	private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

	private final ChatClient chatClient;
	private final VectorStore vectorStore;

	ChatController(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
		this.chatClient = chatClientBuilder.build();
		this.vectorStore = vectorStore;
	}

	@PostMapping("/chat")
	String chatWithDocument(@RequestBody String message) {
		logger.info("Received user message: {}", message);
		return chatClient.prompt()
				.advisors(new QuestionAnswerAdvisor(vectorStore))
				.user(message)
				.call()
				.content();
	}

}

@Component
class IngestionPipeline {

	private static final Logger logger = LoggerFactory.getLogger(IngestionPipeline.class);
	private final JdbcClient jdbcClient;
	private final VectorStore vectorStore;

	@Value("classpath:documents/story.md")
	Resource textFile;

	public IngestionPipeline(JdbcClient jdbcClient, VectorStore vectorStore) {
		this.jdbcClient = jdbcClient;
		this.vectorStore = vectorStore;
	}

	@PostConstruct
	public void run() {
		if ((long) jdbcClient.sql("select count(*) from vector_store").query().singleValue() > 0) {
			return;
		}

		logger.info("Loading text files as Documents");
		var documents = new TextReader(textFile).get();

		logger.info("Creating and storing Embeddings from Documents");
		vectorStore.add(new TokenTextSplitter().split(documents));
	}

}

@Configuration(proxyBeanMethods = false)
class HttpClientAutoConfiguration {

	@Bean
	RestClientCustomizer restClientCustomizer() {
		return restClientBuilder -> {
			restClientBuilder
					.requestFactory(ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
									.withConnectTimeout(Duration.ofSeconds(120))
									.withReadTimeout(Duration.ofSeconds(120))));
		};
	}

}

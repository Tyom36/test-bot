package ru.tyom.test_bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.tyom.test_bot.config.TelegramBotProperties;

@SpringBootApplication
@EnableConfigurationProperties(TelegramBotProperties.class)
public class TestBotApplication {

	public static void main(String[] args) {
		SpringApplication.run(TestBotApplication.class, args);
	}

}

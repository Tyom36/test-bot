package ru.tyom.test_bot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.tyom.test_bot.bot.SimpleBot;
import ru.tyom.test_bot.exception.BotRegistrationException;
import ru.tyom.test_bot.service.MessageHandler;

@Configuration
public class TelegramBotsConfig {

	@Bean
	public TelegramClient telegramClient(TelegramBotProperties properties) {
		return new OkHttpTelegramClient(properties.getToken());
	}

	@Bean
	public TelegramBotsLongPollingApplication telegramBotsApplication() {
		return new TelegramBotsLongPollingApplication();
	}

	@Bean
	public SimpleBot simpleBot(TelegramBotProperties properties, TelegramBotsLongPollingApplication botsApp,
							   TelegramClient telegramClient, MessageHandler messageHandler) {
		SimpleBot bot = new SimpleBot(properties, messageHandler, telegramClient);
		try {
			botsApp.registerBot(properties.getToken(), bot);
		} catch (TelegramApiException e) {
			throw new BotRegistrationException("Failed to register bot: " + properties.getUsername(), e);
		}
		return bot;
	}
}

package ru.tyom.test_bot.bot;

import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.tyom.test_bot.config.TelegramBotProperties;
import ru.tyom.test_bot.service.MessageHandler;

public class SimpleBot implements LongPollingSingleThreadUpdateConsumer {

	private final MessageHandler messageHandler;
	private final TelegramClient telegramClient;
	private final String botUsername;

	public SimpleBot(TelegramBotProperties properties, MessageHandler messageHandler, TelegramClient telegramClient) {
		this.messageHandler = messageHandler;
		this.telegramClient = telegramClient;
		this.botUsername = properties.getUsername();
	}

	@Override
	public void consume(Update update) {
		messageHandler.handle(update, telegramClient);
	}

	public String getBotUsername() {
		return botUsername;
	}
}

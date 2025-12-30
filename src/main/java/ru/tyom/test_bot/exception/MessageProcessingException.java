package ru.tyom.test_bot.exception;

public class MessageProcessingException extends BotException {

	public MessageProcessingException(String message) {
		super(message);
	}

	public MessageProcessingException(String message, Throwable cause) {
		super(message, cause);
	}
}

package ru.tyom.test_bot.exception;

public class BotRegistrationException extends BotException {

	public BotRegistrationException(String message) {
		super(message);
	}

	public BotRegistrationException(String message, Throwable cause) {
		super(message, cause);
	}
}

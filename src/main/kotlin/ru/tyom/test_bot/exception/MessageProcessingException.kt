package ru.tyom.test_bot.exception

class MessageProcessingException(message: String?, cause: Throwable? = null) : BotException(message, cause)

package ru.tyom.test_bot.exception

open class BotException(message: String?, cause: Throwable? = null) : RuntimeException(message, cause)

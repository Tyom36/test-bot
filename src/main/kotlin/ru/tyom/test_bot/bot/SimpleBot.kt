package ru.tyom.test_bot.bot

import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.generics.TelegramClient
import ru.tyom.test_bot.config.TelegramBotProperties
import ru.tyom.test_bot.service.MessageHandler

class SimpleBot(
    properties: TelegramBotProperties,
    private val messageHandler: MessageHandler,
    private val telegramClient: TelegramClient
) : LongPollingSingleThreadUpdateConsumer {

    private val botUsername: String = properties.username ?: ""

    override fun consume(update: Update) {
        messageHandler.handle(update, telegramClient)
    }

    fun getBotUsername(): String = botUsername
}

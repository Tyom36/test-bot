package ru.tyom.test_bot.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication
import org.telegram.telegrambots.meta.generics.TelegramClient
import ru.tyom.test_bot.bot.SimpleBot
import ru.tyom.test_bot.exception.BotRegistrationException
import ru.tyom.test_bot.service.MessageHandler

@Configuration
class TelegramBotsConfig {

    @Bean
    fun telegramClient(properties: TelegramBotProperties): TelegramClient {
        return OkHttpTelegramClient(properties.token)
    }

    @Bean
    fun telegramBotsApplication(): TelegramBotsLongPollingApplication {
        return TelegramBotsLongPollingApplication()
    }

    @Bean
    fun simpleBot(
        properties: TelegramBotProperties,
        botsApp: TelegramBotsLongPollingApplication,
        telegramClient: TelegramClient,
        messageHandler: MessageHandler
    ): SimpleBot {
        val bot = SimpleBot(properties, messageHandler, telegramClient)
        try {
            botsApp.registerBot(properties.token, bot)
        } catch (e: Exception) {
            throw BotRegistrationException("Failed to register bot: ${properties.username}", e)
        }
        return bot
    }
}

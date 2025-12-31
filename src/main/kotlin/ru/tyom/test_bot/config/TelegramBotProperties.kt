package ru.tyom.test_bot.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "telegram.bot")
class TelegramBotProperties {
    var username: String? = null
    var token: String? = null
}

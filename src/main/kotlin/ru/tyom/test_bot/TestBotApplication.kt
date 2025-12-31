package ru.tyom.test_bot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import ru.tyom.test_bot.config.TelegramBotProperties

@SpringBootApplication
@EnableConfigurationProperties(TelegramBotProperties::class)
class TestBotApplication

fun main(args: Array<String>) {
    runApplication<TestBotApplication>(*args)
}

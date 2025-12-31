package ru.tyom.test_bot.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendVideo
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.meta.generics.TelegramClient
import ru.tyom.test_bot.exception.MessageProcessingException
import java.io.File
import java.util.concurrent.TimeoutException
import java.util.regex.Pattern

@Service
class MessageHandler(
    private val youTubeDownloader: YouTubeDownloader,
    private val quotesService: ZhirinovskyQuotesService
) {
    private val logger = LoggerFactory.getLogger(MessageHandler::class.java)

    companion object {
        private val YOUTUBE_URL_PATTERN = Pattern.compile(
            "^(https?\\:\\/\\/)?(www\\.)?(youtube\\.com|youtu\\.?be)\\/.+$"
        )
    }

    fun handle(update: Update, telegramClient: TelegramClient) {
        if (update.hasMessage() && update.message.hasText()) {
            handleMessage(update, telegramClient)
        }
    }

    private fun handleMessage(update: Update, telegramClient: TelegramClient) {
        val text = update.message.text
        val chatId = update.message.chatId

        when (text) {
            "/start" -> send(telegramClient, chatId, "Привет! Отправь мне ссылку на YouTube видео, и я скачаю его для тебя.")
            else -> {
                if (isYouTubeUrl(text)) {
                    handleYouTubeDownload(text, chatId, telegramClient)
                } else {
                    send(telegramClient, chatId, "Не понимаю команду. Отправь ссылку на YouTube видео.")
                }
            }
        }
    }

    private fun isYouTubeUrl(text: String?): Boolean {
        return text != null && YOUTUBE_URL_PATTERN.matcher(text).matches()
    }

    private fun handleYouTubeDownload(url: String, chatId: Long, telegramClient: TelegramClient) {
        send(telegramClient, chatId, quotesService.getRandomDownloadingQuote())

        try {
            val videoFile = youTubeDownloader.downloadVideo(url)

            if (videoFile.length() > 50 * 1024 * 1024) {
                send(
                    telegramClient,
                    chatId,
                    "Видео слишком большое для Telegram (лимит 50MB). Попробуй другое видео."
                )
                youTubeDownloader.cleanup(videoFile)
                return
            }

            val sendVideo = SendVideo.builder()
                .chatId(chatId.toString())
                .video(InputFile(videoFile))
                .build()

            telegramClient.execute(sendVideo)
            logger.info("Video sent to chat: {}", chatId)

            youTubeDownloader.cleanup(videoFile)

        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            send(telegramClient, chatId, quotesService.getRandomErrorQuote())
            logger.error("Download interrupted for chat: {}", chatId, e)
        } catch (e: TimeoutException) {
            send(telegramClient, chatId, quotesService.getRandomErrorQuote())
            logger.error("Download timeout for chat: {}", chatId, e)
        } catch (e: Exception) {
            send(telegramClient, chatId, quotesService.getRandomErrorQuote())
            logger.error("Failed to download video for chat: {}", chatId, e)
        }
    }

    private fun send(telegramClient: TelegramClient, chatId: Long, text: String) {
        try {
            telegramClient.execute(SendMessage(chatId.toString(), text))
        } catch (e: TelegramApiException) {
            throw MessageProcessingException("Failed to send message to chat: $chatId", e)
        }
    }
}

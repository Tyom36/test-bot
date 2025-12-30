package ru.tyom.test_bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.tyom.test_bot.exception.MessageProcessingException;

import java.io.File;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

@Slf4j
@Service
public class MessageHandler {

	private final YouTubeDownloader youTubeDownloader;
	private final ZhirinovskyQuotesService quotesService;

	private static final Pattern YOUTUBE_URL_PATTERN = Pattern.compile(
		"^(https?\\:\\/\\/)?(www\\.)?(youtube\\.com|youtu\\.?be)\\/.+$"
	);

	public MessageHandler(YouTubeDownloader youTubeDownloader, ZhirinovskyQuotesService quotesService) {
		this.youTubeDownloader = youTubeDownloader;
		this.quotesService = quotesService;
	}

	public void handle(Update update, TelegramClient telegramClient) {
		if (update.hasMessage() && update.getMessage().hasText()) {
			handleMessage(update, telegramClient);
		}
	}

	private void handleMessage(Update update, TelegramClient telegramClient) {
		String text = update.getMessage().getText();
		Long chatId = update.getMessage().getChatId();

		if ("/start".equals(text)) {
			send(telegramClient, chatId,
				"Привет! Отправь мне ссылку на YouTube видео, и я скачаю его для тебя.");
		} else if (isYouTubeUrl(text)) {
			handleYouTubeDownload(text, chatId, telegramClient);
		} else {
			send(telegramClient, chatId, "Не понимаю команду. Отправь ссылку на YouTube видео.");
		}
	}

	private boolean isYouTubeUrl(String text) {
		return YOUTUBE_URL_PATTERN.matcher(text).matches();
	}

	private void handleYouTubeDownload(String url, Long chatId, TelegramClient telegramClient) {
		send(telegramClient, chatId, quotesService.getRandomDownloadingQuote());

		try {
			File videoFile = youTubeDownloader.downloadVideo(url);

			if (videoFile.length() > 50 * 1024 * 1024) {
				send(telegramClient, chatId,
					"Видео слишком большое для Telegram (лимит 50MB). Попробуй другое видео.");
				youTubeDownloader.cleanup(videoFile);
				return;
			}

			SendVideo sendVideo = SendVideo.builder()
				.chatId(chatId.toString())
				.video(new InputFile(videoFile))
				.build();

			telegramClient.execute(sendVideo);
			log.info("Video sent to chat: {}", chatId);

			youTubeDownloader.cleanup(videoFile);

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			send(telegramClient, chatId, quotesService.getRandomErrorQuote());
			log.error("Download interrupted for chat: {}", chatId, e);
		} catch (TimeoutException e) {
			send(telegramClient, chatId, quotesService.getRandomErrorQuote());
			log.error("Download timeout for chat: {}", chatId, e);
		} catch (Exception e) {
			send(telegramClient, chatId, quotesService.getRandomErrorQuote());
			log.error("Failed to download video for chat: {}", chatId, e);
		}
	}

	private void send(TelegramClient telegramClient, Long chatId, String text) {
		try {
			telegramClient.execute(new SendMessage(chatId.toString(), text));
		} catch (TelegramApiException e) {
			throw new MessageProcessingException("Failed to send message to chat: " + chatId, e);
		}
	}
}

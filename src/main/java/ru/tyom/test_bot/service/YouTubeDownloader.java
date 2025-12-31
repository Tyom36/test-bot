package ru.tyom.test_bot.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class YouTubeDownloader {

	private final VideoCompressor videoCompressor;

	@Value("${youtube.download.temp-dir:./temp}")
	private String tempDir;

	public YouTubeDownloader(VideoCompressor videoCompressor) {
		this.videoCompressor = videoCompressor;
	}

	@Value("${youtube.downloader:yt-dlp}")
	private String downloaderCommand;

	@Value("${youtube.download.timeout-seconds:30}")
	private int timeoutSeconds;

	@Value("${youtube.download.retries:2}")
	private int retries;

	@Value("${youtube.download.socket-timeout:30}")
	private int socketTimeout;

	@Value("${youtube.download.extractor-retries:2}")
	private int extractorRetries;

	@Value("${youtube.cookies-path}")
	private String cookiesPath;

	public File downloadVideo(String url) throws IOException, InterruptedException, TimeoutException {
		Path tempPath = Paths.get(tempDir);
		if (!Files.exists(tempPath)) {
			Files.createDirectories(tempPath);
		}

		String outputTemplate = tempPath.resolve("%(title)s-[%(id)s].%(ext)s").toString();

		log.info("Downloading video from YouTube: {}", url);

		ProcessBuilder processBuilder = new ProcessBuilder();

		List<String> command = new ArrayList<>();
		if (SystemUtils.IS_OS_WINDOWS) {
			command.add("cmd");
			command.add("/c");
		}
		command.add(downloaderCommand);
		command.add("--cookies");
		command.add(cookiesPath);
		command.add("-f");
		command.add("bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best");
		command.add("--merge-output-format");
		command.add("mp4");
		command.add("-o");
		command.add(outputTemplate);
		command.add("--no-playlist");
		command.add("--retries");
		command.add(String.valueOf(retries));
		command.add("--socket-timeout");
		command.add(String.valueOf(socketTimeout));
		command.add("--extractor-retries");
		command.add(String.valueOf(extractorRetries));
		command.add("--no-check-certificates");
		command.add(url);

		processBuilder.command(command);

		processBuilder.redirectErrorStream(true);
		Process process = processBuilder.start();

		AtomicReference<StringBuilder> outputRef = new AtomicReference<>(new StringBuilder());
		Thread outputReader = new Thread(() -> {
			try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					outputRef.get().append(line).append("\n");
					log.debug("yt-dlp: {}", line);
				}
			} catch (IOException e) {
				log.error("Error reading process output", e);
			}
		});
		outputReader.setDaemon(true);
		outputReader.start();

		boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
		if (!finished) {
			process.destroyForcibly();
			throw new TimeoutException("Download timeout exceeded (" + timeoutSeconds + " seconds)");
		}

		int exitCode = process.exitValue();
		if (exitCode != 0) {
			throw new IOException("Failed to download video. Exit code: " + exitCode + "\nOutput: " + outputRef.get());
		}

		File downloadedFile = findDownloadedFile(tempPath);
		if (downloadedFile == null) {
			throw new IOException("Downloaded file not found in temp directory");
		}

		log.info("Video downloaded successfully: {}", downloadedFile.getName());

		// Compress all videos
		downloadedFile = videoCompressor.compress(downloadedFile);

		return downloadedFile;
	}

	private File findDownloadedFile(Path directory) throws IOException {
		try (var stream = Files.list(directory)) {
			return stream
				.filter(Files::isRegularFile)
				.map(Path::toFile)
				.filter(f -> f.getName().endsWith(".mp4") || f.getName().endsWith(".webm") || f.getName().endsWith(".mkv"))
				.filter(f -> !f.getName().startsWith("compressed_"))  // Skip already compressed files
				.findFirst()
				.orElse(null);
		}
	}

	public void cleanup(File file) {
		if (file != null && file.exists()) {
			try {
				Files.delete(file.toPath());
				log.info("Deleted temporary file: {}", file.getName());
			} catch (IOException e) {
				log.warn("Failed to delete temporary file: {}", file.getName(), e);
			}
		}
	}
}

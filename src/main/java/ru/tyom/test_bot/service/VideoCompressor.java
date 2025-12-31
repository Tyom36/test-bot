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
public class VideoCompressor {

	@Value("${youtube.download.temp-dir:./temp}")
	private String tempDir;

	@Value("${youtube.download.timeout-seconds:30}")
	private int timeoutSeconds;

	@Value("${video.compression.crf:28}")
	private int crf;

	@Value("${video.compression.preset:veryfast}")
	private String preset;

	public File compress(File inputFile) throws IOException, InterruptedException, TimeoutException {
		log.info("Compressing video: {} ({} MB)", inputFile.getName(), inputFile.length() / 1024 / 1024);

		Path outputPath = Paths.get(tempDir, "compressed_" + inputFile.getName());

		ProcessBuilder processBuilder = new ProcessBuilder();

		List<String> command = new ArrayList<>();
		if (SystemUtils.IS_OS_WINDOWS) {
			command.add("cmd");
			command.add("/c");
		}
		command.add("ffmpeg");
		command.add("-i");
		command.add(inputFile.getAbsolutePath());
		command.add("-c:v");
		command.add("libx264");
		command.add("-crf");
		command.add(String.valueOf(crf));
		command.add("-preset");
		command.add(preset);
		command.add("-c:a");
		command.add("aac");
		command.add("-b:a");
		command.add("128k");
		command.add("-movflags");
		command.add("+faststart");
		command.add("-y");
		command.add(outputPath.toString());

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
					log.debug("ffmpeg: {}", line);
				}
			} catch (IOException e) {
				log.error("Error reading ffmpeg output", e);
			}
		});
		outputReader.setDaemon(true);
		outputReader.start();

		boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
		if (!finished) {
			process.destroyForcibly();
			throw new TimeoutException("Compression timeout exceeded (" + timeoutSeconds + " seconds)");
		}

		int exitCode = process.exitValue();
		if (exitCode != 0) {
			throw new IOException("Failed to compress video. Exit code: " + exitCode + "\nOutput: " + outputRef.get());
		}

		// Delete original file
		Files.delete(inputFile.toPath());

		File compressedFile = outputPath.toFile();
		log.info("Video compressed: {} ({} MB)", compressedFile.getName(), compressedFile.length() / 1024 / 1024);
		return compressedFile;
	}
}

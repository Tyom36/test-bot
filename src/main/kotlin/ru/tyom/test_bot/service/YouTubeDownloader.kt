package ru.tyom.test_bot.service

import org.apache.commons.lang3.SystemUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Service
class YouTubeDownloader(private val videoCompressor: VideoCompressor) {
    private val logger = LoggerFactory.getLogger(YouTubeDownloader::class.java)

    @Value("\${youtube.download.temp-dir:./temp}")
    private var tempDir: String = "./temp"

    @Value("\${youtube.downloader:yt-dlp}")
    private var downloaderCommand: String = "yt-dlp"

    @Value("\${youtube.download.timeout-seconds:30}")
    private var timeoutSeconds: Int = 30

    @Value("\${youtube.download.retries:2}")
    private var retries: Int = 2

    @Value("\${youtube.download.socket-timeout:30}")
    private var socketTimeout: Int = 30

    @Value("\${youtube.download.extractor-retries:2}")
    private var extractorRetries: Int = 2

    @Throws(InterruptedException::class, TimeoutException::class, IOException::class)
    fun downloadVideo(url: String): File {
        val tempPath = Paths.get(tempDir)
        if (!Files.exists(tempPath)) {
            Files.createDirectories(tempPath)
        }

        val outputTemplate = tempPath.resolve("%(title)s-[%(id)s].%(ext)s").toString()

        logger.info("Downloading video from YouTube: {}", url)

        val command = buildList {
            if (SystemUtils.IS_OS_WINDOWS) {
                add("cmd")
                add("/c")
            }
            add(downloaderCommand)
            add("-f")
            add("bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
            add("--merge-output-format")
            add("mp4")
            add("-o")
            add(outputTemplate)
            add("--no-playlist")
            add("--retries")
            add(retries.toString())
            add("--socket-timeout")
            add(socketTimeout.toString())
            add("--extractor-retries")
            add(extractorRetries.toString())
            add("--no-check-certificates")
            add(url)
        }

        val processBuilder = ProcessBuilder(command)
        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()

        val outputRef = arrayOfNulls<String>(1)
        val outputReader = Thread {
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        outputRef[0] = outputRef[0] + line + "\n"
                        logger.debug("yt-dlp: {}", line)
                    }
                }
            } catch (e: IOException) {
                logger.error("Error reading process output", e)
            }
        }
        outputReader.isDaemon = true
        outputReader.start()

        val finished = process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw TimeoutException("Download timeout exceeded ($timeoutSeconds seconds)")
        }

        val exitCode = process.exitValue()
        if (exitCode != 0) {
            throw IOException("Failed to download video. Exit code: $exitCode\nOutput: ${outputRef[0]}")
        }

        val downloadedFile = findDownloadedFile(tempPath)
            ?: throw IOException("Downloaded file not found in temp directory")

        logger.info("Video downloaded successfully: {}", downloadedFile.name)

        // Compress all videos
        val compressedFile = videoCompressor.compress(downloadedFile)

        return compressedFile
    }

    @Throws(IOException::class)
    private fun findDownloadedFile(directory: Path): File? {
        Files.list(directory).use { stream ->
            return stream
                .filter { Files.isRegularFile(it) }
                .map { it.toFile() }
                .filter { it.name.endsWith(".mp4") || it.name.endsWith(".webm") || it.name.endsWith(".mkv") }
                .filter { !it.name.startsWith("compressed_") }  // Skip already compressed files
                .findFirst()
                .orElse(null)
        }
    }

    fun cleanup(file: File?) {
        if (file != null && file.exists()) {
            try {
                Files.delete(file.toPath())
                logger.info("Deleted temporary file: {}", file.name)
            } catch (e: IOException) {
                logger.warn("Failed to delete temporary file: {}", file.name, e)
            }
        }
    }
}

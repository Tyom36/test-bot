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
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Service
class VideoCompressor {
    private val logger = LoggerFactory.getLogger(VideoCompressor::class.java)

    @Value("\${youtube.download.temp-dir:./temp}")
    private var tempDir: String = "./temp"

    @Value("\${youtube.download.timeout-seconds:30}")
    private var timeoutSeconds: Int = 30

    @Value("\${video.compression.crf:28}")
    private var crf: Int = 28

    @Value("\${video.compression.preset:veryfast}")
    private var preset: String = "veryfast"

    @Throws(InterruptedException::class, TimeoutException::class, IOException::class)
    fun compress(inputFile: File): File {
        logger.info("Compressing video: {} ({} MB)", inputFile.name, inputFile.length() / 1024 / 1024)

        val outputPath = Paths.get(tempDir, "compressed_" + inputFile.name)

        val command = buildList {
            if (SystemUtils.IS_OS_WINDOWS) {
                add("cmd")
                add("/c")
            }
            add("ffmpeg")
            add("-i")
            add(inputFile.absolutePath)
            add("-c:v")
            add("libx264")
            add("-crf")
            add(crf.toString())
            add("-preset")
            add(preset)
            add("-c:a")
            add("aac")
            add("-b:a")
            add("128k")
            add("-movflags")
            add("+faststart")
            add("-y")
            add(outputPath.toString())
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
                        logger.debug("ffmpeg: {}", line)
                    }
                }
            } catch (e: IOException) {
                logger.error("Error reading ffmpeg output", e)
            }
        }
        outputReader.isDaemon = true
        outputReader.start()

        val finished = process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw TimeoutException("Compression timeout exceeded ($timeoutSeconds seconds)")
        }

        val exitCode = process.exitValue()
        if (exitCode != 0) {
            throw IOException("Failed to compress video. Exit code: $exitCode\nOutput: ${outputRef[0]}")
        }

        // Delete original file
        Files.delete(inputFile.toPath())

        val compressedFile = outputPath.toFile()
        logger.info("Video compressed: {} ({} MB)", compressedFile.name, compressedFile.length() / 1024 / 1024)
        return compressedFile
    }
}

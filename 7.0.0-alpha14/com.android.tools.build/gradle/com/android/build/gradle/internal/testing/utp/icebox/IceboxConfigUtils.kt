/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal.testing.utp

import com.google.common.annotations.VisibleForTesting
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import java.util.logging.Logger
import java.util.regex.Pattern
import kotlin.streams.asSequence

// Emulator gRPC port
@VisibleForTesting
const val DEFAULT_EMULATOR_GRPC_PORT = 8554

private val LOG = Logger.getLogger("IceboxConfigUtils")

/**
 * Returns the Emulator registration directory.
 */
private fun computeRegistrationDirectoryContainer(): Path? {
    val os = System.getProperty("os.name").toLowerCase(Locale.ROOT)
    when {
        os.startsWith("mac") -> {
            return Paths.get(
                System.getenv("HOME") ?: "/",
                "Library",
                "Caches",
                "TemporaryItems"
            )
        }
        os.startsWith("win") -> {
            return Paths.get(System.getenv("LOCALAPPDATA") ?: "/", "Temp")
        }
        else -> { // Linux and Chrome OS.
            for (dirstr in arrayOf(
                System.getenv("XDG_RUNTIME_DIR"),
                "/run/user/${getUid()}",
                System.getenv("ANDROID_EMULATOR_HOME"),
                System.getenv("ANDROID_PREFS_ROOT"),
                System.getenv("ANDROID_SDK_HOME"),
                (System.getenv("HOME") ?: "/") + ".android",
                // For integration tests, we use java property.
                System.getProperty("android.emulator.home")
            )) {
                if (dirstr == null) {
                    continue
                }
                try {
                    val dir = Paths.get(dirstr)
                    if (Files.isDirectory(dir)) {
                        return dir
                    }
                } catch (exception: InvalidPathException) {
                    LOG.finer("Failed to parse dir ${dirstr}, exception ${exception}")
                }
            }

            return Paths.get(
                FileUtils.getTempDirectory().absolutePath,
                "android-" + System.getProperty("USER")
            )
        }
    }
}

private fun getUid(): String? {
    try {
        val userName = System.getProperty("user.name")
        val command = "id -u $userName"
        val process = Runtime.getRuntime().exec(command)
        process.inputStream.use {
            val result = String(it.readBytes(), StandardCharsets.UTF_8).trim()
            if (result.isEmpty()) {
                return null
            }
            return result
        }
    } catch (e: IOException) {
        return null
    }
}

data class EmulatorGrpcInfo(val port: Int, val token: String?)

fun findGrpcInfo(deviceSerial: String, file: Path): EmulatorGrpcInfo? {
    var currentGrpcPort = DEFAULT_EMULATOR_GRPC_PORT
    var currentGrpcToken: String? = null
    var matchedAvd = false
    Files.readAllLines(file).forEach { line ->
        when {
            line.startsWith("grpc.port=") -> {
                currentGrpcPort =
                    Integer.parseInt(line.substring("grpc.port=".length), 10)
            }
            line.startsWith("grpc.token=") -> {
                currentGrpcToken = line.substring("grpc.token=".length)
            }
            line.startsWith("port.serial=") -> {
                val serial = line.substring("port.serial=".length)
                matchedAvd = ("emulator-" + serial == deviceSerial)
            }
        }
    }
    if (matchedAvd) {
        return EmulatorGrpcInfo(currentGrpcPort, currentGrpcToken)
    } else {
        return null
    }
}


fun findGrpcInfo(deviceSerial: String): EmulatorGrpcInfo {
    try {
        val fileNamePattern = Pattern.compile("pid_\\d+.ini")
        val directory = computeRegistrationDirectoryContainer()?.resolve("avd/running")
        return Files.list(directory).asSequence().map { file ->
            if (fileNamePattern.matcher(file.fileName.toString()).matches()) {
                findGrpcInfo(deviceSerial, file)
            } else {
                null
            }
        }.filterNotNull().firstOrNull() ?: EmulatorGrpcInfo(DEFAULT_EMULATOR_GRPC_PORT, null)
    } catch (exception: Throwable) {
        LOG.fine(
            "Failed to parse emulator gRPC port, fallback to default,"
                    + " exception ${exception}"
        )
        return EmulatorGrpcInfo(DEFAULT_EMULATOR_GRPC_PORT, null)
    }
}

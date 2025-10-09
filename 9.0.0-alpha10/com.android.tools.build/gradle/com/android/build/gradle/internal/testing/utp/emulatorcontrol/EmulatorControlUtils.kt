/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.build.gradle.internal.testing.utp.emulatorcontrol

import com.google.common.annotations.VisibleForTesting
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.jwt.JwkSetConverter
import com.google.crypto.tink.jwt.JwtPublicKeySign
import com.google.crypto.tink.jwt.JwtSignatureConfig
import com.google.crypto.tink.jwt.RawJwt
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.logging.Logger
import java.util.regex.Pattern
import kotlin.streams.asSequence

// Emulator gRPC port
@VisibleForTesting
const val DEFAULT_EMULATOR_GRPC_PORT = "8554"

private val LOG = Logger.getLogger("EmulatorAccessUtils")

data class JwtConfig(val token: String, val jwkPath: String)

val INVALID_JWT_CONFIG = JwtConfig("", "")

fun createTokenConfig(aud: Set<String>, validForSeconds: Int, iss: String, info: EmulatorGrpcInfo?): JwtConfig {
    if (info == null) {
        return INVALID_JWT_CONFIG;
    }
    // We do not want to enable this feature if:
    // - The emulator is not using jwks. We won't be able to authenticate properly as
    //   the emulator might be using the `older` -use-grpc-token used by the embedded-emulator
    //   this token which gives blanket access.
    // - The emulator is not supporting allowlists. In this case it is unclear which
    //   methods are (in)accessible.
    if (info.jwks.isNullOrEmpty() || info.allowlist.isNullOrEmpty()) {
        LOG.severe("This emulator is not protected with an allowlist, or jwt enabled")
        return INVALID_JWT_CONFIG
    }

    return createJwtConfig(aud, validForSeconds, iss, info.jwks)
}

fun createJwtConfig(aud: Set<String>, validForSeconds: Int, iss: String, jwkDirectory: String?): JwtConfig {
    if (jwkDirectory.isNullOrEmpty()) {
        return INVALID_JWT_CONFIG;
    }
    JwtSignatureConfig.register();

    val tinkTemplate = KeyTemplates.get("JWT_ES512")
    val handle = KeysetHandle.generateNew(tinkTemplate)

    // Generate a JWK that the emulator can use, and place it in the discovery directory.
    val jwkOutputFile = jwkDirectory + File.separator + handle.primary.id + ".jwk"
    val jwk = JwkSetConverter.fromPublicKeysetHandle(handle.publicKeysetHandle)

    LOG.fine("Writing jwk: $jwk to: $jwkOutputFile")
    File(jwkOutputFile).bufferedWriter().use { out -> out.write(jwk) }

    val now = Instant.now()
    val claimSet =
        RawJwt.newBuilder()
            .setIssuer(iss)
            .setExpiration(now.plusSeconds(validForSeconds.toLong()))
            .setNotBefore(now)
            .setIssuedAt(now)
            .setJwtId(UUID.randomUUID().toString())

    if (aud.isNotEmpty()) {
        claimSet.setAudiences(aud.toList())
    }

    val rawJwt = claimSet.build()
    val signer = handle.getPrimitive(JwtPublicKeySign::class.java)
    val signedJwt = signer.signAndEncode(rawJwt)
    LOG.fine("Signing claims: $rawJwt with jwk: ${handle.primary.id}")

    return JwtConfig(signedJwt, jwkOutputFile)
}

/** Returns the Emulator registration directory. */
fun computeRegistrationDirectoryContainer(): Path? {
    val os = System.getProperty("os.name").lowercase(Locale.ROOT)
    when {
        os.startsWith("mac") -> {
            return Paths.get(System.getProperty("user.home") ?: "/", "Library", "Caches", "TemporaryItems")
        }

        os.startsWith("win") -> {
            return Paths.get(System.getenv("LOCALAPPDATA") ?: "/", "Temp")
        }

        else -> { // Linux and Chrome OS.
            for (dirstr in
            arrayOf(
                System.getenv("XDG_RUNTIME_DIR"),
                "/run/user/${getUid()}",
                System.getenv("ANDROID_EMULATOR_HOME"),
                System.getenv("ANDROID_PREFS_ROOT"),
                System.getenv("ANDROID_SDK_HOME"),
                (System.getProperty("user.home") ?: "/") + ".android"
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
                    LOG.finer("Failed to parse dir $dirstr, exception $exception")
                }
            }

            return Paths.get(
                FileUtils.getTempDirectory().absolutePath,
                "android-" + System.getProperty("user.name")
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

data class EmulatorGrpcInfo(
    val port: Int,
    val token: String?,
    val serverCert: String?,
    val jwks: String?,
    val allowlist: String?
)

fun findGrpcInfo(deviceSerial: String, file: Path): EmulatorGrpcInfo? {
    var discovered = mutableMapOf<String, String>()
    Files.readAllLines(file).forEach { line ->
        val keyValuePair = line.split("=", limit = 2)
        discovered.put(keyValuePair[0], keyValuePair[1])
    }
    val serial = discovered.getOrDefault("port.serial", "")
    val matchedAvd = ("emulator-" + serial == deviceSerial)

    if (matchedAvd) {
        return EmulatorGrpcInfo(
            discovered.getOrDefault("grpc.port", DEFAULT_EMULATOR_GRPC_PORT).toInt(),
            discovered.getOrDefault("grpc.token", ""),
            discovered.getOrDefault("grpc.server_cert", ""),
            discovered.getOrDefault("grpc.jwks", ""),
            discovered.getOrDefault("grpc.allowlist", "")
        )
    } else {
        return null
    }
}

fun findGrpcInfo(deviceSerial: String): EmulatorGrpcInfo {
    try {
        val fileNamePattern = Pattern.compile("pid_\\d+.ini")
        val directory = computeRegistrationDirectoryContainer()?.resolve("avd/running")
        return Files.list(directory)
            .asSequence()
            .map { file ->
                if (fileNamePattern.matcher(file.fileName.toString()).matches()) {
                    findGrpcInfo(deviceSerial, file)
                } else {
                    null
                }
            }
            .filterNotNull()
            .firstOrNull()
            ?: EmulatorGrpcInfo(DEFAULT_EMULATOR_GRPC_PORT.toInt(), null, null, null, null)
    } catch (exception: Throwable) {
        LOG.fine(
            "Failed to parse emulator gRPC port, fallback to default," +
                " exception ${exception}"
        )
        return EmulatorGrpcInfo(DEFAULT_EMULATOR_GRPC_PORT.toInt(), null, null, null, null)
    }
}

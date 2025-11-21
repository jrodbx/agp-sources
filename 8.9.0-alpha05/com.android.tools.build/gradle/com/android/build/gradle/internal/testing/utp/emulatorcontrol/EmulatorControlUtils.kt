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
package com.android.build.gradle.internal.testing.utp

import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.jwt.JwkSetConverter
import com.google.crypto.tink.jwt.JwtPublicKeySign
import com.google.crypto.tink.jwt.JwtSignatureConfig
import com.google.crypto.tink.jwt.RawJwt
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger

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



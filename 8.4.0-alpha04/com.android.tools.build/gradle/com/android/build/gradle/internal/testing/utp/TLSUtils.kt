/*
 * Copyright (C) 2021 The Android Open Source Project
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

import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.Date

/**
 * Generates a self-certificated RSA key pair for inter-process gRPC communication locally.
 *
 * @param certFile a destination of the self-certificated public key
 * @param privateKeyFile a destination of the private key
 */
fun generateRsaKeyPair(certFile: File, privateKeyFile: File) {
    val keyPair = KeyPairGenerator.getInstance("RSA").run {
        initialize(4096, SecureRandom())
        generateKeyPair()
    }
    writePemFile(PemObject("CERTIFICATE", createCert(keyPair).encoded), certFile)
    writePemFile(PemObject("RSA PRIVATE KEY", keyPair.private.encoded), privateKeyFile)
}

/**
 * Creates a self-certificated public key from a given RSA key pair.
 */
private fun createCert(keyPair: KeyPair): X509Certificate {
    val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
    val name = X500Name("CN=localhost")
    val builder = X509v3CertificateBuilder(
            name,
            BigInteger.valueOf(SecureRandom().nextLong()),
            Date(System.currentTimeMillis()),
            Date(System.currentTimeMillis() + Duration.ofDays(365).toMillis()),
            name,
            SubjectPublicKeyInfo.getInstance(ASN1Sequence.getInstance(keyPair.public.encoded)))
    val certHolder = builder.build(signer)
    return JcaX509CertificateConverter().apply {
        setProvider(BouncyCastleProvider())
    }.getCertificate(certHolder)
}

/**
 * Writes a [PemObject] into a file.
 */
private fun writePemFile(pemObject: PemObject, file: File) {
    PemWriter(OutputStreamWriter(FileOutputStream(file))).use { writer ->
        writer.writeObject(pemObject)
    }
}

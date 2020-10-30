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

package com.android.apksig.internal.apk.stamp;

import static com.android.apksig.internal.apk.ApkSigningBlockUtils.encodeAsSequenceOfLengthPrefixedPairsOfIntAndLengthPrefixedBytes;
import static com.android.apksig.internal.apk.stamp.SourceStampSigner.SOURCE_STAMP_BLOCK_ID;

import com.android.apksig.ApkVerifier;
import com.android.apksig.apk.ApkFormatException;
import com.android.apksig.apk.ApkUtils;
import com.android.apksig.internal.apk.ApkSigningBlockUtils;
import com.android.apksig.internal.apk.ContentDigestAlgorithm;
import com.android.apksig.internal.apk.SignatureAlgorithm;
import com.android.apksig.internal.apk.SignatureInfo;
import com.android.apksig.internal.util.GuaranteedEncodedFormX509Certificate;
import com.android.apksig.internal.util.Pair;
import com.android.apksig.internal.util.X509CertificateUtils;
import com.android.apksig.util.DataSource;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Source Stamp verifier.
 *
 * <p>SourceStamp improves traceability of apps with respect to unauthorized distribution.
 *
 * <p>The stamp is part of the APK that is protected by the signing block.
 *
 * <p>The APK contents hash is signed using the stamp key, and is saved as part of the signing
 * block.
 */
public abstract class SourceStampVerifier {

    /** Hidden constructor to prevent instantiation. */
    private SourceStampVerifier() {}

    /**
     * Verifies the provided APK's SourceStamp signatures and returns the result of verification.
     * The APK must be considered verified only if {@link ApkSigningBlockUtils.Result#verified} is
     * {@code true}. If verification fails, the result will contain errors -- see {@link
     * ApkSigningBlockUtils.Result#getErrors()}.
     *
     * @throws NoSuchAlgorithmException if the APK's signatures cannot be verified because a
     *     required cryptographic algorithm implementation is missing
     * @throws ApkSigningBlockUtils.SignatureNotFoundException if no SourceStamp signatures are
     *     found
     * @throws IOException if an I/O error occurs when reading the APK
     */
    public static ApkSigningBlockUtils.Result verify(
            DataSource apk,
            ApkUtils.ZipSections zipSections,
            byte[] sourceStampCertificateDigest,
            Map<ContentDigestAlgorithm, byte[]> apkContentDigests,
            int minSdkVersion,
            int maxSdkVersion)
            throws IOException, NoSuchAlgorithmException,
                    ApkSigningBlockUtils.SignatureNotFoundException {
        ApkSigningBlockUtils.Result result =
                new ApkSigningBlockUtils.Result(ApkSigningBlockUtils.VERSION_SOURCE_STAMP);
        SignatureInfo signatureInfo =
                ApkSigningBlockUtils.findSignature(apk, zipSections, SOURCE_STAMP_BLOCK_ID, result);

        verify(
                signatureInfo.signatureBlock,
                sourceStampCertificateDigest,
                apkContentDigests,
                minSdkVersion,
                maxSdkVersion,
                result);
        return result;
    }

    /**
     * Verifies the provided APK's SourceStamp signatures and outputs the results into the provided
     * {@code result}. APK is considered verified only if there are no errors reported in the {@code
     * result}. See {@link #verify(DataSource, ApkUtils.ZipSections, byte[], Map, int, int)} for
     * more information about the contract of this method.
     */
    private static void verify(
            ByteBuffer sourceStampBlock,
            byte[] sourceStampCertificateDigest,
            Map<ContentDigestAlgorithm, byte[]> apkContentDigests,
            int minSdkVersion,
            int maxSdkVersion,
            ApkSigningBlockUtils.Result result)
            throws NoSuchAlgorithmException {
        ApkSigningBlockUtils.Result.SignerInfo signerInfo =
                new ApkSigningBlockUtils.Result.SignerInfo();
        result.signers.add(signerInfo);
        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            ByteBuffer sourceStampBlockData =
                    ApkSigningBlockUtils.getLengthPrefixedSlice(sourceStampBlock);
            parseSourceStamp(
                    sourceStampBlockData,
                    certFactory,
                    signerInfo,
                    apkContentDigests,
                    sourceStampCertificateDigest,
                    minSdkVersion,
                    maxSdkVersion);
            result.verified = !result.containsErrors() && !result.containsWarnings();
        } catch (CertificateException e) {
            throw new IllegalStateException("Failed to obtain X.509 CertificateFactory", e);
        } catch (ApkFormatException | BufferUnderflowException e) {
            signerInfo.addWarning(ApkVerifier.Issue.SOURCE_STAMP_MALFORMED_SIGNATURE);
        }
    }

    /**
     * Parses the SourceStamp block and populates the {@code result}.
     *
     * <p>This verifies signatures over digests contained in the APK signing block.
     *
     * <p>This method adds one or more errors to the {@code result} if a verification error is
     * expected to be encountered on an Android platform version in the {@code [minSdkVersion,
     * maxSdkVersion]} range.
     */
    private static void parseSourceStamp(
            ByteBuffer sourceStampBlockData,
            CertificateFactory certFactory,
            ApkSigningBlockUtils.Result.SignerInfo result,
            Map<ContentDigestAlgorithm, byte[]> apkContentDigests,
            byte[] sourceStampCertificateDigest,
            int minSdkVersion,
            int maxSdkVersion)
            throws ApkFormatException, NoSuchAlgorithmException {
        List<Pair<Integer, byte[]>> digests = new ArrayList<>();
        for (Map.Entry<ContentDigestAlgorithm, byte[]> apkContentDigest :
                apkContentDigests.entrySet()) {
            digests.add(Pair.of(apkContentDigest.getKey().getId(), apkContentDigest.getValue()));
        }
        Collections.sort(digests, Comparator.comparing(Pair::getFirst));
        byte[] digestBytes =
                encodeAsSequenceOfLengthPrefixedPairsOfIntAndLengthPrefixedBytes(digests);

        // Parse the SourceStamp certificate.
        byte[] sourceStampEncodedCertificate =
                ApkSigningBlockUtils.readLengthPrefixedByteArray(sourceStampBlockData);
        X509Certificate sourceStampCertificate;
        try {
            sourceStampCertificate =
                    X509CertificateUtils.generateCertificate(
                            sourceStampEncodedCertificate, certFactory);
        } catch (CertificateException e) {
            result.addWarning(ApkVerifier.Issue.SOURCE_STAMP_MALFORMED_CERTIFICATE, e);
            return;
        }
        // Wrap the cert so that the result's getEncoded returns exactly the original encoded
        // form. Without this, getEncoded may return a different form from what was stored in
        // the signature. This is because some X509Certificate(Factory) implementations
        // re-encode certificates.
        sourceStampCertificate =
                new GuaranteedEncodedFormX509Certificate(
                        sourceStampCertificate, sourceStampEncodedCertificate);
        result.certs.add(sourceStampCertificate);

        // Verify the SourceStamp certificate found in the signing block is the same as the
        // SourceStamp certificate found in the APK.
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        messageDigest.update(sourceStampEncodedCertificate);
        byte[] sourceStampBlockCertificateDigest = messageDigest.digest();
        if (!Arrays.equals(sourceStampCertificateDigest, sourceStampBlockCertificateDigest)) {
            result.addWarning(
                    ApkVerifier.Issue
                            .SOURCE_STAMP_CERTIFICATE_MISMATCH_BETWEEN_SIGNATURE_BLOCK_AND_APK,
                    ApkSigningBlockUtils.toHex(sourceStampBlockCertificateDigest),
                    ApkSigningBlockUtils.toHex(sourceStampCertificateDigest));
            return;
        }

        // Parse the signatures block and identify supported signatures
        ByteBuffer signatures = ApkSigningBlockUtils.getLengthPrefixedSlice(sourceStampBlockData);
        int signatureCount = 0;
        List<ApkSigningBlockUtils.SupportedSignature> supportedSignatures = new ArrayList<>(1);
        while (signatures.hasRemaining()) {
            signatureCount++;
            try {
                ByteBuffer signature = ApkSigningBlockUtils.getLengthPrefixedSlice(signatures);
                int sigAlgorithmId = signature.getInt();
                byte[] sigBytes = ApkSigningBlockUtils.readLengthPrefixedByteArray(signature);
                result.signatures.add(
                        new ApkSigningBlockUtils.Result.SignerInfo.Signature(
                                sigAlgorithmId, sigBytes));
                SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.findById(sigAlgorithmId);
                if (signatureAlgorithm == null) {
                    result.addWarning(
                            ApkVerifier.Issue.SOURCE_STAMP_UNKNOWN_SIG_ALGORITHM, sigAlgorithmId);
                    continue;
                }
                supportedSignatures.add(
                        new ApkSigningBlockUtils.SupportedSignature(signatureAlgorithm, sigBytes));
            } catch (ApkFormatException | BufferUnderflowException e) {
                result.addWarning(ApkVerifier.Issue.SOURCE_STAMP_MALFORMED_SIGNATURE, signatureCount);
                return;
            }
        }
        if (result.signatures.isEmpty()) {
            result.addWarning(ApkVerifier.Issue.SOURCE_STAMP_NO_SIGNATURE);
            return;
        }

        // Verify signatures over digests using the SourceStamp's certificate.
        List<ApkSigningBlockUtils.SupportedSignature> signaturesToVerify;
        try {
            signaturesToVerify =
                    ApkSigningBlockUtils.getSignaturesToVerify(
                            supportedSignatures, minSdkVersion, maxSdkVersion);
        } catch (ApkSigningBlockUtils.NoSupportedSignaturesException e) {
            result.addWarning(ApkVerifier.Issue.SOURCE_STAMP_NO_SUPPORTED_SIGNATURE);
            return;
        }
        for (ApkSigningBlockUtils.SupportedSignature signature : signaturesToVerify) {
            SignatureAlgorithm signatureAlgorithm = signature.algorithm;
            String jcaSignatureAlgorithm =
                    signatureAlgorithm.getJcaSignatureAlgorithmAndParams().getFirst();
            AlgorithmParameterSpec jcaSignatureAlgorithmParams =
                    signatureAlgorithm.getJcaSignatureAlgorithmAndParams().getSecond();
            PublicKey publicKey = sourceStampCertificate.getPublicKey();
            try {
                Signature sig = Signature.getInstance(jcaSignatureAlgorithm);
                sig.initVerify(publicKey);
                if (jcaSignatureAlgorithmParams != null) {
                    sig.setParameter(jcaSignatureAlgorithmParams);
                }
                sig.update(digestBytes);
                byte[] sigBytes = signature.signature;
                if (!sig.verify(sigBytes)) {
                    result.addWarning(
                            ApkVerifier.Issue.SOURCE_STAMP_DID_NOT_VERIFY, signatureAlgorithm);
                    return;
                }
                result.verifiedSignatures.put(signatureAlgorithm, sigBytes);
            } catch (InvalidKeyException
                    | InvalidAlgorithmParameterException
                    | SignatureException e) {
                result.addWarning(
                        ApkVerifier.Issue.SOURCE_STAMP_VERIFY_EXCEPTION, signatureAlgorithm, e);
                return;
            }
        }
    }
}

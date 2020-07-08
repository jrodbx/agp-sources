/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.ide.common.signing;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.prefs.AndroidLocation;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.utils.ILogger;
import com.android.utils.Pair;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import com.google.common.io.Closeables;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v1CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * A Helper to create and read keystore/keys.
 */
public final class KeystoreHelper {

    /**
     * Certificate CN value. This is a hard-coded value for the debug key.
     * Android Market checks against this value in order to refuse applications signed with
     * debug keys.
     */
    private static final String CERTIFICATE_DESC = "CN=Android Debug,O=Android,C=US";

    /**
     * Generated certificate validity.
     */
    private static final int DEFAULT_VALIDITY_YEARS = 30;


    /**
     * Returns the location of the default debug keystore.
     *
     * @return The location of the default debug keystore
     * @throws AndroidLocationException if the location cannot be computed
     */
    @NonNull
    public static String defaultDebugKeystoreLocation() throws AndroidLocationException {
        //this is guaranteed to either return a non null value (terminated with a platform
        // specific separator), or throw.
        String folder = AndroidLocation.getFolder();
        return folder + "debug.keystore";
    }

    /**
     * Creates a new debug store with the location, keyalias, and passwords specified in the
     * config.
     *
     * @param storeType an optional type of keystore; if {@code null} the default
     * @param storeFile the file where the store should be created
     * @param storePassword a password for the key store
     * @param keyPassword a password for the key
     * @param keyAlias the alias under which the key is stored in the store
     * @param logger (not used, kept for backwards compatibility)
     * @throws KeytoolException
     */
    public static boolean createDebugStore(@Nullable String storeType, @NonNull File storeFile,
            @NonNull String storePassword, @NonNull String keyPassword, @NonNull String keyAlias,
            @NonNull ILogger logger) throws KeytoolException {

        return createNewStore(storeType, storeFile, storePassword, keyPassword, keyAlias,
                CERTIFICATE_DESC, DEFAULT_VALIDITY_YEARS);
    }

    /**
     * Creates a new store with a self-signed certificate. The certificate will be valid starting
     * from the current date up to the number of years provided.
     *
     * @param storeType an optional type of keystore; if {@code null} the default
     * @param storeFile the file where the store should be created
     * @param storePassword a password for the key store
     * @param keyPassword a password for the key
     * @param keyAlias the alias under which the key is stored in the store
     * @param dn the distinguished name of the owner and issuer of the certificate
     * @param validityYears number of years the certificate should be valid
     * @throws KeytoolException failed to generate the self-signed certificate or the store
     */
    public static boolean createNewStore(
            @Nullable String storeType,
            @NonNull File storeFile,
            @NonNull String storePassword,
            @NonNull String keyPassword,
            @NonNull String keyAlias,
            @NonNull String dn,
            int validityYears)
            throws KeytoolException {
        Preconditions.checkArgument(validityYears > 0, "validityYears (%s) <= 0", validityYears);

        String useStoreType = storeType;
        if (useStoreType == null) {
            useStoreType = KeyStore.getDefaultType();
            Verify.verifyNotNull(useStoreType);
        }

        try {
            KeyStore ks = KeyStore.getInstance(useStoreType);
            ks.load(null, null);

            Pair<PrivateKey, X509Certificate> generated = generateKeyAndCertificate("RSA",
                    "SHA1withRSA", validityYears, dn);
            ks.setKeyEntry(keyAlias, generated.getFirst(), keyPassword.toCharArray(),
                    new Certificate[]{generated.getSecond()});
            FileOutputStream fos = new FileOutputStream(storeFile);
            boolean threw = true;
            try {
                ks.store(fos, storePassword.toCharArray());
                threw = false;
            } finally {
                Closeables.close(fos, threw);
            }
        } catch (KeytoolException e) {
            throw e;
        } catch (Exception e) {
            throw new KeytoolException("Failed to create keystore.", e);
        }

        return true;
    }

    /**
     * Returns the CertificateInfo for the given signing configuration.
     *
     * @param storeType an optional type of keystore; if {@code null} the default
     * @param storeFile the file where the store should be created
     * @param storePassword a password for the key store
     * @param keyPassword a password for the key
     * @param keyAlias the alias under which the key is stored in the store
     * @return the certificate info if it could be loaded
     * @throws KeytoolException If the password is wrong
     * @throws FileNotFoundException If the store file cannot be found
     */
    @NonNull
    public static CertificateInfo getCertificateInfo(
            @Nullable String storeType,
            @NonNull File storeFile,
            @NonNull String storePassword,
            @NonNull String keyPassword,
            @NonNull String keyAlias)
            throws KeytoolException, FileNotFoundException {

        try {
            KeyStore keyStore = KeyStore.getInstance(
                    storeType != null ? storeType : KeyStore.getDefaultType());

            FileInputStream fis = new FileInputStream(storeFile);
            keyStore.load(fis, storePassword.toCharArray());
            fis.close();

            char[] keyPasswordArray = keyPassword.toCharArray();
            PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry)keyStore.getEntry(
                    keyAlias, new KeyStore.PasswordProtection(keyPasswordArray));

            if (entry == null) {
                throw new KeytoolException(
                        String.format(
                                "No key with alias '%1$s' found in keystore %2$s",
                                keyAlias,
                                storeFile.getAbsolutePath()));
            }

            return new CertificateInfo(
                    entry.getPrivateKey(),
                    (X509Certificate) entry.getCertificate());
        } catch (FileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new KeytoolException(
                    String.format("Failed to read key %1$s from store \"%2$s\": %3$s",
                            keyAlias, storeFile, e.getMessage()),
                    e);
        }
    }

    /**
     * Generates a key and self-signed certificate pair.
     * @param asymmetric the asymmetric encryption algorithm (<em>e.g.,</em> {@code RSA})
     * @param sign the signature algorithm (<em>e.g.,</em> {@code SHA1withRSA})
     * @param validityYears number of years the certificate should be valid, must be greater than
     * zero
     * @param dn the distinguished name of the issuer and owner of the certificate
     * @return a pair with the private key and the corresponding certificate
     * @throws KeytoolException failed to generate the pair
     */
    private static Pair<PrivateKey, X509Certificate> generateKeyAndCertificate(
            @NonNull String asymmetric, @NonNull String sign, int validityYears,
            @NonNull String dn) throws KeytoolException {
        Preconditions.checkArgument(validityYears > 0, "validityYears <= 0");

        KeyPair keyPair;
        try {
            keyPair = KeyPairGenerator.getInstance(asymmetric).generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new KeytoolException("Failed to generate key and certificate pair for "
                    + "algorithm '" + asymmetric + "'.", e);
        }

        Date notBefore = new Date(System.currentTimeMillis());
        Date notAfter = new Date(System.currentTimeMillis() + validityYears * 365L * 24 * 60 * 60
                * 1000);

        X500Name issuer = new X500Name(new X500Principal(dn).getName());

        SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfo.getInstance(
                keyPair.getPublic().getEncoded());
        X509v1CertificateBuilder builder = new X509v1CertificateBuilder(issuer, BigInteger.ONE,
                notBefore, notAfter, issuer, publicKeyInfo);

        ContentSigner signer;
        try {
            signer = new JcaContentSignerBuilder(sign).setProvider(
                    new BouncyCastleProvider()).build(keyPair.getPrivate());
        } catch (OperatorCreationException e) {
            throw new KeytoolException("Failed to build content signer with signature algorithm '"
                    + sign + "'.", e);
        }

        X509CertificateHolder holder = builder.build(signer);

        JcaX509CertificateConverter converter = new JcaX509CertificateConverter()
                .setProvider(new BouncyCastleProvider());

        X509Certificate certificate;
        try {
            certificate = converter.getCertificate(holder);
        } catch (CertificateException e) {
            throw new KeytoolException("Failed to obtain the self-signed certificate.", e);
        }

        return Pair.of(keyPair.getPrivate(), certificate);
    }
}

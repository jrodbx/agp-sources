/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal;

import com.android.Version;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.jar.Manifest;

public final class NonFinalPluginExpiry {

    /**
     * default retirement age in days since its inception date for RC or beta versions.
     */
    @VisibleForTesting static final Period DEFAULT_RETIREMENT_AGE_FOR_NON_RELEASE =
            Period.ofDays(40);

    private NonFinalPluginExpiry() {
    }

    /**
     * Verify that this plugin execution is within its public time range.
     *
     * @throws RuntimeException if the plugin is a non final plugin older than 40 days.
     */
    public static void verifyRetirementAge() {
        // disable the time bomb for now.
        if (true) return;

        URLClassLoader cl = (URLClassLoader) NonFinalPluginExpiry.class.getClassLoader();
        try (InputStream inputStream = cl.findResource("META-INF/MANIFEST.MF").openStream()) {
            verifyRetirementAge(
                    LocalDate.now(),
                    new Manifest(inputStream),
                    System.getenv("ANDROID_DAILY_OVERRIDE"));
        } catch (IOException ignore) {}
    }

    @VisibleForTesting
    static void verifyRetirementAge(
            @NonNull LocalDate now,
            @NonNull Manifest manifest,
            @Nullable String dailyOverride) {

        String version = manifest.getMainAttributes().getValue("Plugin-Version");
        Period retirementAge = getRetirementAge(version);
        // if this plugin version will never be outdated, return.
        if (retirementAge == null) {
            return;
        }

        String inceptionDateAttr = manifest.getMainAttributes().getValue("Inception-Date");
        // when running in unit tests, etc... the manifest entries are absent.
        if (inceptionDateAttr == null) {
            return;
        }
        LocalDate inceptionDate =
                LocalDate.parse(inceptionDateAttr, DateTimeFormatter.ISO_LOCAL_DATE);

        LocalDate expiryDate = inceptionDate.plus(retirementAge);

        if (now.compareTo(expiryDate) > 0) {
            // this plugin is too old.
            final MessageDigest crypt;
            try {
                crypt = MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException e) {
                return;
            }
            crypt.reset();
            // encode the day, not the current time.
            try {
                crypt.update(
                        String.format(
                                "%1$s:%2$s:%3$s",
                                now.getYear(),
                                now.getMonthValue() -1,
                                now.getDayOfMonth())
                                .getBytes("utf8"));
            } catch (UnsupportedEncodingException e) {
                return;
            }
            String overrideValue = new BigInteger(1, crypt.digest()).toString(16);
            if (dailyOverride == null) {
                String message =
                        String.format(
                                "The android gradle plugin version %1$s is too old, "
                                        + "please update to the latest version.\n"
                                        + "\n"
                                        + "To override this check from the command line please "
                                        + "set the ANDROID_DAILY_OVERRIDE environment variable to "
                                        + "\"%2$s\"",
                                version,
                                overrideValue);
                System.err.println(message);
                throw new AndroidGradlePluginTooOldException(message);
            } else {
                // allow a version specific override.
                String versionOverride =
                        String.valueOf(Version.ANDROID_GRADLE_PLUGIN_VERSION.hashCode());
                if (dailyOverride.equals(overrideValue) || dailyOverride.equals(versionOverride)) {
                    return;
                }
                String message =
                        String.format(
                                "The android gradle plugin version %1$s is too old,"
                                        + "please update to the latest version.\n"
                                        + "\n"
                                        + "The ANDROID_DAILY_OVERRIDE value is outdated. "
                                        + "Please set the ANDROID_DAILY_OVERRIDE environment "
                                        + "variable to \"%2$s\"",
                                version, overrideValue);
                System.err.println(message);
                throw new AndroidGradlePluginTooOldException(message);
            }
        }
    }

    /**
     * Returns the retirement age for this plugin depending on its version string, or null if this
     * plugin version will never become obsolete
     *
     * @param version the plugin full version, like 1.3.4-preview5 or 1.0.2 or 1.2.3-beta4
     */
    @Nullable
    private static Period getRetirementAge(@Nullable String version) {
        if (version == null
                || version.contains("rc")
                || version.contains("beta")
                || version.contains("alpha")
                || version.contains("preview")) {
            return DEFAULT_RETIREMENT_AGE_FOR_NON_RELEASE;
        }
        return null;
    }

    public static final class AndroidGradlePluginTooOldException extends RuntimeException {
        public AndroidGradlePluginTooOldException(@NonNull String message) {
            super(message);
        }
    }
}

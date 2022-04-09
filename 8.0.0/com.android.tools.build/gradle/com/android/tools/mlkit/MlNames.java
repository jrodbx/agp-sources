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
package com.android.tools.mlkit;

import com.android.annotations.NonNull;
import com.android.utils.StringHelper;
import com.google.common.base.CaseFormat;
import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.io.MoreFiles;
import com.google.common.primitives.UnsignedBytes;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.lang.model.SourceVersion;

/** Store names that used by both light class and gradle task. */
public class MlNames {
    public static final String OUTPUTS = "Outputs";
    public static final String PACKAGE_SUFFIX = ".ml";

    private static final String MODEL_NAME_PREFIX = "AutoModel";

    /** Format getter method to getPropertyNameAsType(i.e. getImage1AsTensorImage()). */
    public static String formatGetterName(String propertyName, String type) {
        return "get"
                + StringHelper.usLocaleCapitalize(propertyName)
                + "As"
                + StringHelper.usLocaleCapitalize(type);
    }

    public static String formatGroupGetterName(String propertyName) {
        return "get" + StringHelper.usLocaleCapitalize(propertyName) + "List";
    }

    @NonNull
    public static String computeModelClassName(@NonNull String relativeModelFilePath) {
        Path filePath = Paths.get(relativeModelFilePath);

        String formattedName =
                MoreFiles.getNameWithoutExtension(filePath).trim().replaceAll("[- ]+", "_");
        if (formattedName.contains("_")) {
            formattedName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, formattedName);
        } else {
            formattedName = StringHelper.usLocaleCapitalize(formattedName);
        }
        CharMatcher classNameMatcher =
                CharMatcher.inRange('0', '9')
                        .or(CharMatcher.inRange('A', 'Z'))
                        .or(CharMatcher.inRange('a', 'z'));
        String className = classNameMatcher.retainFrom(formattedName);

        if (className.isEmpty()) {
            // If we can't interpret a valid class name from file name, then create name from
            // fileName hashcode(i.e. AutoModel75)
            return MODEL_NAME_PREFIX + getHashValue(relativeModelFilePath);
        }

        // If model is in sub directory, add a hashcode in model class name.
        if (filePath.getParent() != null) {
            className += getHashValue(relativeModelFilePath);
        }

        if (SourceVersion.isIdentifier(className) && !SourceVersion.isKeyword(className)) {
            return className;
        } else {
            return MODEL_NAME_PREFIX + className;
        }
    }

    @NonNull
    public static String computeIdentifierName(@NonNull String name, @NonNull String defaultName) {
        if (name.isEmpty()) {
            return defaultName;
        }

        // Handle "-" and "_" inside to make name lowerCamel.
        String formattedName = name.replaceAll("[- ]+", "_");
        if (formattedName.contains("_")) {
            formattedName =
                    CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, formattedName.trim());
        }

        // Remove special characters.
        CharMatcher classNameMatcher =
                CharMatcher.inRange('0', '9')
                        .or(CharMatcher.inRange('A', 'Z'))
                        .or(CharMatcher.inRange('a', 'z'));
        String matchedName = classNameMatcher.retainFrom(formattedName);

        if (SourceVersion.isIdentifier(matchedName) && !SourceVersion.isKeyword(matchedName)) {
            return matchedName;
        } else {
            return defaultName;
        }
    }

    @NonNull
    private static String getHashValue(@NonNull String name) {
        return UnsignedBytes.toString(
                Hashing.murmur3_32().hashString(name, Charsets.UTF_8).asBytes()[0]);
    }

    @NonNull
    public static String computeIdentifierName(@NonNull String name) {
        String defaultName = "name" + getHashValue(name);
        return computeIdentifierName(name, defaultName);
    }
}

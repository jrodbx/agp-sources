/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.ide.common.resources;

import static com.android.SdkConstants.DOT_9PNG;
import static com.android.SdkConstants.DOT_OTF;
import static com.android.SdkConstants.DOT_TTC;
import static com.android.SdkConstants.DOT_TTF;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.DOT_XSD;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.utils.SdkUtils;
import java.io.File;
import java.util.List;
import javax.lang.model.SourceVersion;

public final class FileResourceNameValidator {

    private FileResourceNameValidator() {
    }

    /**
     * Validate a single-file resource name.
     *
     * @param file the file resource to validate.
     * @param resourceType the resource folder type.
     * @throws MergingException is the resource name is not valid.
     */
    public static void validate(@NonNull File file, @NonNull ResourceFolderType resourceType)
            throws MergingException {
        String error = getErrorTextForFileResource(file.getName(), resourceType);
        if (error != null) {
            throw MergingException.withMessage(error).withFile(file).build();
        }
    }

    /**
     * Validate a single-file resource name.
     *
     * @param fileNameWithExt the resource file name to validate.
     * @param resourceFolderType the resource folder type.
     * @return null if no error, otherwise a string describing the error.
     */
    @Nullable
    public static String getErrorTextForFileResource(
            @NonNull final String fileNameWithExt,
            @NonNull final ResourceFolderType resourceFolderType) {
        if (fileNameWithExt.trim().isEmpty()) {
            return "Resource must have a name";
        }

        final String fileName;

        if (resourceFolderType == ResourceFolderType.RAW) {
            // Allow any single file extension.
            fileName = removeSingleExtension(fileNameWithExt);
        } else if (resourceFolderType == ResourceFolderType.DRAWABLE
                || resourceFolderType == ResourceFolderType.MIPMAP) {
            // Require either an image or xml file extension
            if (SdkUtils.endsWithIgnoreCase(fileNameWithExt, DOT_XML)) {
                fileName = fileNameWithExt
                        .substring(0, fileNameWithExt.length() - DOT_XML.length());
            } else if (SdkUtils.hasImageExtension(fileNameWithExt)) {
                if (SdkUtils.endsWithIgnoreCase(fileNameWithExt, DOT_9PNG)) {
                    fileName = fileNameWithExt
                            .substring(0, fileNameWithExt.length() - DOT_9PNG.length());
                } else {
                    fileName = fileNameWithExt.substring(0, fileNameWithExt.lastIndexOf('.'));
                }
            } else {
                return "The file name must end with .xml or .png";
            }
        } else if (resourceFolderType == ResourceFolderType.XML) {
            // Also allow xsd as they are xml files.
            if (SdkUtils.endsWithIgnoreCase(fileNameWithExt, DOT_XML) ||
                    SdkUtils.endsWithIgnoreCase(fileNameWithExt, DOT_XSD)) {
                fileName = removeSingleExtension(fileNameWithExt);
            } else {
                return "The file name must end with .xml";
            }
        } else if (resourceFolderType == ResourceFolderType.FONT) {
            if (SdkUtils.endsWithIgnoreCase(fileNameWithExt, DOT_XML) ||
                    SdkUtils.endsWithIgnoreCase(fileNameWithExt, DOT_TTF) ||
                    SdkUtils.endsWithIgnoreCase(fileNameWithExt, DOT_OTF) ||
                    SdkUtils.endsWithIgnoreCase(fileNameWithExt, DOT_TTC)) {
                fileName = removeSingleExtension(fileNameWithExt);
            } else {
                return "The file name must end with .xml, .ttf, .ttc or .otf";
            }
        } else {
            // Require xml extension
            if (SdkUtils.endsWithIgnoreCase(fileNameWithExt, DOT_XML)) {
                fileName = fileNameWithExt
                        .substring(0, fileNameWithExt.length() - DOT_XML.length());
            } else {
                return "The file name must end with .xml";
            }
        }

        return getErrorTextForNameWithoutExtension(fileName, resourceFolderType);
    }

    /**
     * Validate a single-file resource name.
     *
     * @param fileNameWithoutExt The resource file name to validate, without an extension.
     * @return null if no error, otherwise a string describing the error.
     */
    @Nullable
    public static String getErrorTextForNameWithoutExtension(
            @NonNull String fileNameWithoutExt, @NonNull ResourceFolderType resourceFolderType) {
        if (resourceFolderType == ResourceFolderType.VALUES) {
            // There don't seem to be any restrictions for this case, as there is no trace of this
            // string in aapt output.
            return null;
        }

        char first = fileNameWithoutExt.charAt(0);
        if (!Character.isJavaIdentifierStart(first)) {
            return "The resource name must start with a letter";
        }

        // AAPT only allows lowercase+digits+_:
        // "%s: Invalid file name: must contain only [a-z0-9_.]","
        for (int i = 0, n = fileNameWithoutExt.length(); i < n; i++) {
            char c = fileNameWithoutExt.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) {
                return String.format("'%1$c' is not a valid file-based resource name character: "
                        + "File-based resource names must contain only lowercase a-z, 0-9,"
                        + " or underscore", c);
            }
        }

        if (SourceVersion.isKeyword(fileNameWithoutExt)) {
            return String.format(
                    "%1$s is not a valid resource name (reserved Java keyword)",
                    fileNameWithoutExt);
        }

        // Success!
        return null;
    }

    private static String removeSingleExtension(String fileNameWithExt) {
        int lastDot = fileNameWithExt.lastIndexOf('.');
        if (lastDot != -1) {
            return fileNameWithExt.substring(0, lastDot);
        } else {
            return fileNameWithExt;
        }
    }

    private static boolean oneOfStartsWithIgnoreCase(List<String> strings, String prefix) {
        boolean matches = false;
        for (String allowedString : strings) {
            if (SdkUtils.startsWithIgnoreCase(allowedString, prefix)) {
                matches = true;
                break;
            }
        }
        return matches;
    }
}

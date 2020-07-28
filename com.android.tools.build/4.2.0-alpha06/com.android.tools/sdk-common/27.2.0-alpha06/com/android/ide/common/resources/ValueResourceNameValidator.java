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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import java.io.File;
import javax.lang.model.SourceVersion;

public final class ValueResourceNameValidator {

    private ValueResourceNameValidator() {
    }

    /**
     * Validate a value resource name.
     *
     * @param resourceName the resource name to validate.
     * @param resourceType the resource type.
     * @param file         the file the resource came from.
     * @throws MergingException is the resource name is not valid.
     */
    public static void validate(@NonNull String resourceName, @NonNull ResourceType resourceType,
            @Nullable File file)
            throws MergingException {
        String error = getErrorText(resourceName, resourceType);
        if (error != null) {
            // TODO find location in file.
            MergingException.Builder exception = MergingException.withMessage(error);
            if (file != null) {
                exception.withFile(file);
            }
            throw exception.build();
        }
    }

    /**
     * Validate a value resource name.
     *
     * @param fullResourceName the resource name to validate.
     * @param resourceType     the resource type.
     * @return null if no error, otherwise a string describing the error.
     */
    @Nullable
    public static String getErrorText(@NonNull String fullResourceName,
            @Nullable ResourceType resourceType) {

        if (resourceType == ResourceType.ATTR) {
            if (fullResourceName.startsWith("android:")) {
                fullResourceName = fullResourceName.substring(8);
            }
        }
        final String resourceName = normalizeName(fullResourceName);

        // Resource names must be valid Java identifiers, since they will
        // be represented as Java identifiers in the R file:
        if (!SourceVersion.isIdentifier(resourceName)) {
            if (resourceName.isEmpty()) {
                return "The resource name shouldn't be empty";
            } else if (!Character.isJavaIdentifierStart(resourceName.charAt(0))) {
                return "The resource name must start with a letter";
            } else {
                for (int i = 1, n = resourceName.length(); i < n; i++) {
                    char c = resourceName.charAt(i);
                    if (!Character.isJavaIdentifierPart(c)) {
                        return String
                                .format("'%1$c' is not a valid resource name character", c);
                    }
                }
            }
        }

        if (SourceVersion.isKeyword(resourceName)) {
            return String.format("%1$s is not a valid resource name (reserved Java keyword)",
                    resourceName);
        }

        // Success.
        return null;
    }

    @NonNull
    public static String normalizeName(@NonNull String fullResourceName) {
        return fullResourceName.replace('.', '_');
    }
}

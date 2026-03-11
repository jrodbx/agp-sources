/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.xml.sax;

import com.android.annotations.NonNull;
import java.util.Objects;
import java.util.Optional;
import org.xml.sax.Attributes;

public final class AttributeUtils {
    private AttributeUtils() {}

    @NonNull
    public static Optional<Boolean> getBoolean(
            @NonNull Attributes attributes, @NonNull String qualifiedName) {
        Object value = attributes.getValue(qualifiedName);

        if (Objects.equals(value, "true") || Objects.equals(value, "1")) {
            return Optional.of(true);
        }

        if (Objects.equals(value, "false") || Objects.equals(value, "0")) {
            return Optional.of(false);
        }

        return Optional.empty();
    }
}

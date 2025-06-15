/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.utils;

import com.android.annotations.NonNull;
import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.common.base.Enums;
import java.util.Arrays;
import java.util.stream.Collectors;

public class HelpfulEnumConverter<T extends Enum<T>> extends Converter<String, T> {
    private final Converter<String, T> delegate;
    private final Class<T> klass;

    public HelpfulEnumConverter(Class<T> klass) {
        this.klass = klass;
        this.delegate =
                CaseFormat.LOWER_UNDERSCORE
                        .converterTo(CaseFormat.UPPER_UNDERSCORE)
                        .andThen(Enums.stringConverter(klass));
    }

    @Override
    protected T doForward(@NonNull String value) {
        try {
            return delegate.convert(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format(
                            "Unknown %s value '%s'. Possible values are %s.",
                            klass.getSimpleName(),
                            value,
                            Arrays.stream(klass.getEnumConstants())
                                    .map(c -> "'" + c.name().toLowerCase() + "'")
                                    .collect(Collectors.joining(", "))));
        }
    }

    @Override
    protected String doBackward(@NonNull T anEnum) {
        return delegate.reverse().convert(anEnum);
    }
}

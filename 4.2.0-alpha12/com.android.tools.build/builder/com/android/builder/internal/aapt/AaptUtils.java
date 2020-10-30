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

package com.android.builder.internal.aapt;

import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.google.common.collect.Iterables;
import java.util.function.Predicate;

/**
 * Utilities used in the {@code aapt} package.
 */
public final class AaptUtils {

    /**
     * Predicate that evaluates whether a resource config is a density as per
     * {@link Density#getEnum(String)}.
     */
    private static final Predicate<String> IS_DENSITY = d -> Density.getEnum(d) != null;

    /**
     * Utility class: no constructor.
     */
    private AaptUtils() {
        /*
         * Never executed.
         */
    }

    /**
     * Obtains resource configs that are densities.
     *
     * @param configs the resource configs
     * @return resource configs that are recognized as densities as per
     * {@link Density#getEnum(String)}
     */
    public static Iterable<String> getDensityResConfigs(@NonNull Iterable<String> configs) {
        return Iterables.filter(configs, IS_DENSITY::test);
    }

    /**
     * Obtains resource configs that are not densities.
     *
     * @return resource configs that are not recognized as densities as per
     * {@link Density#getEnum(String)}
     */
    public static Iterable<String> getNonDensityResConfigs(@NonNull Iterable<String> configs) {
        return Iterables.filter(configs, IS_DENSITY.negate()::test);
    }
}

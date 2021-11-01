/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.builder.testing.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.util.List;
import java.util.Set;

/**
 * Provider of a test device characteristics.
 */
public interface DeviceConfigProvider {

    @NonNull
    String getConfigFor(String abi);

    int getDensity();

    @Nullable
    String getLanguage();

    @Nullable
    default Set<String> getLanguageSplits() {
        return null;
    }

    @Nullable
    String getRegion();

    @NonNull
    List<String> getAbis();

    @Nullable
    default String getApiCodeName() {
        return null;
    }

    default int getApiLevel() {
        return 1;
    }
}

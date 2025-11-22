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

package com.android.build.gradle.internal.cxx.json;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Subset of normal NativeBuildConfigValue that does not include potentially large structures like
 * files.
 *
 * <p>Note: This class is populated via {@link AndroidBuildGradleJsons::MiniConfigBuildingVisitor}.
 * If you add fields here you also need to update that class to read the fields.
 */
public class NativeBuildConfigValueMini {
    @NonNull public List<File> buildFiles = Lists.newArrayList();
    @NonNull public List<List<String>> cleanCommandsComponents = Lists.newArrayList();
    @Nullable public List<String> buildTargetsCommandComponents;
    @NonNull public Map<String, NativeLibraryValueMini> libraries = Maps.newHashMap();
}

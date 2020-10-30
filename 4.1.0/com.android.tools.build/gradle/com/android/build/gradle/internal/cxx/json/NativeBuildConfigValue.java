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

import com.android.annotations.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Value type to be used with Gson.
 */
public class NativeBuildConfigValue {
    @Nullable public Map<Integer, String> stringTable;
    @Nullable
    public Collection<File> buildFiles;
    @Nullable
    public List<String> cleanCommands;
    @Nullable public String buildTargetsCommand;
    @Nullable public Map<String, NativeLibraryValue> libraries;
    @Nullable
    public Map<String, NativeToolchainValue> toolchains;
    @Nullable
    public Collection<String> cFileExtensions;
    @Nullable
    public Collection<String> cppFileExtensions;
}

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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;

/** Implementation of CoreShaderOptions for usage in the non-model based Gradle plugin DSL. */
public class ShaderOptions implements CoreShaderOptions, com.android.build.api.dsl.Shaders {

    List<String> args = Lists.newArrayListWithExpectedSize(2);
    ListMultimap<String, String> scopedArgs = ArrayListMultimap.create();

    @Inject
    public ShaderOptions() {}

    @NonNull
    @Override
    public List<String> getGlslcArgs() {
        return args;
    }

    @NonNull
    @Override
    public ListMultimap<String, String> getScopedGlslcArgs() {
        return scopedArgs;
    }

    @Override
    public void glslcArgs(@NonNull String... options) {
        this.args.addAll(Arrays.asList(options));
    }

    @Override
    public void glslcScopedArgs(@NonNull String key, @NonNull String... options) {
        this.scopedArgs.putAll(key, Arrays.asList(options));
    }

    void _initWith(@NonNull CoreShaderOptions that) {
        args.clear();
        args.addAll(that.getGlslcArgs());

        scopedArgs.clear();
        scopedArgs.putAll(that.getScopedGlslcArgs());
    }
}

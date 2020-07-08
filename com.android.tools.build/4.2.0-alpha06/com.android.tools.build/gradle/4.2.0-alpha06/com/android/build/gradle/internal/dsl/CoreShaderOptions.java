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

import com.google.common.collect.ListMultimap;
import java.util.List;

/**
 * Options for configuring scoped shader options.
 */
public interface CoreShaderOptions {

    /**
     * Returns the list of glslc args.
     */
    List<String> getGlslcArgs();

    /**
     * Returns the list of scoped glsl args.
     */
    ListMultimap<String, String> getScopedGlslcArgs();
}

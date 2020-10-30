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

package com.android.build.gradle.api;

import org.gradle.api.artifacts.component.ComponentIdentifier;

/**
 * Component Identifier for a tested artifact.
 *
 * <p>This can be used with {@link BaseVariant#getCompileClasspathArtifacts(Object)} to disambiguate
 * the dependencies vs the tested artifact(s).
 */
public interface TestedComponentIdentifier extends ComponentIdentifier {

    /**
     * returns the name of the tested variant.
     *
     * @return the name of the tested variant.
     */
    String getVariantName();
}

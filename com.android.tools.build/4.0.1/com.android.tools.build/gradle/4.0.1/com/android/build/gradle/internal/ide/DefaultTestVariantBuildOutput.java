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

package com.android.build.gradle.internal.ide;

import com.android.annotations.NonNull;
import com.android.builder.model.TestVariantBuildOutput;
import java.util.Collection;

/** Default implementation of the {@link TestVariantBuildOutput}. */
public class DefaultTestVariantBuildOutput extends DefaultBaseBuildOutput
        implements TestVariantBuildOutput {

    private final String testedVariantName;
    private final TestType testType;

    public DefaultTestVariantBuildOutput(
            @NonNull String name,
            @NonNull Collection<EarlySyncBuildOutput> buildOutputSupplier,
            @NonNull String testedVariantName,
            @NonNull TestType testType) {
        super(name, buildOutputSupplier);
        this.testedVariantName = testedVariantName;
        this.testType = testType;
    }

    @NonNull
    @Override
    public String getTestedVariantName() {
        return testedVariantName;
    }

    @NonNull
    @Override
    public String getType() {
        return testType.name();
    }
}

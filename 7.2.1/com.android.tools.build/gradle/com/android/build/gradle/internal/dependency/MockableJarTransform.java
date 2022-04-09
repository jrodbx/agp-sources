/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency;

import com.android.builder.testing.MockableJarGenerator;
import java.io.File;
import java.io.IOException;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/** Transform that converts an Android JAR file into a Mockable Android JAR file. */
@CacheableTransform
public abstract class MockableJarTransform
        implements TransformAction<MockableJarTransform.Parameters> {

    public interface Parameters extends GenericTransformParameters {
        @Input
        Property<Boolean> getReturnDefaultValues();
    }

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(TransformOutputs transformOutputs) {
        File input = getInputArtifact().get().getAsFile();
        File outputFile = transformOutputs.file(input.getName());
        Logging.getLogger(MockableJarTransform.class).info(
                "Calling mockable JAR artifact transform to create file: "
                        + outputFile.getAbsolutePath()
                        + " with input "
                        + input.getAbsolutePath());

        MockableJarGenerator generator =
                new MockableJarGenerator(getParameters().getReturnDefaultValues().get());
        try {
            generator.createMockableJar(input, outputFile);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create mockable android.jar", e);
        }
    }
}

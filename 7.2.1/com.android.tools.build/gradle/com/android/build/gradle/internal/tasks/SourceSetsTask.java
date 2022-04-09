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

package com.android.build.gradle.internal.tasks;

import com.android.annotations.NonNull;
import com.android.build.gradle.api.AndroidSourceDirectorySet;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.api.DefaultAndroidSourceDirectorySet;
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.builder.core.VariantType;
import java.io.IOException;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.tasks.diagnostics.ProjectBasedReportTask;
import org.gradle.api.tasks.diagnostics.internal.ReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.work.DisableCachingByDefault;

/** Prints out the DSL names and directory names of available source sets. */
@DisableCachingByDefault
public class SourceSetsTask extends ProjectBasedReportTask {

    private final TextReportRenderer mRenderer = new TextReportRenderer();

    private Iterable<AndroidSourceSet> sourceSetContainer;

    @Override
    protected ReportRenderer getRenderer() {
        return mRenderer;
    }

    @Override
    protected void generate(Project project) throws IOException {
        for (AndroidSourceSet sourceSet : sourceSetContainer) {
            mRenderer.getBuilder().subheading(sourceSet.getName());

            renderKeyValue("Compile configuration: ", sourceSet.getCompileConfigurationName());
            renderKeyValue("build.gradle name: ", "android.sourceSets." + sourceSet.getName());

            renderDirectorySet("Java sources", sourceSet.getJava(), project);
            renderDirectorySet(
                    "Kotlin sources", (AndroidSourceDirectorySet) sourceSet.getKotlin(), project);

            if (!sourceSet.getName().startsWith(VariantType.UNIT_TEST_PREFIX)) {
                renderKeyValue(
                        "Manifest file: ",
                        project.getRootProject()
                                .relativePath(sourceSet.getManifest().getSrcFile()));

                renderDirectorySet("Android resources", sourceSet.getRes(), project);
                renderDirectorySet("Assets", sourceSet.getAssets(), project);
                renderDirectorySet("AIDL sources", sourceSet.getAidl(), project);
                renderDirectorySet("RenderScript sources", sourceSet.getRenderscript(), project);
                renderDirectorySet("JNI sources", sourceSet.getJni(), project);
                renderDirectorySet("JNI libraries", sourceSet.getJniLibs(), project);
                if (sourceSet instanceof DefaultAndroidSourceSet) {
                    DefaultAndroidSourceSet androidSourceSet = (DefaultAndroidSourceSet) sourceSet;
                    if (!androidSourceSet.getExtras$gradle_core().isEmpty()) {
                        androidSourceSet
                                .getExtras$gradle_core()
                                .forEach(
                                        androidSourceDirectorySet ->
                                                renderDirectorySet(
                                                        "Custom sources",
                                                        androidSourceDirectorySet,
                                                        project));
                    }
                }
            }

            renderDirectorySet("Java-style resources", sourceSet.getResources(), project);

            mRenderer.getTextOutput().println();
        }

        mRenderer.complete();
    }

    private void renderDirectorySet(
            String name, DefaultAndroidSourceDirectorySet sourceDirectorySet, Project project) {
        String relativePaths =
                sourceDirectorySet.getSrcDirs().stream()
                        .map(file -> project.getRootProject().relativePath(file))
                        .collect(Collectors.joining(", "));
        renderKeyValue(name + ": ", String.format("[%s]", relativePaths));
    }

    private void renderDirectorySet(
            String name, AndroidSourceDirectorySet sourceDirectorySet, Project project) {
        String relativePaths =
                sourceDirectorySet.getSrcDirs().stream()
                        .map(file -> project.getRootProject().relativePath(file))
                        .collect(Collectors.joining(", "));
        renderKeyValue(name + ": ", String.format("[%s]", relativePaths));
    }

    private void renderKeyValue(String o, String o1) {
        mRenderer.getTextOutput()
                .withStyle(StyledTextOutput.Style.Identifier)
                .text(o);

        mRenderer.getTextOutput()
                .withStyle(StyledTextOutput.Style.Info)
                .text(o1);

        mRenderer.getTextOutput().println();
    }


    public static class CreationAction extends TaskCreationAction<SourceSetsTask> {

        @NonNull private final Iterable<AndroidSourceSet> sourceSetContainer;

        public CreationAction(@NonNull Iterable<AndroidSourceSet> sourceSetContainer) {
            this.sourceSetContainer = sourceSetContainer;
        }

        @NonNull
        @Override
        public String getName() {
            return "sourceSets";
        }

        @NonNull
        @Override
        public Class<SourceSetsTask> getType() {
            return SourceSetsTask.class;
        }

        @Override
        public void configure(@NonNull SourceSetsTask sourceSetsTask) {
            sourceSetsTask.sourceSetContainer = sourceSetContainer;
            sourceSetsTask.setDescription(
                    "Prints out all the source sets defined in this project.");
            sourceSetsTask.setGroup(TaskManager.ANDROID_GROUP);
        }
    }
}

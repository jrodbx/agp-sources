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
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.api.AndroidSourceDirectorySet;
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.internal.TaskManager;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.builder.core.VariantType;
import java.io.IOException;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.diagnostics.AbstractReportTask;
import org.gradle.api.tasks.diagnostics.internal.ReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;

/**
 * Prints out the DSL names and directory names of available source sets.
 */
public class SourceSetsTask extends AbstractReportTask {

    private final TextReportRenderer mRenderer = new TextReportRenderer();

    private BaseExtension extension;

    @Override
    protected ReportRenderer getRenderer() {
        return mRenderer;
    }

    @Internal("Temporary to suppress Gradle warnings (bug 135900510), may need more investigation")
    public BaseExtension getExtension() {
        return extension;
    }

    public void setExtension(BaseExtension extension) {
        this.extension = extension;
    }

    @Override
    protected void generate(Project project) throws IOException {
        if (extension != null) {
            for (AndroidSourceSet sourceSet : extension.getSourceSets()) {
                mRenderer.getBuilder().subheading(sourceSet.getName());


                renderKeyValue("Compile configuration: ", sourceSet.getCompileConfigurationName());
                renderKeyValue("build.gradle name: ", "android.sourceSets." + sourceSet.getName());

                renderDirectorySet("Java sources", sourceSet.getJava(), project);

                if (!sourceSet.getName().startsWith(VariantType.UNIT_TEST_PREFIX)) {
                    renderKeyValue(
                            "Manifest file: ",
                            project.getRootProject().relativePath(
                                    sourceSet.getManifest().getSrcFile()));

                    renderDirectorySet("Android resources", sourceSet.getRes(), project);
                    renderDirectorySet("Assets", sourceSet.getAssets(), project);
                    renderDirectorySet("AIDL sources", sourceSet.getAidl(), project);
                    renderDirectorySet("RenderScript sources", sourceSet.getRenderscript(), project);
                    renderDirectorySet("JNI sources", sourceSet.getJni(), project);
                    renderDirectorySet("JNI libraries", sourceSet.getJniLibs(), project);
                }

                renderDirectorySet("Java-style resources", sourceSet.getResources(), project);

                mRenderer.getTextOutput().println();
            }
        }

        mRenderer.complete();
    }

    private void renderDirectorySet(String name, AndroidSourceDirectorySet java, Project project) {
        String relativePaths = java.getSrcDirs().stream()
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

        private final BaseExtension extension;

        public CreationAction(@NonNull BaseExtension extension) {
            this.extension = extension;
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
            sourceSetsTask.setExtension(extension);
            sourceSetsTask.setDescription(
                    "Prints out all the source sets defined in this project.");
            sourceSetsTask.setGroup(TaskManager.ANDROID_GROUP);
        }
    }
}

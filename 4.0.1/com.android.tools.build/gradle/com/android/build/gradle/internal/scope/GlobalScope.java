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

package com.android.build.gradle.internal.scope;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ARTIFACT_TYPE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.MOCKABLE_JAR_RETURN_DEFAULT_VALUES;
import static com.android.builder.core.BuilderConstants.FD_REPORTS;
import static com.android.builder.model.AndroidProject.FD_GENERATED;
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;
import static com.android.builder.model.AndroidProject.FD_OUTPUTS;
import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.SdkComponents;
import com.android.build.gradle.internal.api.dsl.DslScope;
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.model.OptionalCompilationStep;
import com.android.builder.utils.FileCache;
import com.android.ide.common.blame.MessageReceiver;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Set;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** A scope containing data for the Android plugin. */
public class GlobalScope implements TransformGlobalScope {

    @NonNull private final Project project;
    @NonNull private BaseExtension extension;
    @NonNull private final SdkComponents sdkComponents;
    @NonNull private final ToolingModelBuilderRegistry toolingRegistry;
    @NonNull private final Set<OptionalCompilationStep> optionalCompilationSteps;
    @NonNull private final ProjectOptions projectOptions;
    @Nullable private final FileCache buildCache;
    @NonNull private final MessageReceiver messageReceiver;
    @NonNull private final SoftwareComponentFactory componentFactory;

    @NonNull private final String createdBy;
    @NonNull private final DslScope dslScope;

    @NonNull private Configuration lintChecks;
    @NonNull private Configuration lintPublish;

    private Configuration androidJarConfig;

    @NonNull private final BuildArtifactsHolder globalArtifacts;

    @Nullable private ConfigurableFileCollection bootClasspath = null;

    public GlobalScope(
            @NonNull Project project,
            @NonNull String createdBy,
            @NonNull ProjectOptions projectOptions,
            @NonNull DslScope dslScope,
            @NonNull SdkComponents sdkComponents,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @Nullable FileCache buildCache,
            @NonNull MessageReceiver messageReceiver,
            @NonNull SoftwareComponentFactory componentFactory) {
        // Attention: remember that this code runs early in the build lifecycle, project may not
        // have been fully configured yet (e.g. buildDir can still change).
        this.project = checkNotNull(project);
        this.createdBy = createdBy;
        this.dslScope = checkNotNull(dslScope);
        this.sdkComponents = checkNotNull(sdkComponents);
        this.toolingRegistry = checkNotNull(toolingRegistry);
        this.optionalCompilationSteps = checkNotNull(projectOptions.getOptionalCompilationSteps());
        this.projectOptions = checkNotNull(projectOptions);
        this.buildCache = buildCache;
        this.messageReceiver = messageReceiver;
        this.componentFactory = componentFactory;
        this.globalArtifacts = new GlobalBuildArtifactsHolder(project, this::getBuildDir);

        // Create empty configurations before these have been set.
        this.lintChecks = project.getConfigurations().detachedConfiguration();
    }

    public void setExtension(@NonNull BaseExtension extension) {
        this.extension = checkNotNull(extension);
    }

    @NonNull
    public BuildFeatureValues getBuildFeatures() {
        return dslScope.getBuildFeatures();
    }

    @NonNull
    @Override
    public Project getProject() {
        return project;
    }

    @NonNull
    public String getCreatedBy() {
        return createdBy;
    }

    @NonNull
    public BaseExtension getExtension() {
        return extension;
    }

    @NonNull
    public String getProjectBaseName() {
        return (String) project.property("archivesBaseName");
    }

    @NonNull
    public SdkComponents getSdkComponents() {
        return sdkComponents;
    }

    @NonNull
    public ToolingModelBuilderRegistry getToolingRegistry() {
        return toolingRegistry;
    }

    @NonNull
    @Override
    public File getBuildDir() {
        return project.getBuildDir();
    }

    @NonNull
    public File getIntermediatesDir() {
        return new File(getBuildDir(), FD_INTERMEDIATES);
    }

    @NonNull
    public File getGeneratedDir() {
        return new File(getBuildDir(), FD_GENERATED);
    }

    @NonNull
    public File getReportsDir() {
        return new File(getBuildDir(), FD_REPORTS);
    }

    public File getTestResultsFolder() {
        return new File(getBuildDir(), "test-results");
    }

    public File getTestReportFolder() {
        return new File(getBuildDir(), "reports/tests");
    }

    @NonNull
    public File getTmpFolder() {
        return new File(getIntermediatesDir(), "tmp");
    }

    @NonNull
    public File getOutputsDir() {
        return new File(getBuildDir(), FD_OUTPUTS);
    }

    @Override
    public boolean isActive(OptionalCompilationStep step) {
        return optionalCompilationSteps.contains(step);
    }

    @NonNull
    public String getArchivesBaseName() {
        return (String)getProject().getProperties().get("archivesBaseName");
    }

    @NonNull
    public File getJacocoAgentOutputDirectory() {
        return new File(getIntermediatesDir(), "jacoco");
    }

    @NonNull
    public File getJacocoAgent() {
        return new File(getJacocoAgentOutputDirectory(), "jacocoagent.jar");
    }

    @NonNull
    @Override
    public ProjectOptions getProjectOptions() {
        return projectOptions;
    }

    @Nullable
    @Override
    public FileCache getBuildCache() {
        return buildCache;
    }

    public void setLintChecks(@NonNull Configuration lintChecks) {
        this.lintChecks = lintChecks;
    }

    public void setLintPublish(@NonNull Configuration lintPublish) {
        this.lintPublish = lintPublish;
    }

    public void setAndroidJarConfig(@NonNull Configuration androidJarConfig) {
        this.androidJarConfig = androidJarConfig;
    }

    @NonNull
    public FileCollection getMockableJarArtifact() {
        return getMockableJarArtifact(
                getExtension().getTestOptions().getUnitTests().isReturnDefaultValues());
    }

    @NonNull
    public FileCollection getMockableJarArtifact(boolean returnDefaultValues) {
        Preconditions.checkNotNull(androidJarConfig);
        Action<AttributeContainer> attributes =
                container ->
                        container
                                .attribute(ARTIFACT_TYPE, AndroidArtifacts.TYPE_MOCKABLE_JAR)
                                .attribute(MOCKABLE_JAR_RETURN_DEFAULT_VALUES, returnDefaultValues);

        return androidJarConfig
                .getIncoming()
                .artifactView(config -> config.attributes(attributes))
                .getArtifacts()
                .getArtifactFiles();
    }

    @NonNull
    public FileCollection getPlatformAttrs() {
        Preconditions.checkNotNull(androidJarConfig);
        Action<AttributeContainer> attributes =
                container ->
                        container.attribute(ARTIFACT_TYPE, AndroidArtifacts.TYPE_PLATFORM_ATTR);

        return androidJarConfig
                .getIncoming()
                .artifactView(config -> config.attributes(attributes))
                .getArtifacts()
                .getArtifactFiles();
    }

    @NonNull
    public DslScope getDslScope() {
        return dslScope;
    }

    @NonNull
    public MessageReceiver getMessageReceiver() {
        return messageReceiver;
    }

    /**
     * Gets the lint JAR from the lint checking configuration.
     *
     * @return the resolved lint.jar ArtifactFile from the lint checking configuration
     */
    @NonNull
    public FileCollection getLocalCustomLintChecks() {
        // Query for JAR instead of PROCESSED_JAR as we want to get the original lint.jar
        Action<AttributeContainer> attributes =
                container ->
                        container.attribute(
                                ARTIFACT_TYPE, AndroidArtifacts.ArtifactType.JAR.getType());

        return lintChecks
                .getIncoming()
                .artifactView(config -> config.attributes(attributes))
                .getArtifacts()
                .getArtifactFiles();
    }

    /**
     * Gets the lint JAR from the lint publishing configuration.
     *
     * @return the resolved lint.jar ArtifactFile from the lint publishing configuration
     */
    @NonNull
    public FileCollection getPublishedCustomLintChecks() {
        // Query for JAR instead of PROCESSED_JAR as we want to get the original lint.jar
        Action<AttributeContainer> attributes =
                container ->
                        container.attribute(
                                ARTIFACT_TYPE, AndroidArtifacts.ArtifactType.JAR.getType());

        return lintPublish
                .getIncoming()
                .artifactView(config -> config.attributes(attributes))
                .getArtifacts()
                .getArtifactFiles();
    }

    @NonNull
    public BuildArtifactsHolder getArtifacts() {
        return globalArtifacts;
    }

    public boolean hasDynamicFeatures() {
        final BaseExtension extension = getExtension();
        if (extension instanceof BaseAppModuleExtension) {
            return !((BaseAppModuleExtension) extension).getDynamicFeatures().isEmpty();
        }

        return false;
    }

    /**
     * Returns the boot classpath to be used during compilation with the core lambdas stubs.
     *
     * @return {@link FileCollection} for the boot classpath.
     */
    @NonNull
    public FileCollection getBootClasspath() {
        if (bootClasspath == null) {
            bootClasspath = project.files(getFilteredBootClasspath());
            if (extension.getCompileOptions().getTargetCompatibility().isJava8Compatible()) {
                bootClasspath.from(getSdkComponents().getCoreLambdaStubsProvider());
            }
        }
        return bootClasspath;
    }

    /**
     * Returns the boot classpath to be used during compilation with all available additional jars
     * but only the requested optional ones.
     *
     * <p>Requested libraries not found will be reported to the issue handler.
     *
     * @return a {@link FileCollection} that forms the filtered classpath.
     */
    public FileCollection getFilteredBootClasspath() {
        return BootClasspathBuilder.INSTANCE.computeClasspath(
                project,
                getDslScope().getIssueReporter(),
                getSdkComponents().getTargetBootClasspathProvider(),
                getSdkComponents().getTargetAndroidVersionProvider(),
                getSdkComponents().getAdditionalLibrariesProvider(),
                getSdkComponents().getOptionalLibrariesProvider(),
                getSdkComponents().getAnnotationsJarProvider(),
                false,
                ImmutableList.copyOf(getExtension().getLibraryRequests()));
    }

    /**
     * Returns the boot classpath to be used during compilation with all available additional jars
     * including all optional libraries.
     *
     * @return a {@link FileCollection} that forms the filtered classpath.
     */
    @NonNull
    public FileCollection getFullBootClasspath() {
        return BootClasspathBuilder.INSTANCE.computeClasspath(
                project,
                getDslScope().getIssueReporter(),
                getSdkComponents().getTargetBootClasspathProvider(),
                getSdkComponents().getTargetAndroidVersionProvider(),
                getSdkComponents().getAdditionalLibrariesProvider(),
                getSdkComponents().getOptionalLibrariesProvider(),
                getSdkComponents().getAnnotationsJarProvider(),
                true,
                ImmutableList.of());
    }

    @NonNull
    public SoftwareComponentFactory getComponentFactory() {
        return componentFactory;
    }
}

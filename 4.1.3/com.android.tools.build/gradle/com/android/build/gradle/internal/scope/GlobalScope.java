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
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;
import static com.android.builder.model.AndroidProject.FD_OUTPUTS;
import static com.google.common.base.Preconditions.checkNotNull;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.impl.ArtifactsImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.SdkComponentsBuildService;
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension;
import com.android.build.gradle.internal.ide.DependencyFailureHandler;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.SyncOptions;
import com.android.builder.model.OptionalCompilationStep;
import com.android.ide.common.blame.MessageReceiver;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.provider.Provider;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** A scope containing data for the Android plugin. */
public class GlobalScope {

    @NonNull private final Project project;
    @NonNull private final DataBindingBuilder dataBindingBuilder;
    @NonNull private BaseExtension extension;
    @NonNull private final Provider<SdkComponentsBuildService> sdkComponents;
    @NonNull private final ToolingModelBuilderRegistry toolingRegistry;
    @NonNull private final Set<OptionalCompilationStep> optionalCompilationSteps;
    @NonNull private final MessageReceiver messageReceiver;
    @NonNull private final SoftwareComponentFactory componentFactory;

    @NonNull private final String createdBy;
    @NonNull private final DslServices dslServices;

    @NonNull private Configuration lintChecks;
    @NonNull private Configuration lintPublish;

    private Configuration androidJarConfig;

    @NonNull private final ArtifactsImpl globalArtifacts;

    @Nullable private Provider<List<RegularFile>> bootClasspath = null;

    public GlobalScope(
            @NonNull Project project,
            @NonNull String createdBy,
            @NonNull DslServices dslServices,
            @NonNull Provider<SdkComponentsBuildService> sdkComponents,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull MessageReceiver messageReceiver,
            @NonNull SoftwareComponentFactory componentFactory) {
        // Attention: remember that this code runs early in the build lifecycle, project may not
        // have been fully configured yet (e.g. buildDir can still change).
        this.project = checkNotNull(project);
        this.createdBy = createdBy;
        this.dslServices = checkNotNull(dslServices);
        this.sdkComponents = sdkComponents;
        this.toolingRegistry = checkNotNull(toolingRegistry);
        this.optionalCompilationSteps =
                checkNotNull(dslServices.getProjectOptions().getOptionalCompilationSteps());
        this.messageReceiver = messageReceiver;
        this.componentFactory = componentFactory;

        this.globalArtifacts = new ArtifactsImpl(project, "global");

        // Create empty configurations before these have been set.
        this.lintChecks = project.getConfigurations().detachedConfiguration();

        this.dataBindingBuilder = new DataBindingBuilder();
        dataBindingBuilder.setPrintMachineReadableOutput(
                SyncOptions.getErrorFormatMode(dslServices.getProjectOptions())
                        == SyncOptions.ErrorFormatMode.MACHINE_PARSABLE);
    }

    public void setExtension(@NonNull BaseExtension extension) {
        this.extension = checkNotNull(extension);
    }

    @NonNull
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
    public DataBindingBuilder getDataBindingBuilder() {
        return dataBindingBuilder;
    }

    @NonNull
    public String getProjectBaseName() {
        BasePluginConvention convention =
                Preconditions.checkNotNull(
                        project.getConvention().findPlugin(BasePluginConvention.class));
        return convention.getArchivesBaseName();
    }

    @NonNull
    public Provider<SdkComponentsBuildService> getSdkComponents() {
        return sdkComponents;
    }

    @NonNull
    public ToolingModelBuilderRegistry getToolingRegistry() {
        return toolingRegistry;
    }

    @NonNull
    public File getBuildDir() {
        return project.getBuildDir();
    }

    @NonNull
    public File getIntermediatesDir() {
        return new File(getBuildDir(), FD_INTERMEDIATES);
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

    public boolean isActive(OptionalCompilationStep step) {
        return optionalCompilationSteps.contains(step);
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
    public ProjectOptions getProjectOptions() {
        return dslServices.getProjectOptions();
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

    /**
     * Do not use unless you have to.
     *
     * <p>If the code has access to DslServices directly, use that. If the code has access to
     * VariantPropertiesApiServices or VariantApiServices, use that. If the code has access to
     * TaskCreationServices, use that
     */
    @Deprecated
    @NonNull
    public DslServices getDslServices() {
        return dslServices;
    }

    @NonNull
    public MessageReceiver getMessageReceiver() {
        return messageReceiver;
    }

    /**
     * Gets the lint JAR from the lint checking configuration.
     *
     * @return the resolved lint.jar artifact files from the lint checking configuration
     */
    @NonNull
    public FileCollection getLocalCustomLintChecks() {
        boolean lenientMode =
                dslServices.getProjectOptions().get(BooleanOption.IDE_BUILD_MODEL_ONLY);

        // Query for JAR instead of PROCESSED_JAR as we want to get the original lint.jar
        Action<AttributeContainer> attributes =
                container ->
                        container.attribute(
                                ARTIFACT_TYPE, AndroidArtifacts.ArtifactType.JAR.getType());

        ArtifactCollection artifactCollection =
                lintChecks
                        .getIncoming()
                        .artifactView(
                                config -> {
                                    config.attributes(attributes);
                                    config.lenient(lenientMode);
                                })
                        .getArtifacts();

        if (lenientMode) {
            Collection<Throwable> failures = artifactCollection.getFailures();
            if (!failures.isEmpty()) {
                DependencyFailureHandler failureHandler = new DependencyFailureHandler();
                failureHandler.addErrors(project.getPath() + "/" + lintChecks.getName(), failures);
                failureHandler.registerIssues(dslServices.getIssueReporter());
            }
        }

        return artifactCollection.getArtifactFiles();
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
    public ArtifactsImpl getGlobalArtifacts() {
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
    public Provider<List<RegularFile>> getBootClasspath() {
        if (bootClasspath == null) {
            bootClasspath =
                    project.provider(
                            () -> {
                                ImmutableList.Builder<RegularFile> builder =
                                        ImmutableList.builder();
                                builder.addAll(getFilteredBootClasspath().get());
                                if (extension
                                        .getCompileOptions()
                                        .getTargetCompatibility()
                                        .isJava8Compatible()) {
                                    builder.add(
                                            getSdkComponents()
                                                    .get()
                                                    .getCoreLambdaStubsProvider()
                                                    .get());
                                }
                                return builder.build();
                            });
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
    public Provider<List<RegularFile>> getFilteredBootClasspath() {
        return BootClasspathBuilder.INSTANCE.computeClasspath(
                project,
                getDslServices().getIssueReporter(),
                getSdkComponents()
                        .flatMap(SdkComponentsBuildService::getTargetBootClasspathProvider),
                getSdkComponents()
                        .flatMap(SdkComponentsBuildService::getTargetAndroidVersionProvider),
                getSdkComponents()
                        .flatMap(SdkComponentsBuildService::getAdditionalLibrariesProvider),
                getSdkComponents().flatMap(SdkComponentsBuildService::getOptionalLibrariesProvider),
                getSdkComponents().flatMap(SdkComponentsBuildService::getAnnotationsJarProvider),
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
        return project.files(
                BootClasspathBuilder.INSTANCE
                        .computeClasspath(
                                project,
                                getDslServices().getIssueReporter(),
                                getSdkComponents()
                                        .flatMap(
                                                SdkComponentsBuildService
                                                        ::getTargetBootClasspathProvider),
                                getSdkComponents()
                                        .flatMap(
                                                SdkComponentsBuildService
                                                        ::getTargetAndroidVersionProvider),
                                getSdkComponents()
                                        .flatMap(
                                                SdkComponentsBuildService
                                                        ::getAdditionalLibrariesProvider),
                                getSdkComponents()
                                        .flatMap(
                                                SdkComponentsBuildService
                                                        ::getOptionalLibrariesProvider),
                                getSdkComponents()
                                        .flatMap(
                                                SdkComponentsBuildService
                                                        ::getAnnotationsJarProvider),
                                true,
                                ImmutableList.of())
                        .get());
    }

    @NonNull
    public SoftwareComponentFactory getComponentFactory() {
        return componentFactory;
    }
}

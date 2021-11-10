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
import static com.google.common.base.Preconditions.checkNotNull;

import android.databinding.tool.DataBindingBuilder;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.impl.ArtifactsImpl;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.AvdComponentsBuildService;
import com.android.build.gradle.internal.SdkComponentsBuildService;
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension;
import com.android.build.gradle.internal.lint.CustomLintCheckUtils;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.services.DslServices;
import com.android.build.gradle.options.SyncOptions;
import com.android.builder.model.OptionalCompilationStep;
import com.android.ide.common.blame.MessageReceiver;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Set;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

/** A scope containing data for the Android plugin. */
public class GlobalScope {

    @NonNull private final Project project;
    @NonNull private final DataBindingBuilder dataBindingBuilder;
    @NonNull private BaseExtension extension;
    @NonNull private final Provider<SdkComponentsBuildService> sdkComponents;
    @NonNull private final Provider<AvdComponentsBuildService> avdComponents;
    @NonNull private final ToolingModelBuilderRegistry toolingRegistry;
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
            @NonNull Provider<AvdComponentsBuildService> avdComponents,
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull MessageReceiver messageReceiver,
            @NonNull SoftwareComponentFactory componentFactory) {
        // Attention: remember that this code runs early in the build lifecycle, project may not
        // have been fully configured yet (e.g. buildDir can still change).
        this.project = checkNotNull(project);
        this.createdBy = createdBy;
        this.dslServices = checkNotNull(dslServices);
        this.sdkComponents = sdkComponents;
        this.avdComponents = avdComponents;
        this.toolingRegistry = checkNotNull(toolingRegistry);
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
    public Provider<SdkComponentsBuildService> getSdkComponents() {
        return sdkComponents;
    }

    @NonNull
    public Provider<AvdComponentsBuildService> getAvdComponents() {
        return avdComponents;
    }

    @NonNull
    public ToolingModelBuilderRegistry getToolingRegistry() {
        return toolingRegistry;
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
        return CustomLintCheckUtils.getLocalCustomLintChecks(lintChecks);
    }

    /**
     * Gets the lint JAR from the lint publishing configuration.
     *
     * @return the resolved lint.jar ArtifactFile from the lint publishing configuration
     */
    @NonNull
    public FileCollection getPublishedCustomLintChecks() {
        // Query for JAR instead of PROCESSED_JAR as lint.jar doesn't need processing
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
    public synchronized Provider<List<RegularFile>> getBootClasspath() {
        if (bootClasspath == null) {
            ListProperty<RegularFile> classpath =
                    project.getObjects().listProperty(RegularFile.class);
            // This cannot be protected against unsafe reads until BaseExtension::bootClasspath
            // has been removed. Most users of that method will call it at configuration time which
            // resolves this collection. Uncomment next line once BaseExtension::bootClasspath is
            // removed.
            // classpath.disallowUnsafeRead();
            classpath.addAll(getFilteredBootClasspath());
            if (extension.getCompileOptions().getTargetCompatibility().isJava8Compatible()) {
                classpath.add(
                        getVersionedSdkLoader()
                                .flatMap(
                                        SdkComponentsBuildService.VersionedSdkLoader
                                                ::getCoreLambdaStubsProvider));
            }
            bootClasspath = classpath;
        }
        return bootClasspath;
    }

    @VisibleForTesting
    public synchronized void setBootClasspath(Provider<List<RegularFile>> classpath) {
        this.bootClasspath = classpath;
    }

    private ListProperty<RegularFile> filteredBootClasspath = null;

    /**
     * Returns the boot classpath to be used during compilation with all available additional jars
     * but only the requested optional ones.
     *
     * <p>Requested libraries not found will be reported to the issue handler.
     *
     * @return a {@link FileCollection} that forms the filtered classpath.
     */
    public synchronized Provider<List<RegularFile>> getFilteredBootClasspath() {
        if (filteredBootClasspath == null) {
            Provider<SdkComponentsBuildService.VersionedSdkLoader> versionedSdkLoader =
                    getVersionedSdkLoader();
            filteredBootClasspath = project.getObjects().listProperty(RegularFile.class);
            filteredBootClasspath.addAll(
                    BootClasspathBuilder.INSTANCE.computeClasspath(
                            project.getLayout(),
                            project.getProviders(),
                            project.getObjects(),
                            getDslServices().getIssueReporter(),
                            versionedSdkLoader.flatMap(
                                    SdkComponentsBuildService.VersionedSdkLoader
                                            ::getTargetBootClasspathProvider),
                            versionedSdkLoader.flatMap(
                                    SdkComponentsBuildService.VersionedSdkLoader
                                            ::getTargetAndroidVersionProvider),
                            versionedSdkLoader.flatMap(
                                    SdkComponentsBuildService.VersionedSdkLoader
                                            ::getAdditionalLibrariesProvider),
                            versionedSdkLoader.flatMap(
                                    SdkComponentsBuildService.VersionedSdkLoader
                                            ::getOptionalLibrariesProvider),
                            versionedSdkLoader.flatMap(
                                    SdkComponentsBuildService.VersionedSdkLoader
                                            ::getAnnotationsJarProvider),
                            false,
                            ImmutableList.copyOf(getExtension().getLibraryRequests())));
        }
        return filteredBootClasspath;
    }

    private Provider<SdkComponentsBuildService.VersionedSdkLoader> versionedSdkLoader = null;

    public synchronized Provider<SdkComponentsBuildService.VersionedSdkLoader>
            getVersionedSdkLoader() {
        if (versionedSdkLoader == null) {
            versionedSdkLoader =
                    getSdkComponents()
                            .map(
                                    sdkComponentsBuildService ->
                                            sdkComponentsBuildService.sdkLoader(
                                                    project.provider(
                                                            extension::getCompileSdkVersion),
                                                    project.provider(
                                                            extension::getBuildToolsRevision)));
        }
        return versionedSdkLoader;
    }

    /**
     * Returns the boot classpath to be used during compilation with all available additional jars
     * including all optional libraries.
     *
     * @return a {@link FileCollection} that forms the filtered classpath.
     */
    @NonNull
    public FileCollection getFullBootClasspath() {
        return project.files(getFullBootClasspathProvider().get());
    }

    @NonNull
    public Provider<List<RegularFile>> getFullBootClasspathProvider() {
        return BootClasspathBuilder.INSTANCE.computeClasspath(
                project.getLayout(),
                project.getProviders(),
                project.getObjects(),
                getDslServices().getIssueReporter(),
                getVersionedSdkLoader()
                        .flatMap(
                                SdkComponentsBuildService.VersionedSdkLoader
                                        ::getTargetBootClasspathProvider),
                getVersionedSdkLoader()
                        .flatMap(
                                SdkComponentsBuildService.VersionedSdkLoader
                                        ::getTargetAndroidVersionProvider),
                getVersionedSdkLoader()
                        .flatMap(
                                SdkComponentsBuildService.VersionedSdkLoader
                                        ::getAdditionalLibrariesProvider),
                getVersionedSdkLoader()
                        .flatMap(
                                SdkComponentsBuildService.VersionedSdkLoader
                                        ::getOptionalLibrariesProvider),
                getVersionedSdkLoader()
                        .flatMap(
                                SdkComponentsBuildService.VersionedSdkLoader
                                        ::getAnnotationsJarProvider),
                true,
                ImmutableList.of());
    }

    @NonNull
    public SoftwareComponentFactory getComponentFactory() {
        return componentFactory;
    }
}

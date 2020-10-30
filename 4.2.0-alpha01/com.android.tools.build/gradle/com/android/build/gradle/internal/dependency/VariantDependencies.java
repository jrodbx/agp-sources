/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ARTIFACT_TYPE;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.PROJECT;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.PROVIDED_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.google.common.base.Preconditions.checkArgument;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.VariantType;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.Map;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Spec;

/**
 * Object that represents the dependencies of variant.
 *
 * <p>The dependencies are expressed as composite Gradle configuration objects that extends
 * all the configuration objects of the "configs".</p>
 *
 * <p>It optionally contains the dependencies for a test config for the given config.</p>
 */
public class VariantDependencies {

    public static final String CONFIG_NAME_COMPILE = "compile";
    public static final String CONFIG_NAME_PUBLISH = "publish";
    public static final String CONFIG_NAME_APK = "apk";
    public static final String CONFIG_NAME_PROVIDED = "provided";
    public static final String CONFIG_NAME_WEAR_APP = "wearApp";
    public static final String CONFIG_NAME_ANDROID_APIS = "androidApis";
    public static final String CONFIG_NAME_ANNOTATION_PROCESSOR = "annotationProcessor";

    public static final String CONFIG_NAME_API = "api";
    public static final String CONFIG_NAME_COMPILE_ONLY = "compileOnly";
    public static final String CONFIG_NAME_IMPLEMENTATION = "implementation";
    public static final String CONFIG_NAME_RUNTIME_ONLY = "runtimeOnly";
    @Deprecated public static final String CONFIG_NAME_FEATURE = "feature";
    public static final String CONFIG_NAME_APPLICATION = "application";

    public static final String CONFIG_NAME_LINTCHECKS = "lintChecks";
    public static final String CONFIG_NAME_LINTPUBLISH = "lintPublish";

    public static final String CONFIG_NAME_TESTED_APKS = "testedApks";
    public static final String CONFIG_NAME_CORE_LIBRARY_DESUGARING = "coreLibraryDesugaring";

    @NonNull private final String variantName;
    @NonNull private final VariantType variantType;

    @Nullable private final VariantPropertiesImpl testedVariant;

    @NonNull private final Configuration compileClasspath;
    @NonNull private final Configuration runtimeClasspath;
    @NonNull private final Configuration providedClasspath;

    @NonNull private final Collection<Configuration> sourceSetRuntimeConfigurations;
    @NonNull private final Collection<Configuration> sourceSetImplementationConfigurations;

    @NonNull private final Configuration annotationProcessorConfiguration;
    @Nullable private final Configuration wearAppConfiguration;
    @Nullable private final Configuration reverseMetadataValuesConfiguration;

    @NonNull private final ImmutableMap<PublishedConfigType, Configuration> elements;

    @NonNull private final Project project;
    @NonNull private final ProjectOptions projectOptions;

    VariantDependencies(
            @NonNull String variantName,
            @NonNull VariantType variantType,
            @NonNull Configuration compileClasspath,
            @NonNull Configuration runtimeClasspath,
            @NonNull Collection<Configuration> sourceSetRuntimeConfigurations,
            @NonNull Collection<Configuration> sourceSetImplementationConfigurations,
            @NonNull Map<PublishedConfigType, Configuration> elements,
            @NonNull Configuration providedClasspath,
            @NonNull Configuration annotationProcessorConfiguration,
            @Nullable Configuration reverseMetadataValuesConfiguration,
            @Nullable Configuration wearAppConfiguration,
            @Nullable VariantPropertiesImpl testedVariant,
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions) {
        Preconditions.checkState(
                !variantType.isTestComponent() || testedVariant != null,
                "testedVariantDependencies null for test component");

        this.variantName = variantName;
        this.variantType = variantType;
        this.compileClasspath = compileClasspath;
        this.runtimeClasspath = runtimeClasspath;
        this.sourceSetRuntimeConfigurations = sourceSetRuntimeConfigurations;
        this.sourceSetImplementationConfigurations = sourceSetImplementationConfigurations;
        this.elements = Maps.immutableEnumMap(elements);
        this.providedClasspath = providedClasspath;
        this.annotationProcessorConfiguration = annotationProcessorConfiguration;
        this.reverseMetadataValuesConfiguration = reverseMetadataValuesConfiguration;
        this.wearAppConfiguration = wearAppConfiguration;
        this.testedVariant = testedVariant;
        this.project = project;
        this.projectOptions = projectOptions;
    }

    public String getName() {
        return variantName;
    }

    @NonNull
    public Configuration getCompileClasspath() {
        return compileClasspath;
    }

    @NonNull
    public Configuration getRuntimeClasspath() {
        return runtimeClasspath;
    }

    @NonNull
    public Collection<Dependency> getIncomingRuntimeDependencies() {
        ImmutableList.Builder<Dependency> builder = ImmutableList.builder();
        for (Configuration classpath : sourceSetRuntimeConfigurations) {
            builder.addAll(classpath.getIncoming().getDependencies());
        }
        return builder.build();
    }

    @Nullable
    public Configuration getElements(PublishedConfigType configType) {
        return elements.get(configType);
    }

    @NonNull
    public Configuration getAnnotationProcessorConfiguration() {
        return annotationProcessorConfiguration;
    }

    @Nullable
    public Configuration getWearAppConfiguration() {
        return wearAppConfiguration;
    }

    @Nullable
    public Configuration getReverseMetadataValuesConfiguration() {
        return reverseMetadataValuesConfiguration;
    }

    @NonNull
    public Collection<Configuration> getSourceSetImplementationConfigurations() {
        return sourceSetImplementationConfigurations;
    }

    @NonNull
    public Collection<Configuration> getSourceSetRuntimeConfigurations() {
        return sourceSetRuntimeConfigurations;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("name", variantName).toString();
    }

    @NonNull
    public FileCollection getArtifactFileCollection(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull AndroidArtifacts.ArtifactScope scope,
            @NonNull AndroidArtifacts.ArtifactType artifactType) {
        return getArtifactFileCollection(configType, scope, artifactType, null);
    }

    @NonNull
    public FileCollection getArtifactFileCollection(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull AndroidArtifacts.ArtifactScope scope,
            @NonNull AndroidArtifacts.ArtifactType artifactType,
            @Nullable Map<Attribute<String>, String> attributeMap) {
        if (configType.needsTestedComponents()) {
            return getArtifactCollection(configType, scope, artifactType, attributeMap)
                    .getArtifactFiles();
        }
        ArtifactCollection artifacts =
                computeArtifactCollection(configType, scope, artifactType, attributeMap);

        FileCollection fileCollection;

        if (configType == RUNTIME_CLASSPATH && isArtifactTypeExcluded(artifactType)) {

            FileCollection excludedDirectories =
                    computeArtifactCollection(
                                    PROVIDED_CLASSPATH,
                                    PROJECT,
                                    AndroidArtifacts.ArtifactType.PACKAGED_DEPENDENCIES,
                                    attributeMap)
                            .getArtifactFiles();

            fileCollection =
                    new FilteringSpec(artifacts, excludedDirectories)
                            .getFilteredFileCollection(project);

        } else {
            fileCollection = artifacts.getArtifactFiles();
        }

        return fileCollection;
    }

    @NonNull
    public ArtifactCollection getArtifactCollection(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull AndroidArtifacts.ArtifactScope scope,
            @NonNull AndroidArtifacts.ArtifactType artifactType) {
        return getArtifactCollection(configType, scope, artifactType, null);
    }

    @NonNull
    public ArtifactCollection getArtifactCollection(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull AndroidArtifacts.ArtifactScope scope,
            @NonNull AndroidArtifacts.ArtifactType artifactType,
            @Nullable Map<Attribute<String>, String> attributeMap) {
        ArtifactCollection artifacts =
                computeArtifactCollection(configType, scope, artifactType, attributeMap);

        if (configType == RUNTIME_CLASSPATH && isArtifactTypeExcluded(artifactType)) {

            FileCollection excludedDirectories =
                    computeArtifactCollection(
                                    PROVIDED_CLASSPATH,
                                    PROJECT,
                                    AndroidArtifacts.ArtifactType.PACKAGED_DEPENDENCIES,
                                    null)
                            .getArtifactFiles();
            artifacts =
                    new FilteredArtifactCollection(
                            project, new FilteringSpec(artifacts, excludedDirectories));
        }

        if (!configType.needsTestedComponents() || !variantType.isTestComponent()) {
            return artifacts;
        }

        // get the matching file collection for the tested variant, if any.
        if (testedVariant == null) {
            return artifacts;
        }

        // For artifact that should not be duplicated between test APk and tested APK (e.g. classes)
        // we remove duplicates from test APK. More specifically, for androidTest variants for base
        // and dynamic features, we need to remove artifacts that are already packaged in the tested
        // variant. Also, we remove artifacts already packaged in base/features that the tested
        // feature depends on.
        if (!variantType.isApk()) {
            // Don't filter unit tests.
            return artifacts;
        }
        if (configType != RUNTIME_CLASSPATH) {
            // Only filter runtime classpath.
            return artifacts;
        }
        if (testedVariant.getVariantType().isAar()) {
            // Don't filter test APKs for library projects, as there is no tested APK.
            return artifacts;
        }
        if (!isArtifactTypeSubtractedForInstrumentationTests(artifactType)) {
            return artifacts;
        }
        if (testedVariant.getVariantType().isDynamicFeature()) {
            // If we're in an androidTest for a dynamic feature we need to filter out artifacts from
            // the base and dynamic features this dynamic feature depends on.
            FileCollection excludedDirectories =
                    testedVariant
                            .getVariantDependencies()
                            .computeArtifactCollection(
                                    PROVIDED_CLASSPATH,
                                    PROJECT,
                                    AndroidArtifacts.ArtifactType.PACKAGED_DEPENDENCIES,
                                    null)
                            .getArtifactFiles();

            artifacts =
                    new FilteredArtifactCollection(
                            project, new FilteringSpec(artifacts, excludedDirectories));
        }

        ArtifactCollection testedArtifactCollection =
                testedVariant
                        .getVariantDependencies()
                        .getArtifactCollection(configType, scope, artifactType, attributeMap);
        artifacts =
                new SubtractingArtifactCollection(
                        artifacts, testedArtifactCollection, project.getObjects());
        return artifacts;
    }

    private boolean isArtifactTypeExcluded(@NonNull AndroidArtifacts.ArtifactType artifactType) {
        if (variantType.isDynamicFeature()) {
            return artifactType != AndroidArtifacts.ArtifactType.PACKAGED_DEPENDENCIES
                    && artifactType != AndroidArtifacts.ArtifactType.APKS_FROM_BUNDLE
                    && artifactType != AndroidArtifacts.ArtifactType.FEATURE_DEX
                    && artifactType != AndroidArtifacts.ArtifactType.FEATURE_NAME;
        }
        if (variantType.isSeparateTestProject()) {
            return isArtifactTypeSubtractedForInstrumentationTests(artifactType);
        }
        return false;
    }

    private static boolean isArtifactTypeSubtractedForInstrumentationTests(
            @NonNull AndroidArtifacts.ArtifactType artifactType) {
        return artifactType != AndroidArtifacts.ArtifactType.ANDROID_RES
                && artifactType != AndroidArtifacts.ArtifactType.COMPILED_DEPENDENCIES_RESOURCES;
    }

    @NonNull
    private Configuration getConfiguration(
            @NonNull AndroidArtifacts.ConsumedConfigType configType) {
        switch (configType) {
            case COMPILE_CLASSPATH:
                return getCompileClasspath();
            case RUNTIME_CLASSPATH:
                return getRuntimeClasspath();
            case PROVIDED_CLASSPATH:
                return providedClasspath;
            case ANNOTATION_PROCESSOR:
                return getAnnotationProcessorConfiguration();
            case REVERSE_METADATA_VALUES:
                return Preconditions.checkNotNull(getReverseMetadataValuesConfiguration());
            default:
                throw new RuntimeException("unknown ConfigType value " + configType);
        }
    }

    @NonNull
    public ArtifactCollection getArtifactCollectionForToolingModel(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull AndroidArtifacts.ArtifactScope scope,
            @NonNull AndroidArtifacts.ArtifactType artifactType) {
        return computeArtifactCollection(configType, scope, artifactType, null);
    }

    @NonNull
    private ArtifactCollection computeArtifactCollection(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull AndroidArtifacts.ArtifactScope scope,
            @NonNull AndroidArtifacts.ArtifactType artifactType,
            @Nullable Map<Attribute<String>, String> attributeMap) {
        checkComputeArtifactCollectionArguments(configType, scope, artifactType);

        Configuration configuration = getConfiguration(configType);

        Action<AttributeContainer> attributes =
                container -> {
                    container.attribute(ARTIFACT_TYPE, artifactType.getType());
                    if (attributeMap != null) {
                        for (Attribute<String> attribute : attributeMap.keySet()) {
                            container.attribute(attribute, attributeMap.get(attribute));
                        }
                    }
                };

        Spec<ComponentIdentifier> filter = getComponentFilter(scope);

        boolean lenientMode =
                Boolean.TRUE.equals(projectOptions.get(BooleanOption.IDE_BUILD_MODEL_ONLY));

        return configuration
                .getIncoming()
                .artifactView(
                        config -> {
                            config.attributes(attributes);
                            if (filter != null) {
                                config.componentFilter(filter);
                            }
                            // TODO somehow read the unresolved dependencies?
                            config.lenient(lenientMode);
                        })
                .getArtifacts();
    }

    private static void checkComputeArtifactCollectionArguments(
            @NonNull AndroidArtifacts.ConsumedConfigType configType,
            @NonNull AndroidArtifacts.ArtifactScope scope,
            @NonNull AndroidArtifacts.ArtifactType artifactType) {
        switch (artifactType) {
            case PACKAGED_DEPENDENCIES:
                checkArgument(
                        configType == PROVIDED_CLASSPATH || configType == REVERSE_METADATA_VALUES,
                        "Packaged dependencies must only be requested from the PROVIDED_CLASSPATH or REVERSE_METADATA_VALUES");
                break;
            default:
                break; // No validation
        }
        switch (configType) {
            case PROVIDED_CLASSPATH:
                checkArgument(
                        artifactType == AndroidArtifacts.ArtifactType.PACKAGED_DEPENDENCIES
                                || artifactType == AndroidArtifacts.ArtifactType.APK,
                        "Provided classpath must only be used for from the PACKAGED_DEPENDENCIES and APKS");
                break;
            default:
                break; // No validation
        }
    }

    @Nullable
    private static Spec<ComponentIdentifier> getComponentFilter(
            @NonNull AndroidArtifacts.ArtifactScope scope) {
        switch (scope) {
            case ALL:
                return null;
            case EXTERNAL:
                // since we want both Module dependencies and file based dependencies in this case
                // the best thing to do is search for non ProjectComponentIdentifier.
                return id -> !(id instanceof ProjectComponentIdentifier);
            case PROJECT:
                return id -> id instanceof ProjectComponentIdentifier;
            case REPOSITORY_MODULE:
                return id -> id instanceof ModuleComponentIdentifier;
            case FILE:
                return id ->
                        !(id instanceof ProjectComponentIdentifier
                                || id instanceof ModuleComponentIdentifier);
        }
        throw new RuntimeException("unknown ArtifactScope value");
    }

}

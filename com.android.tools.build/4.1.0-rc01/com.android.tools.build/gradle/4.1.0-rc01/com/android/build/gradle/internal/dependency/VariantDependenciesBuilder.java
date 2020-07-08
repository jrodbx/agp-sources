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

import static com.android.build.gradle.internal.dependency.VariantDependencies.CONFIG_NAME_TESTED_APKS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.AAB_PUBLICATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.ALL_API_PUBLICATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.ALL_RUNTIME_PUBLICATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.API_ELEMENTS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.API_PUBLICATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.APK_PUBLICATION;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.REVERSE_METADATA_ELEMENTS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.RUNTIME_ELEMENTS;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType.RUNTIME_PUBLICATION;
import static org.gradle.api.attributes.Bundling.BUNDLING_ATTRIBUTE;
import static org.gradle.api.attributes.Bundling.EXTERNAL;
import static org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE;
import static org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.attributes.BuildTypeAttr;
import com.android.build.api.attributes.ProductFlavorAttr;
import com.android.build.api.attributes.VariantAttr;
import com.android.build.api.variant.impl.VariantPropertiesImpl;
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet;
import com.android.build.gradle.internal.core.VariantDslInfo;
import com.android.build.gradle.internal.dependency.ConstraintHandler.CachedStringBuildService;
import com.android.build.gradle.internal.dsl.ProductFlavor;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PublishedConfigType;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.VariantType;
import com.android.builder.errors.IssueReporter;
import com.android.utils.StringHelper;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;

/**
 * Object that represents the dependencies of variant.
 *
 * <p>The dependencies are expressed as composite Gradle configuration objects that extends all the
 * configuration objects of the "configs".
 *
 * <p>It optionally contains the dependencies for a test config for the given config.
 */
public class VariantDependenciesBuilder {

    public static VariantDependenciesBuilder builder(
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull IssueReporter errorReporter,
            @NonNull VariantDslInfo variantDslInfo) {
        return new VariantDependenciesBuilder(
                project, projectOptions, errorReporter, variantDslInfo);
    }

    @NonNull private final Project project;
    @NonNull private final ProjectOptions projectOptions;
    @NonNull private final IssueReporter issueReporter;
    @NonNull private final VariantDslInfo variantDslInfo;
    private Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> flavorSelection;

    // default size should be enough. It's going to be rare for a variant to include
    // more than a few configurations (main, build-type, flavors...)
    // At most it's going to be flavor dimension count + 5:
    // variant-specific, build type, multi-flavor, flavor1, ..., flavorN, defaultConfig, test.
    // Default hash-map size of 16 (w/ load factor of .75) should be enough.
    private final Set<Configuration> compileClasspaths = Sets.newLinkedHashSet();
    private final Set<Configuration> apiClasspaths = Sets.newLinkedHashSet();
    private final Set<Configuration> implementationConfigurations = Sets.newLinkedHashSet();
    private final Set<Configuration> runtimeClasspaths = Sets.newLinkedHashSet();
    private final Set<Configuration> annotationConfigs = Sets.newLinkedHashSet();
    private final Set<Configuration> wearAppConfigs = Sets.newLinkedHashSet();
    private VariantPropertiesImpl testedVariant;

    @Nullable private Set<String> featureList;

    protected VariantDependenciesBuilder(
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull IssueReporter issueReporter,
            @NonNull VariantDslInfo variantDslInfo) {
        this.project = project;
        this.projectOptions = projectOptions;
        this.issueReporter = issueReporter;
        this.variantDslInfo = variantDslInfo;
    }

    public VariantDependenciesBuilder addSourceSets(
            @NonNull DefaultAndroidSourceSet... sourceSets) {
        for (DefaultAndroidSourceSet sourceSet : sourceSets) {
            addSourceSet(sourceSet);
        }
        return this;
    }

    public VariantDependenciesBuilder addSourceSets(
            @NonNull Collection<DefaultAndroidSourceSet> sourceSets) {
        for (DefaultAndroidSourceSet sourceSet : sourceSets) {
            addSourceSet(sourceSet);
        }
        return this;
    }

    public VariantDependenciesBuilder setTestedVariant(
            @NonNull VariantPropertiesImpl testedVariant) {
        this.testedVariant = testedVariant;
        return this;
    }

    public VariantDependenciesBuilder setFeatureList(Set<String> featureList) {
        this.featureList = featureList;
        return this;
    }

    public VariantDependenciesBuilder addSourceSet(@Nullable DefaultAndroidSourceSet sourceSet) {
        if (sourceSet != null) {

            final ConfigurationContainer configs = project.getConfigurations();

            compileClasspaths.add(configs.getByName(sourceSet.getCompileOnlyConfigurationName()));
            runtimeClasspaths.add(configs.getByName(sourceSet.getRuntimeOnlyConfigurationName()));

            final Configuration implementationConfig =
                    configs.getByName(sourceSet.getImplementationConfigurationName());
            compileClasspaths.add(implementationConfig);
            runtimeClasspaths.add(implementationConfig);
            implementationConfigurations.add(implementationConfig);

            String apiConfigName = sourceSet.getApiConfigurationName();
            if (apiConfigName != null) {
                apiClasspaths.add(configs.getByName(apiConfigName));
            }

            annotationConfigs.add(
                    configs.getByName(sourceSet.getAnnotationProcessorConfigurationName()));
            wearAppConfigs.add(configs.getByName(sourceSet.getWearAppConfigurationName()));
        }

        return this;
    }

    public VariantDependenciesBuilder setFlavorSelection(
            @NonNull Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> flavorSelection) {
        this.flavorSelection = flavorSelection;
        return this;
    }

    public VariantDependencies build() {
        ObjectFactory factory = project.getObjects();

        final Usage apiUsage = factory.named(Usage.class, Usage.JAVA_API);
        final Usage runtimeUsage = factory.named(Usage.class, Usage.JAVA_RUNTIME);
        final Usage reverseMetadataUsage = factory.named(Usage.class, "android-reverse-meta-data");

        String variantName = variantDslInfo.getComponentIdentity().getName();
        VariantType variantType = variantDslInfo.getVariantType();
        String buildType = variantDslInfo.getComponentIdentity().getBuildType();
        Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> consumptionFlavorMap =
                getFlavorAttributes(flavorSelection);

        final ConfigurationContainer configurations = project.getConfigurations();

        final String compileClasspathName = variantName + "CompileClasspath";
        Configuration compileClasspath = configurations.maybeCreate(compileClasspathName);
        compileClasspath.setVisible(false);
        compileClasspath.setDescription(
                "Resolved configuration for compilation for variant: " + variantName);
        compileClasspath.setExtendsFrom(compileClasspaths);
        if (testedVariant != null) {
            for (Configuration configuration :
                    testedVariant
                            .getVariantDependencies()
                            .getSourceSetImplementationConfigurations()) {
                compileClasspath.extendsFrom(configuration);
            }

            compileClasspath.getDependencies().add(project.getDependencies().create(project));
        }
        compileClasspath.setCanBeConsumed(false);
        compileClasspath
                .getResolutionStrategy()
                .sortArtifacts(ResolutionStrategy.SortOrder.CONSUMER_FIRST);
        final AttributeContainer compileAttributes = compileClasspath.getAttributes();
        applyVariantAttributes(compileAttributes, buildType, consumptionFlavorMap);
        compileAttributes.attribute(Usage.USAGE_ATTRIBUTE, apiUsage);

        Configuration annotationProcessor =
                configurations.maybeCreate(variantName + "AnnotationProcessorClasspath");
        annotationProcessor.setVisible(false);
        annotationProcessor.setDescription(
                "Resolved configuration for annotation-processor for variant: " + variantName);
        annotationProcessor.setExtendsFrom(annotationConfigs);
        annotationProcessor.setCanBeConsumed(false);
        // the annotation processor is using its dependencies for running the processor, so we need
        // all the runtime graph.
        final AttributeContainer annotationAttributes = annotationProcessor.getAttributes();
        annotationAttributes.attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
        applyVariantAttributes(annotationAttributes, buildType, consumptionFlavorMap);

        final String runtimeClasspathName = variantName + "RuntimeClasspath";
        Configuration runtimeClasspath = configurations.maybeCreate(runtimeClasspathName);
        runtimeClasspath.setVisible(false);
        runtimeClasspath.setDescription(
                "Resolved configuration for runtime for variant: " + variantName);
        runtimeClasspath.setExtendsFrom(runtimeClasspaths);
        if (testedVariant != null) {
            if (testedVariant.getVariantDslInfo().getVariantType().isAar()
                    || !variantDslInfo.getVariantType().isApk()) {
                runtimeClasspath.getDependencies().add(project.getDependencies().create(project));
            }
        }
        runtimeClasspath.setCanBeConsumed(false);
        runtimeClasspath
                .getResolutionStrategy()
                .sortArtifacts(ResolutionStrategy.SortOrder.CONSUMER_FIRST);
        final AttributeContainer runtimeAttributes = runtimeClasspath.getAttributes();
        applyVariantAttributes(runtimeAttributes, buildType, consumptionFlavorMap);
        runtimeAttributes.attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);

        if (projectOptions.get(BooleanOption.USE_DEPENDENCY_CONSTRAINTS)) {
            Provider<CachedStringBuildService> cachedStringBuildServiceProvider =
                    new CachedStringBuildService.RegistrationAction(project).execute();
            // make compileClasspath match runtimeClasspath
            compileClasspath
                    .getIncoming()
                    .beforeResolve(
                            new ConstraintHandler(
                                    runtimeClasspath,
                                    project.getDependencies(),
                                    false,
                                    cachedStringBuildServiceProvider));

            // if this is a test App, then also synchronize the 2 runtime classpaths
            if (variantType.isApk() && testedVariant != null) {
                Configuration testedRuntimeClasspath =
                        testedVariant.getVariantDependencies().getRuntimeClasspath();
                runtimeClasspath
                        .getIncoming()
                        .beforeResolve(
                                new ConstraintHandler(
                                        testedRuntimeClasspath,
                                        project.getDependencies(),
                                        true,
                                        cachedStringBuildServiceProvider));
            }
        }

        if (!projectOptions.get(BooleanOption.USE_ANDROID_X)) {
            AndroidXDependencyCheck androidXDependencyCheck =
                    new AndroidXDependencyCheck(issueReporter);
            compileClasspath.getIncoming().afterResolve(androidXDependencyCheck);
            runtimeClasspath.getIncoming().afterResolve(androidXDependencyCheck);
        }

        Configuration globalTestedApks =
                configurations.findByName(VariantDependencies.CONFIG_NAME_TESTED_APKS);
        Configuration providedClasspath;
        if (variantType.isApk() && globalTestedApks != null) {
            // this configuration is created only for test-only project
            Configuration testedApks =
                    configurations.maybeCreate(
                            StringHelper.appendCapitalized(variantName, CONFIG_NAME_TESTED_APKS));
            testedApks.setVisible(false);
            testedApks.setDescription(
                    "Resolved configuration for tested apks for variant: " + variantName);
            testedApks.extendsFrom(globalTestedApks);
            final AttributeContainer testedApksAttributes = testedApks.getAttributes();
            applyVariantAttributes(testedApksAttributes, buildType, consumptionFlavorMap);
            testedApksAttributes.attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
            // For the test only classpath find the packaged dependencies through this testedApks
            // configuration.
            providedClasspath = testedApks;
        } else {
            // For dynamic features, use the runtime classpath to find the packaged dependencies.
            providedClasspath = runtimeClasspath;
        }

        Configuration reverseMetadataValues = null;
        Configuration wearApp = null;
        EnumMap<PublishedConfigType, Configuration> elements =
                Maps.newEnumMap(PublishedConfigType.class);

        if (variantType.isBaseModule()) {
            wearApp = configurations.maybeCreate(variantName + "WearBundling");
            wearApp.setDescription(
                    "Resolved Configuration for wear app bundling for variant: " + variantName);
            wearApp.setExtendsFrom(wearAppConfigs);
            wearApp.setCanBeConsumed(false);
            final AttributeContainer wearAttributes = wearApp.getAttributes();
            applyVariantAttributes(wearAttributes, buildType, consumptionFlavorMap);
            // because the APK is published to Runtime, then we need to make sure this one consumes RUNTIME as well.
            wearAttributes.attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
        }

        VariantAttr variantNameAttr = factory.named(VariantAttr.class, variantName);

        Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> publicationFlavorMap =
                getFlavorAttributes(null);

        if (variantType.getPublishToOtherModules()) {
            // this is the configuration that contains the artifacts for inter-module
            // dependencies.
            Configuration runtimeElements =
                    createPublishingConfig(
                            configurations,
                            variantName + "RuntimeElements",
                            "Runtime elements for " + variantName,
                            buildType,
                            publicationFlavorMap,
                            variantNameAttr,
                            runtimeUsage);

            // always extend from the runtimeClasspath. Let the FilteringSpec handle what
            // should be packaged.
            runtimeElements.extendsFrom(runtimeClasspath);
            elements.put(RUNTIME_ELEMENTS, runtimeElements);

            Configuration apiElements =
                    createPublishingConfig(
                            configurations,
                            variantName + "ApiElements",
                            "API elements for " + variantName,
                            buildType,
                            publicationFlavorMap,
                            variantNameAttr,
                            apiUsage);

            // apiElements only extends the api classpaths.
            apiElements.setExtendsFrom(apiClasspaths);
            elements.put(API_ELEMENTS, apiElements);
        }

        if (variantType.getPublishToRepository()) {
            // if the variant is a library, we need to make both a runtime and an API
            // configurations, and they both must contain transitive dependencies
            if (variantType.isAar()) {
                LibraryElements libraryElements =
                        factory.named(
                                LibraryElements.class, AndroidArtifacts.ArtifactType.AAR.getType());
                Bundling bundling = factory.named(Bundling.class, EXTERNAL);
                Category category = factory.named(Category.class, Category.LIBRARY);

                Configuration runtimePublication =
                        createAarPublishingConfiguration(
                                configurations,
                                variantName + "RuntimePublication",
                                "Runtime publication for " + variantName,
                                runtimeUsage,
                                libraryElements,
                                bundling,
                                category,
                                null,
                                null);
                runtimePublication.extendsFrom(runtimeClasspath);

                elements.put(RUNTIME_PUBLICATION, runtimePublication);

                Configuration apiPublication =
                        createAarPublishingConfiguration(
                                configurations,
                                variantName + "ApiPublication",
                                "API publication for " + variantName,
                                apiUsage,
                                libraryElements,
                                bundling,
                                category,
                                null,
                                null);
                apiPublication.setExtendsFrom(apiClasspaths);
                elements.put(API_PUBLICATION, apiPublication);

                Configuration allApiPublication =
                        createAarPublishingConfiguration(
                                configurations,
                                variantName + "AllApiPublication",
                                "All API publication for " + variantName,
                                apiUsage,
                                libraryElements,
                                bundling,
                                category,
                                buildType,
                                publicationFlavorMap);
                allApiPublication.setExtendsFrom(apiClasspaths);
                elements.put(ALL_API_PUBLICATION, allApiPublication);

                Configuration allRuntimePublication =
                        createAarPublishingConfiguration(
                                configurations,
                                variantName + "AllRuntimePublication",
                                "All runtime publication for " + variantName,
                                runtimeUsage,
                                libraryElements,
                                bundling,
                                category,
                                buildType,
                                publicationFlavorMap);
                allRuntimePublication.setExtendsFrom(runtimeClasspaths);
                elements.put(ALL_RUNTIME_PUBLICATION, allRuntimePublication);
            } else {
                // For APK, no transitive dependencies, and no api vs runtime configs.
                // However we have 2 publications, one for bundle, one for Apk
                Configuration apkPublication =
                        createPublishingConfig(
                                configurations,
                                variantName + "ApkPublication",
                                "APK publication for " + variantName,
                                buildType,
                                publicationFlavorMap,
                                null /*variantNameAttr*/,
                                null /*Usage*/);
                elements.put(APK_PUBLICATION, apkPublication);
                apkPublication.setVisible(false);
                apkPublication.setCanBeConsumed(false);

                Configuration aabPublication =
                        createPublishingConfig(
                                configurations,
                                variantName + "AabPublication",
                                "Bundle Publication for " + variantName,
                                buildType,
                                publicationFlavorMap,
                                null /*variantNameAttr*/,
                                null /*Usage*/);
                elements.put(AAB_PUBLICATION, aabPublication);
                aabPublication.setVisible(false);
                aabPublication.setCanBeConsumed(false);
            }
        }

        if (variantType.getPublishToMetadata()) {
            // Variant-specific reverse metadata publishing configuration. Only published to
            // by base app, optional apks, and non base feature modules.
            Configuration reverseMetadataElements =
                    createPublishingConfig(
                            configurations,
                            variantName + "ReverseMetadataElements",
                            "Reverse Meta-data elements for " + variantName,
                            buildType,
                            publicationFlavorMap,
                            variantNameAttr,
                            reverseMetadataUsage);
            elements.put(REVERSE_METADATA_ELEMENTS, reverseMetadataElements);
        }

        if (variantType.isBaseModule()) {
            // The variant-specific configuration that will contain the feature
            // reverse metadata. It's per-variant to contain the right attribute.
            final String reverseMetadataValuesName = variantName + "ReverseMetadataValues";
            reverseMetadataValues = configurations.maybeCreate(reverseMetadataValuesName);

            if (featureList != null) {
                DependencyHandler depHandler = project.getDependencies();
                List<String> notFound = new ArrayList<>();

                for (String feature : featureList) {
                    Project p = project.findProject(feature);
                    if (p != null) {
                        depHandler.add(reverseMetadataValuesName, p);
                    } else {
                        notFound.add(feature);
                    }
                }

                if (!notFound.isEmpty()) {
                    issueReporter.reportError(
                            IssueReporter.Type.GENERIC,
                            "Unable to find matching projects for Dynamic Features: " + notFound);
                }
            } else {
                reverseMetadataValues.extendsFrom(
                        configurations.getByName(VariantDependencies.CONFIG_NAME_FEATURE));
            }

            reverseMetadataValues.setDescription("Metadata Values dependencies for the base Split");
            reverseMetadataValues.setCanBeConsumed(false);
            final AttributeContainer reverseMetadataValuesAttributes =
                    reverseMetadataValues.getAttributes();
            reverseMetadataValuesAttributes.attribute(Usage.USAGE_ATTRIBUTE, reverseMetadataUsage);
            applyVariantAttributes(
                    reverseMetadataValuesAttributes, buildType, consumptionFlavorMap);
        }

        // TODO remove after a while?
        checkOldConfigurations(configurations, "_" + variantName + "Compile", compileClasspathName);
        checkOldConfigurations(configurations, "_" + variantName + "Apk", runtimeClasspathName);
        checkOldConfigurations(configurations, "_" + variantName + "Publish", runtimeClasspathName);

        return new VariantDependencies(
                variantName,
                variantDslInfo.getVariantType(),
                compileClasspath,
                runtimeClasspath,
                runtimeClasspaths,
                implementationConfigurations,
                elements,
                providedClasspath,
                annotationProcessor,
                reverseMetadataValues,
                wearApp,
                testedVariant,
                project,
                projectOptions);
    }

    @NonNull
    private Configuration createPublishingConfig(
            @NonNull ConfigurationContainer configurations,
            @NonNull String configName,
            @NonNull String configDesc,
            @NonNull String buildType,
            @NonNull Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> publicationFlavorMap,
            @Nullable VariantAttr variantNameAttr,
            @Nullable Usage usage) {
        Configuration config = configurations.maybeCreate(configName);
        config.setDescription(configDesc);
        config.setCanBeResolved(false);

        final AttributeContainer attrContainer = config.getAttributes();

        applyVariantAttributes(attrContainer, buildType, publicationFlavorMap);

        if (variantNameAttr != null) {
            attrContainer.attribute(VariantAttr.ATTRIBUTE, variantNameAttr);
        }

        if (usage != null) {
            attrContainer.attribute(Usage.USAGE_ATTRIBUTE, usage);
        }

        return config;
    }

    @NonNull
    private Configuration createAarPublishingConfiguration(
            @NonNull ConfigurationContainer configurations,
            @NonNull String configName,
            @NonNull String configDesc,
            @NonNull Usage usage,
            @NonNull LibraryElements libraryElements,
            @NonNull Bundling bundling,
            @NonNull Category category,
            @Nullable String buildType,
            @Nullable Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> publicationFlavorMap) {
        Configuration config = configurations.maybeCreate(configName);
        config.setDescription(configDesc);
        config.setCanBeResolved(false);
        config.setVisible(false);
        config.setCanBeConsumed(false);

        final AttributeContainer attrContainer = config.getAttributes();
        attrContainer.attribute(Usage.USAGE_ATTRIBUTE, usage);

        // Add standard attributes defined by Gradle.
        attrContainer.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, libraryElements);
        attrContainer.attribute(BUNDLING_ATTRIBUTE, bundling);
        attrContainer.attribute(CATEGORY_ATTRIBUTE, category);

        if (buildType != null) {
            Preconditions.checkNotNull(publicationFlavorMap);
            applyVariantAttributes(attrContainer, buildType, publicationFlavorMap);
        }

        return config;
    }

    private static void checkOldConfigurations(
            @NonNull ConfigurationContainer configurations,
            @NonNull String oldConfigName,
            @NonNull String newConfigName) {
        if (configurations.findByName(oldConfigName) != null) {
            throw new RuntimeException(
                    String.format(
                            "Configuration with old name %s found. Use new name %s instead.",
                            oldConfigName, newConfigName));
        }
    }

    /**
     * Returns a map of Configuration attributes containing all the flavor values.
     *
     * @param flavorSelection a list of override for flavor matching or for new attributes.
     */
    @NonNull
    private Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> getFlavorAttributes(
            @Nullable Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> flavorSelection) {
        List<ProductFlavor> productFlavors = variantDslInfo.getProductFlavorList();
        Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> map =
                Maps.newHashMapWithExpectedSize(productFlavors.size());

        // during a sync, it's possible that the flavors don't have dimension names because
        // the variant manager is lenient about it.
        // In that case we're going to avoid resolving the dependencies anyway, so we can just
        // skip this.
        if (issueReporter.hasIssue(IssueReporter.Type.UNNAMED_FLAVOR_DIMENSION)) {
            return map;
        }

        final ObjectFactory objectFactory = project.getObjects();

        // first go through the product flavors and add matching attributes
        for (ProductFlavor f : productFlavors) {
            assert f.getDimension() != null;

            map.put(
                    Attribute.of(f.getDimension(), ProductFlavorAttr.class),
                    objectFactory.named(ProductFlavorAttr.class, f.getName()));
        }

        // then go through the override or new attributes.
        if (flavorSelection != null) {
            map.putAll(flavorSelection);
        }

        return map;
    }

    private void applyVariantAttributes(
            @NonNull AttributeContainer attributeContainer,
            @NonNull String buildType,
            @NonNull Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr> flavorMap) {
        attributeContainer.attribute(
                BuildTypeAttr.ATTRIBUTE,
                project.getObjects().named(BuildTypeAttr.class, buildType));
        for (Map.Entry<Attribute<ProductFlavorAttr>, ProductFlavorAttr> entry :
                flavorMap.entrySet()) {
            attributeContainer.attribute(entry.getKey(), entry.getValue());
        }
    }
}

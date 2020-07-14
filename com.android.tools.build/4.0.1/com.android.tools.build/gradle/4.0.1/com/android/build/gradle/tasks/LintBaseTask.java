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

package com.android.build.gradle.tasks;

import static com.android.build.VariantOutput.OutputType.FULL_SPLIT;
import static com.android.build.VariantOutput.OutputType.MAIN;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.LINT;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;
import static com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_MANIFEST;
import static com.android.build.gradle.internal.scope.InternalArtifactType.LINT_JAR;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MANIFEST_MERGE_REPORT;
import static com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_MANIFESTS;

import com.android.Version;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AppExtension;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.LibraryExtension;
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.internal.dsl.LintOptions;
import com.android.build.gradle.internal.ide.dependencies.ArtifactCollections;
import com.android.build.gradle.internal.scope.BuildArtifactsHolder;
import com.android.build.gradle.internal.scope.BuildElements;
import com.android.build.gradle.internal.scope.BuildOutput;
import com.android.build.gradle.internal.scope.ExistingBuildElements;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.internal.variant.TestedVariantData;
import com.android.builder.core.VariantType;
import com.android.repository.Revision;
import com.android.tools.lint.gradle.api.ReflectiveLintRunner;
import com.android.utils.Pair;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.HasConvention;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

public abstract class LintBaseTask extends DefaultTask {
    public static final String LINT_CLASS_PATH = "lintClassPath";

    protected static final Logger LOG = Logging.getLogger(LintBaseTask.class);

    @Nullable FileCollection lintClassPath;
    protected Provider<Revision> buildToolsRevisionProvider;

    /** Lint classpath */
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    @Nullable
    public FileCollection getLintClassPath() {
        return lintClassPath;
    }

    @Nullable protected LintOptions lintOptions;
    protected File sdkHome;
    protected ToolingModelBuilderRegistry toolingRegistry;
    @Nullable protected File reportsDir;

    @Internal("Temporary to suppress Gradle warnings (bug 135900510), may need more investigation")
    @Nullable
    public LintOptions getLintOptions() {
        return lintOptions;
    }

    protected void runLint(LintBaseTaskDescriptor descriptor) {
        FileCollection lintClassPath = getLintClassPath();
        if (lintClassPath != null) {
            new ReflectiveLintRunner().runLint(getProject().getGradle(),
                    descriptor, lintClassPath.getFiles());
        }
    }

    // No influence on output, this is to give access to the build tools version.
    @NonNull
    private Revision getBuildToolsRevision() {
        return buildToolsRevisionProvider.get();
    }

    protected abstract class LintBaseTaskDescriptor extends
            com.android.tools.lint.gradle.api.LintExecutionRequest {

        @Override
        @NonNull
        public File getSdkHome() {
            return sdkHome;
        }

        @NonNull
        @Override
        public ToolingModelBuilderRegistry getToolingRegistry() {
            return toolingRegistry;
        }

        @Nullable
        @Override
        public LintOptions getLintOptions() {
            return lintOptions;
        }

        @Override
        @Nullable
        public File getReportsDir() {
            return reportsDir;
        }

        @NonNull
        @Override
        public Project getProject() {
            return LintBaseTask.this.getProject();
        }

        @NonNull
        @Override
        public Revision getBuildToolsRevision() {
            return LintBaseTask.this.getBuildToolsRevision();
        }

        @Override
        public void warn(@NonNull String message, @NonNull Object... args) {
            LOG.warn(message, args);
        }

        @NonNull
        @Override
        public String getGradlePluginVersion() {
            return Version.ANDROID_GRADLE_PLUGIN_VERSION;
        }

        private final Cache<Pair<String, String>, List<File>> kotlinSourceFoldersCache =
                CacheBuilder.newBuilder().build();

        private List<File> doFetchKotlinSourceFolders(
                @NonNull String sourceSetName, @NonNull Project project) throws Exception {
            BaseExtension extension = (BaseExtension) project.getExtensions().getByName("android");
            Object kotlinSourceSet =
                    ((HasConvention) extension.getSourceSets().getByName(sourceSetName))
                            .getConvention()
                            .getPlugins()
                            .get("kotlin");
            Method getSourceDirectorySet =
                    kotlinSourceSet.getClass().getDeclaredMethod("getKotlin");
            SourceDirectorySet sourceDirectorySet =
                    (SourceDirectorySet) getSourceDirectorySet.invoke(kotlinSourceSet);
            return sourceDirectorySet
                    .getSrcDirs()
                    .stream()
                    .filter(File::exists)
                    .collect(Collectors.toList());
        }

        private List<File> fetchKotlinSourceFolders(
                @NonNull String sourceSetName, @NonNull Project project) {
            try {
                return kotlinSourceFoldersCache.get(
                        Pair.of(sourceSetName, project.getPath()),
                        () -> doFetchKotlinSourceFolders(sourceSetName, project));
            } catch (Throwable e) {
                getLogger()
                        .warn(
                                "Unable to fetch kotlin source folders for source set "
                                        + sourceSetName,
                                e);
                return Collections.emptyList();
            }
        }

        @NonNull
        @Override
        public List<File> getKotlinSourceFolders(@NonNull String variantName, Project project) {
            if (project == null || !project.getPlugins().hasPlugin("kotlin-android")) {
                return Collections.emptyList();
            }
            ImmutableSet.Builder<File> builder = new ImmutableSet.Builder<>();
            BaseExtension extension = (BaseExtension) project.getExtensions().getByName("android");
            DomainObjectSet<? extends BaseVariant> variants;
            if (extension instanceof AppExtension) {
                variants = ((AppExtension) extension).getApplicationVariants();
            } else if (extension instanceof LibraryExtension) {
                variants = ((LibraryExtension) extension).getLibraryVariants();
            } else {
                return Collections.emptyList();
            }
            variants.matching(it -> it.getName().equals(variantName))
                    .forEach(
                            variant ->
                                    variant.getSourceSets()
                                            .forEach(
                                                    sourceProvider -> {
                                                        builder.addAll(
                                                                fetchKotlinSourceFolders(
                                                                        sourceProvider.getName(),
                                                                        project));
                                                    }));
            return builder.build().asList();
        }
    }

    /**
     * These artifacts are used in ModelBuilder eventually, we add them to inputs here so Gradle
     * would make sure they are resolved before starting the task.
     */
    protected static void addModelArtifactsToInputs(
            @NonNull ConfigurableFileCollection inputs, @NonNull VariantScope variantScope) {

        inputs.from(
                (Callable<Collection<ArtifactCollection>>)
                        () ->
                                new ArtifactCollections(variantScope, COMPILE_CLASSPATH)
                                        .getAllCollections());
        inputs.from(
                (Callable<Collection<ArtifactCollection>>)
                        () ->
                                new ArtifactCollections(variantScope, RUNTIME_CLASSPATH)
                                        .getAllCollections());

        if (variantScope.getVariantData() instanceof TestedVariantData) {
            for (VariantType variantType : VariantType.Companion.getTestComponents()) {
                TestVariantData testVariantData =
                        ((TestedVariantData) variantScope.getVariantData())
                                .getTestVariantData(variantType);
                if (testVariantData != null) {
                    addModelArtifactsToInputs(inputs, testVariantData.getScope());
                }
            }
        }
    }

    public static class VariantInputs implements com.android.tools.lint.gradle.api.VariantInputs {
        @NonNull private final String name;
        @NonNull private final Provider<? extends FileSystemLocation> mergedManifest;
        @NonNull private final Provider<RegularFile> mergedManifestReport;
        @NonNull private final FileCollection lintRuleJars;

        private final ConfigurableFileCollection allInputs;

        public VariantInputs(@NonNull VariantScope variantScope) {
            name = variantScope.getName();
            allInputs = variantScope.getGlobalScope().getProject().files();

            Provider<RegularFile> localLintJarCollection;
            allInputs.from(
                    localLintJarCollection =
                            variantScope
                                    .getGlobalScope()
                                    .getArtifacts()
                                    .getFinalProduct(LINT_JAR.INSTANCE));
            FileCollection dependencyLintJarCollection;
            allInputs.from(
                    dependencyLintJarCollection =
                            variantScope.getArtifactFileCollection(RUNTIME_CLASSPATH, ALL, LINT));

            lintRuleJars = variantScope.getGlobalScope().getProject().files(
                    localLintJarCollection,
                    dependencyLintJarCollection);

            BuildArtifactsHolder artifacts = variantScope.getArtifacts();
            Provider<? extends FileSystemLocation> tmpMergedManifest =
                    artifacts.getFinalProduct(MERGED_MANIFESTS.INSTANCE);
            if (!tmpMergedManifest.isPresent()) {
                tmpMergedManifest = artifacts.getFinalProduct(LIBRARY_MANIFEST.INSTANCE);
            }
            if (!tmpMergedManifest.isPresent()) {
                throw new RuntimeException(
                        "VariantInputs initialized with no merged manifest on: "
                                + variantScope.getVariantDslInfo().getVariantType());
            }
            mergedManifest = tmpMergedManifest;
            allInputs.from(mergedManifest);

            mergedManifestReport = artifacts.getFinalProduct(MANIFEST_MERGE_REPORT.INSTANCE);
            if (mergedManifest.isPresent()) {
                allInputs.from(mergedManifestReport);
            } else {
                throw new RuntimeException(
                        "VariantInputs initialized with no merged manifest report on: "
                                + variantScope.getVariantDslInfo().getVariantType());
            }

            // these inputs are only there to ensure that the lint task runs after these build
            // intermediates are built.
            allInputs.from(artifacts.getAllClasses());

            addModelArtifactsToInputs(allInputs, variantScope);
        }

        @NonNull
        public FileCollection getAllInputs() {
            return allInputs;
        }

        @Override
        @NonNull
        public String getName() {
            return name;
        }

        /** the lint rule jars */
        @Override
        @NonNull
        public FileCollection getRuleJars() {
            return lintRuleJars;
        }

        /** the merged manifest of the current module */
        @Override
        @NonNull
        public File getMergedManifest() {
            File file = mergedManifest.get().getAsFile();
            if (file.isFile()) {
                return file;
            }

            BuildElements manifests =
                    ExistingBuildElements.from(
                            InternalArtifactType.MERGED_MANIFESTS.INSTANCE, file);

            if (manifests.isEmpty()) {
                throw new RuntimeException("Can't find any manifest in folder: " + file);
            }

            // first search for a main manifest
            Optional<File> mainManifest =
                    manifests
                            .stream()
                            .filter(buildOutput -> buildOutput.getApkData().getType() == MAIN)
                            .map(BuildOutput::getOutputFile)
                            .findFirst();
            if (mainManifest.isPresent()) {
                return mainManifest.get();
            }

            // else search for a full_split with no filters.
            Optional<File> universalSplit =
                    manifests
                            .stream()
                            .filter(
                                    output ->
                                            output.getApkData().getType() == FULL_SPLIT
                                                    && output.getFilters().isEmpty())
                            .map(BuildOutput::getOutputFile)
                            .findFirst();

            // return the universal Manifest, or a random one if not found.
            return universalSplit.orElseGet(() -> manifests.iterator().next().getOutputFile());
        }

        @Override
        @Nullable
        public File getManifestMergeReport() {
            if (mergedManifestReport.isPresent()) {
                return mergedManifestReport.get().getAsFile();
            } else {
                return null;
            }
        }
    }

    public abstract static class BaseCreationAction<T extends LintBaseTask>
            extends TaskCreationAction<T> {

        @NonNull private final GlobalScope globalScope;

        public BaseCreationAction(@NonNull GlobalScope globalScope) {
            this.globalScope = globalScope;
        }

        @NonNull
        protected GlobalScope getGlobalScope() {
            return globalScope;
        }

        @Override
        public void configure(@NonNull T lintTask) {
            lintTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
            lintTask.lintOptions = globalScope.getExtension().getLintOptions();
            lintTask.sdkHome = globalScope.getSdkComponents().getSdkDirectory();
            lintTask.toolingRegistry = globalScope.getToolingRegistry();
            lintTask.reportsDir = globalScope.getReportsDir();
            lintTask.buildToolsRevisionProvider =
                    globalScope.getSdkComponents().getBuildToolsRevisionProvider();

            lintTask.lintClassPath = globalScope.getProject().getConfigurations()
                    .getByName(LINT_CLASS_PATH);
        }
    }
}

/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.tasks.factory;

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType;
import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.artifact.ScopedArtifact;
import com.android.build.api.artifact.impl.ArtifactsImpl;
import com.android.build.api.variant.ScopedArtifacts;
import com.android.build.gradle.internal.SdkComponentsBuildService;
import com.android.build.gradle.internal.component.ComponentCreationConfig;
import com.android.build.gradle.internal.component.HostTestCreationConfig;
import com.android.build.gradle.internal.component.VariantCreationConfig;
import com.android.build.gradle.internal.core.dsl.features.UnitTestOptionsDslInfo;
import com.android.build.gradle.internal.publishing.AndroidArtifacts;
import com.android.build.gradle.internal.scope.BootClasspathBuilder;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.build.gradle.internal.tasks.BuildAnalyzer;
import com.android.build.gradle.internal.tasks.UsesAnalytics;
import com.android.build.gradle.internal.tasks.VariantTask;
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction;
import com.android.build.gradle.internal.utils.HasConfigurableValuesKt;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.tasks.AndroidAnalyticsTestListener;
import com.android.build.gradle.tasks.GenerateTestConfig;
import com.android.buildanalyzer.common.TaskCategory;
import com.android.builder.core.ComponentType;

import com.google.common.collect.ImmutableList;

import kotlin.Unit;

import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.reporting.DirectoryReport;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.JUnitXmlReport;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestTaskReports;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension;
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

/** Patched version of {@link Test} that we need to use for local host tests support. */
// TODO(b/330843002): This class should be renamed after the bug is fixed.
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST)
public abstract class AndroidUnitTest extends Test implements VariantTask, UsesAnalytics {

    private String variantName;

    private ArtifactCollection dependencies;

    private boolean isIdeInvoked;

    @Nullable private GenerateTestConfig.TestConfigInputs testConfigInputs;

    @Internal
    @NonNull
    @Override
    public String getVariantName() {
        return variantName;
    }

    @Override
    public void setVariantName(@NonNull String name) {
        variantName = name;
    }

    @Nested
    @Optional
    public GenerateTestConfig.TestConfigInputs getTestConfigInputs() {
        return testConfigInputs;
    }

    @OutputFile
    @Optional
    public abstract RegularFileProperty getJacocoCoverageOutputFile();

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ListProperty<Collection<Directory>> getTestJniLibs();

    @Input
    public abstract Property<Boolean> getJacocoExtensionEnabled();

    @Override
    @TaskAction
    public void executeTests() {
        AndroidAnalyticsTestListener testListener =
                new AndroidAnalyticsTestListener(
                        dependencies,
                        getJacocoExtensionEnabled().get(),
                        getAnalyticsService().get(),
                        this.getFilter(),
                        isIdeInvoked);
        this.addTestListener(testListener);

        // Collect all the jni libs directories (including the system ones) and set the
        // "java.library.path" system property for the spawned JVM so test can load jni libraries
        // from those locations.
        List<Collection<Directory>> jniLibsDirs = this.getTestJniLibs().get();
        if (!jniLibsDirs.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            // the System value is always the top priority, we need to set it as the presence
            // of the systemProperty("java.library.path", ...) call below will otherwise ignore it.
            if (System.getProperty("java.library.path") != null) {
                sb.append(System.getProperty("java.library.path"));
            }
            // add existing values before resetting it.
            if (getSystemProperties().get("java.library.path") != null) {
                if (sb.length() > 0) sb.append(File.pathSeparatorChar);
                sb.append(getSystemProperties().get("java.library.path"));
            }

            // append all jni libraries directories in order.
            jniLibsDirs.forEach(
                    directories -> {
                        directories.forEach(
                                directory -> {
                                    sb.append(File.pathSeparatorChar);
                                    sb.append(directory.getAsFile().getAbsolutePath());
                                });
                    });
            systemProperty("java.library.path", sb.toString());
        }

        super.executeTests();
    }

    public static class CreationAction
            extends VariantTaskCreationAction<AndroidUnitTest, ComponentCreationConfig> {

        @NonNull private final HostTestCreationConfig hostTestCreationConfig;

        private final String jacocoVersion;

        private final InternalArtifactType<RegularFile> internalArtifactType;

        public CreationAction(
                @NonNull HostTestCreationConfig hostTestCreationConfig,
                @NonNull String jacocoVersion,
                @NonNull InternalArtifactType<RegularFile> internalArtifactType) {
            super(hostTestCreationConfig);
            this.hostTestCreationConfig = hostTestCreationConfig;
            this.jacocoVersion = jacocoVersion;
            this.internalArtifactType = internalArtifactType;
        }

        @NonNull
        @Override
        public String getName() {
            return computeTaskName(ComponentType.UNIT_TEST_PREFIX);
        }

        @NonNull
        @Override
        public Class<AndroidUnitTest> getType() {
            return AndroidUnitTest.class;
        }

        @Override
        public void handleProvider(@NotNull TaskProvider<AndroidUnitTest> taskProvider) {
            super.handleProvider(taskProvider);
            if (hostTestCreationConfig.getCodeCoverageEnabled()) {
                hostTestCreationConfig
                        .getArtifacts()
                        .setInitialProvider(
                                taskProvider, AndroidUnitTest::getJacocoCoverageOutputFile)
                        .withName(taskProvider.getName() + SdkConstants.DOT_EXEC)
                        .on(internalArtifactType);
            }
        }

        @Override
        public void configure(@NonNull AndroidUnitTest task) {
            super.configure(task);
            UsesAnalytics.ConfigureAction.INSTANCE.configure(task);

            JacocoPluginExtension pluginExtension =
                    task.getProject().getExtensions().findByType(JacocoPluginExtension.class);
            if (pluginExtension != null) {
                pluginExtension.setToolVersion(jacocoVersion);
            }

            JacocoTaskExtension jacocoTaskExtension =
                    task.getExtensions().findByType(JacocoTaskExtension.class);
            HasConfigurableValuesKt.setDisallowChanges(
                    task.getJacocoExtensionEnabled(),
                    hostTestCreationConfig
                            .getServices()
                            .provider(
                                    () ->
                                            jacocoTaskExtension != null
                                                    && jacocoTaskExtension.isEnabled()));

            hostTestCreationConfig.onTestedVariant(
                    testedConfig -> {
                        if (hostTestCreationConfig.getCodeCoverageEnabled()) {
                            task.getProject()
                                    .getPlugins()
                                    .withType(
                                            JacocoPlugin.class,
                                            plugin -> {
                                                jacocoTaskExtension.setDestinationFile(
                                                        task.getJacocoCoverageOutputFile()
                                                                .getAsFile());
                                            });
                        }
                        return null;
                    });

            VariantCreationConfig testedVariant = hostTestCreationConfig.getMainVariant();
            hostTestCreationConfig
                    .getSources()
                    .jniLibs(
                            layeredSourceDirectories -> {
                                HasConfigurableValuesKt.setDisallowChanges(
                                        task.getTestJniLibs(), layeredSourceDirectories.getAll());
                                return Unit.INSTANCE;
                            });

            UnitTestOptionsDslInfo testOptions = creationConfig.getGlobal().getUnitTestOptions();
            boolean includeAndroidResources;
            if (hostTestCreationConfig.getComponentType().isForScreenshotPreview()) {
                includeAndroidResources = true;
            } else {
                includeAndroidResources = testOptions.isIncludeAndroidResources();
            }

            ProjectOptions configOptions = creationConfig.getServices().getProjectOptions();

            // Get projectOptions to determine if the test is invoked from the IDE or the terminal.
            task.isIdeInvoked = configOptions.get(BooleanOption.IDE_INVOKED_FROM_IDE);

            // we run by default in headless mode, so the forked JVM doesn't steal focus.
            task.systemProperty("java.awt.headless", "true");

            task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
            String testType =
                    hostTestCreationConfig.getComponentType().isForScreenshotPreview()
                            ? "screenshot"
                            : "unit";
            task.setDescription(
                    "Run " + testType + " tests for the " + testedVariant.getName() + " build.");

            task.setTestClassesDirs(
                    creationConfig
                            .getArtifacts()
                            .forScope(ScopedArtifacts.Scope.PROJECT)
                            .getFinalArtifacts$gradle_core(ScopedArtifact.CLASSES.INSTANCE));
            task.setClasspath(computeClasspath(creationConfig, includeAndroidResources));

            if (includeAndroidResources) {
                // When computing the classpath above, we made sure this task depends on the output
                // of the GenerateTestConfig task. However, it is not enough. The GenerateTestConfig
                // task has 2 types of inputs: direct inputs and indirect inputs. Only the direct
                // inputs are registered with Gradle, whereas the indirect inputs are not (see that
                // class for details).
                // Since this task also depends on the indirect inputs to the GenerateTestConfig
                // task, we also need to register those inputs with Gradle.
                task.testConfigInputs =
                        new GenerateTestConfig.TestConfigInputs(hostTestCreationConfig);
            }

            // Put the variant name in the report path, so that different testing tasks don't
            // overwrite each other's reports. For component model plugin, the report tasks are not
            // yet configured.  We get a hardcoded value matching Gradle's default. This will
            // eventually be replaced with the new Java plugin.
            TestTaskReports testTaskReports = task.getReports();
            JUnitXmlReport xmlReport = testTaskReports.getJunitXml();
            xmlReport
                    .getOutputLocation()
                    .fileProvider(
                            creationConfig
                                    .getServices()
                                    .getProjectInfo()
                                    .getTestResultsFolder()
                                    .map(it -> it.dir(task.getName()).getAsFile()));

            DirectoryReport htmlReport = testTaskReports.getHtml();
            htmlReport
                    .getOutputLocation()
                    .fileProvider(
                            creationConfig
                                    .getServices()
                                    .getProjectInfo()
                                    .getTestReportFolder()
                                    .map(it -> it.dir(task.getName()).getAsFile()));

            testOptions.applyConfiguration(task);

            // Robolectric specific: add JVM flag to allow unit tests to run when using Java 17+.
            // Ref b/248929512.
            task.jvmArgs((ImmutableList.of("--add-opens=java.base/java.io=ALL-UNNAMED")));

            task.dependencies =
                    creationConfig
                            .getVariantDependencies()
                            .getArtifactCollection(
                                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                                    AndroidArtifacts.ArtifactScope.EXTERNAL,
                                    AndroidArtifacts.ArtifactType.CLASSES_JAR);
        }

        @NonNull
        private ConfigurableFileCollection computeClasspath(
                ComponentCreationConfig creationConfig, boolean includeAndroidResources) {
            ArtifactsImpl artifacts = creationConfig.getArtifacts();

            ConfigurableFileCollection collection = creationConfig.getServices().fileCollection();

            // the test classpath is made up of:
            // 1. the config file
            if (includeAndroidResources) {
                collection.from(
                        artifacts.get(InternalArtifactType.UNIT_TEST_CONFIG_DIRECTORY.INSTANCE));
            }

            // 2. the test creationConfig classes and java_res
            collection.from(
                    artifacts
                            .forScope(ScopedArtifacts.Scope.PROJECT)
                            .getFinalArtifacts$gradle_core(ScopedArtifact.CLASSES.INSTANCE));

            // TODO is this the right thing? this doesn't include the res merging via transform
            // AFAIK
            collection.from(artifacts.get(InternalArtifactType.JAVA_RES.INSTANCE));

            // 3. the runtime dependencies for both CLASSES and JAVA_RES type
            if (creationConfig.getInstrumentationCreationConfig() != null) {
                collection.from(
                        creationConfig
                                .getInstrumentationCreationConfig()
                                .getDependenciesClassesJarsPostInstrumentation(ALL));
            } else {
                collection.from(
                        creationConfig
                                .getVariantDependencies()
                                .getArtifactFileCollection(
                                        RUNTIME_CLASSPATH,
                                        ALL,
                                        AndroidArtifacts.ArtifactType.CLASSES_JAR));
            }
            collection.from(
                    creationConfig
                            .getVariantDependencies()
                            .getArtifactFileCollection(
                                    RUNTIME_CLASSPATH, ALL, ArtifactType.JAVA_RES));

            // 4. The separately compile R class, if applicable.
            if (creationConfig.getAndroidResourcesCreationConfig() != null
                    && !creationConfig.getGlobal().getNamespacedAndroidResources()) {
                collection.from(
                        creationConfig
                                .getAndroidResourcesCreationConfig()
                                .getCompiledRClassArtifact());
            }

            // 5. Any additional or requested optional libraries
            collection.from(getAdditionalAndRequestedOptionalLibraries());

            // 6. Mockable JAR is last, to make sure you can shadow the classes with
            // dependencies.
            collection.from(creationConfig.getGlobal().getMockableJarArtifact());

            return collection;
        }

        /**
         * Returns the list of additional and requested optional library jar files
         *
         * @return the list of files from the additional and optional libraries which appear in the
         *     filtered boot classpath
         */
        @NonNull
        private ConfigurableFileCollection getAdditionalAndRequestedOptionalLibraries() {
            return creationConfig
                    .getServices()
                    .fileCollection(
                            (Callable)
                                    () -> {
                                        SdkComponentsBuildService.VersionedSdkLoader
                                                versionedSdkLoader =
                                                        creationConfig
                                                                .getGlobal()
                                                                .getVersionedSdkLoader()
                                                                .get();
                                        return BootClasspathBuilder.INSTANCE
                                                .computeAdditionalAndRequestedOptionalLibraries(
                                                        creationConfig.getServices(),
                                                        versionedSdkLoader
                                                                .getAdditionalLibrariesProvider()
                                                                .get(),
                                                        versionedSdkLoader
                                                                .getOptionalLibrariesProvider()
                                                                .get(),
                                                        false,
                                                        ImmutableList.copyOf(
                                                                creationConfig
                                                                        .getGlobal()
                                                                        .getLibraryRequests()),
                                                        creationConfig
                                                                .getServices()
                                                                .getIssueReporter());
                                    });
        }
    }
}

/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.internal.testsuites.impl

import com.android.Version
import com.android.build.api.attributes.AgpVersionAttr
import com.android.build.api.attributes.BuildTypeAttr
import com.android.build.api.attributes.ProductFlavorAttr
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.core.dsl.MultiVariantComponentDslInfo
import com.android.build.gradle.internal.dependency.TestSuiteClasspath
import com.android.build.gradle.internal.dependency.VariantAwareDependenciesBuilder
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.errors.IssueReporter
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.internal.extensions.stdlib.capitalized

/**
 * Object that builds the dependencies of a test suite.
 *
 * TODO: reconcile with VariantDependenciesBuilder and see if there are common pattern that can
 * abstracted into a supertype.
 */
class TestSuiteDependenciesBuilder internal constructor(
    project: Project,
    private val projectOptions: ProjectOptions,
    issueReporter: IssueReporter,
    private val testSuiteBuilder: TestSuiteBuilderImpl,
    private val testedVariant: VariantCreationConfig,
    private val flavorSelection: Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr>,
    dslInfo: MultiVariantComponentDslInfo,
): VariantAwareDependenciesBuilder(project, issueReporter, dslInfo) {

    private val jvmEnvironment =
        project.objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.ANDROID)
    private val agpVersion =
        project.objects.named(AgpVersionAttr::class.java, Version.ANDROID_GRADLE_PLUGIN_VERSION)
    private val library =
        project.objects.named(
            org.gradle.api.attributes.Category::class.java, org.gradle.api.attributes.Category.LIBRARY
        )
    private val testSuiteName = testSuiteBuilder.name
    private val dslDeclaredDependencies = testSuiteBuilder.dslDeclaredDependencies
    private val variantDeclaredDependencies = testSuiteBuilder.dependencies

    /**
     * Creates the configuration associated with a test suite.
     *
     * At this point, only compile and runtime classpath are available.
     */
    fun build(): TestSuiteClasspath {
        val factory = project.objects
        val configurations = project.configurations
        val testedVariantName = testedVariant.name

        // ----------- COMPILE CLASSPATH
        val compileClasspathName: String = testSuiteName + testedVariantName.capitalized() + "CompileClasspath"
        val compileClasspath: Configuration = configurations.maybeCreate(compileClasspathName)
        compileClasspath.setVisible(false)
        compileClasspath.setDescription(
            "Resolved configuration for compilation for test suite: $testSuiteName in $testedVariantName"
        )
        populateClasspath(
            compileClasspath,
            listOf(
                dslDeclaredDependencies.compileOnly,
                variantDeclaredDependencies.compileOnly,
                dslDeclaredDependencies.implementation,
                variantDeclaredDependencies.implementation
            )
        )
        compileClasspath.extendsFrom(
            testedVariant.variantDependencies.compileClasspath
        )
        addAttributes(compileClasspath,factory.named(Usage::class.java, Usage.JAVA_API))

        // -------------- RUNTIME CLASSPATH
        val runtimeClasspathName: String = testSuiteName + testedVariantName.capitalized() + "RuntimeClasspath"
        val runtimeClasspath = configurations.maybeCreate(runtimeClasspathName)
        runtimeClasspath.setDescription(
            "Resolved configuration for runtime for tes suite: $testSuiteName in $testedVariantName"
        )
        populateClasspath(
            runtimeClasspath,
            listOf(
                dslDeclaredDependencies.implementation,
                variantDeclaredDependencies.implementation,
                dslDeclaredDependencies.runtimeOnly,
                variantDeclaredDependencies.runtimeOnly
            )
        )
        runtimeClasspath.extendsFrom(
            testedVariant.variantDependencies.runtimeClasspath
        )
        addAttributes(runtimeClasspath, factory.named(Usage::class.java, Usage.JAVA_RUNTIME))

        return TestSuiteClasspath(
            compileClasspath = compileClasspath,
            runtimeClasspath = runtimeClasspath,
            objectFactory = project.objects
        )
    }

    private fun populateClasspath(classpath: Configuration, from: List<DependencyCollector>) {
        for (collector in from) {
            classpath.dependencies.addAllLater(collector.dependencies)
            classpath.dependencyConstraints.addAllLater(collector.dependencyConstraints)
        }
    }

    private fun addAttributes(configuration: Configuration, usage: Usage) {
        configuration.isCanBeConsumed = false
        configuration.isVisible = false
        configuration
            .resolutionStrategy
            .sortArtifacts(ResolutionStrategy.SortOrder.CONSUMER_FIRST)
        val attributes = configuration.attributes
        attributes.attribute(Usage.USAGE_ATTRIBUTE, usage)
        attributes.attribute(
            TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
            jvmEnvironment
        )
        attributes.attribute(AgpVersionAttr.ATTRIBUTE, agpVersion)
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, library)
        val consumptionFlavorMap = getConsumptionFlavorAttributes(flavorSelection)
        applyVariantAttributes(attributes, testedVariant.buildType, consumptionFlavorMap)
    }

    private fun applyVariantAttributes(
        attributeContainer: AttributeContainer,
        buildType: String?,
        flavorMap: Map<Attribute<ProductFlavorAttr>, ProductFlavorAttr>
    ) {
        if (buildType != null) {
            attributeContainer.attribute(
                BuildTypeAttr.ATTRIBUTE,
                project.objects.named(BuildTypeAttr::class.java, buildType)
            )
        }
        for ((key, value) in flavorMap) {
            attributeContainer.attribute(key, value)
        }
    }
}

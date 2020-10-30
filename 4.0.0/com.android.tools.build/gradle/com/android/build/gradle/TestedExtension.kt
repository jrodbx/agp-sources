package com.android.build.gradle

import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.api.UnitTestVariant
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.VariantType
import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.FileCollection

/**
 * Provides test components that are common to [AppExtension], [LibraryExtension], and
 * [FeatureExtension].
 *
 * To learn more about testing Android projects, read
 * [Test your app](https://developer.android.com/studio/test/index.html)
 */
abstract class TestedExtension(
    dslScope: DslScope,
    projectOptions: ProjectOptions,
    globalScope: GlobalScope,
    buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
    sourceSetManager: SourceSetManager,
    extraModelInfo: ExtraModelInfo,
    isBaseModule: Boolean
) : BaseExtension(
    dslScope,
    projectOptions,
    globalScope,
    buildOutputs,
    sourceSetManager,
    extraModelInfo,
    isBaseModule
), TestedAndroidConfig, com.android.build.api.dsl.TestedExtension {

    private val testVariantList: DomainObjectSet<TestVariant> =
        dslScope.objectFactory.domainObjectSet(TestVariant::class.java)

    private val unitTestVariantList: DomainObjectSet<UnitTestVariant> =
        dslScope.objectFactory.domainObjectSet(UnitTestVariant::class.java)

    init {
        sourceSetManager.setUpTestSourceSet(VariantType.ANDROID_TEST_PREFIX)
        sourceSetManager.setUpTestSourceSet(VariantType.UNIT_TEST_PREFIX)
    }

    /**
     * A collection of Android test
     * [build variants](https://developer.android.com/studio/build/build-variants.html)
     *
     * To process elements in this collection, you should use
     * [`all`](https://docs.gradle.org/current/javadoc/org/gradle/api/DomainObjectCollection.html#all-org.gradle.api.Action-).
     * That's because the plugin populates this collection only after
     * the project is evaluated. Unlike the `each` iterator, using `all`
     * processes future elements as the plugin creates them.
     *
     * To learn more about testing Android projects, read
     * [Test your app](https://developer.android.com/studio/test/index.html)
     */
    override val testVariants: DomainObjectSet<TestVariant>
        get() = testVariantList

    fun addTestVariant(testVariant: TestVariant) {
        testVariantList.add(testVariant)
    }

    /**
     * Returns a collection of Android unit test
     * [build variants](https://developer.android.com/studio/build/build-variants.html).
     *
     * To process elements in this collection, you should use
     * [`all`](https://docs.gradle.org/current/javadoc/org/gradle/api/DomainObjectCollection.html#all-org.gradle.api.Action-).
     * That's because the plugin populates this collection only after
     * the project is evaluated. Unlike the `each` iterator, using `all`
     * processes future elements as the plugin creates them.
     *
     * To learn more about testing Android projects, read
     * [Test your app](https://developer.android.com/studio/test/index.html)
     */
    override val unitTestVariants: DomainObjectSet<UnitTestVariant>
        get() = unitTestVariantList

    fun addUnitTestVariant(testVariant: UnitTestVariant) {
        unitTestVariantList.add(testVariant)
    }

    /**
     * Specifies the
     * [build type](https://developer.android.com/studio/build/build-variants.html#build-types)
     * that the plugin should use to test the module.
     *
     * By default, the Android plugin uses the "debug" build type. This means that when you
     * deploy your instrumented tests using `gradlew connectedAndroidTest`, it uses the
     * code and resources from the module's "debug" build type to create the test APK. The plugin
     * then deploys the "debug" version of both the module's APK and the test APK to a connected
     * device, and runs your tests.
     *
     * To change the test build type to something other than "debug", specify it as follows:
     *
     * ```
     * android {
     *     // Changes the test build type for instrumented tests to "stage".
     *     testBuildType "stage"
     * }
     * ```
     *
     * If your module configures
     * [product flavors](https://developer.android.com/studio/build/build-variants.html#product-flavors)
     * the plugin creates a test APK and deploys tests for each build variant that uses
     * the test build type. For example, consider if your module configures "debug" and "release"
     * build types, and "free" and "paid" product flavors. By default, when you run your
     * instrumented tests using `gradlew connectedAndroidTest`, the plugin performs
     * executes the following tasks:
     *
     * * `connectedFreeDebugAndroidTest`: builds and deploys a `freeDebug`
     *       test APK and module APK, and runs instrumented tests for that variant.
     * * `connectedPaidDebugAndroidTest`: builds and deploys a `paidDebug`
     *       test APK and module APK, and runs instrumented tests for that variant.
     *
     * To learn more, read
     * [Create instrumented test for a build variant](https://developer.android.com/studio/test/index.html#create_instrumented_test_for_a_build_variant)
     *
     * **Note:** You can execute `connected<BuildVariant>AndroidTest` tasks
     * only for build variants that use the test build type. So, by default, running
     * `connectedStageAndroidTest` results in the following build error:
     *
     * ```
     * Task 'connectedStageAndroidTest' not found in root project
     * ```
     *
     * You can resolve this issue by changing the test build type to "stage".
     */
    override var testBuildType = "debug"

    fun getMockableAndroidJar(): FileCollection {
        return globalScope.mockableJarArtifact
    }
}

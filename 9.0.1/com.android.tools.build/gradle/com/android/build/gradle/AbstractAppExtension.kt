package com.android.build.gradle

import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfig
import com.android.build.gradle.options.BooleanOption
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectContainer

/**
 * An intermediate implementation class of the previous `android` extension for application and dynamic feature plugins.
 *
 * Replaced by [com.android.build.api.dsl.ApplicationExtension] and [com.android.build.api.dsl.DynamicFeatureExtension] in the application and dynamic-feature plugins respectively.
 */
@Deprecated(
    message = "Replaced by com.android.build.api.dsl.ApplicationExtension and com.android.build.api.dsl.DynamicFeatureExtension.\n" +
            "This class is not used for the public extensions in AGP when android.newDsl=true, which is the default in AGP 9.0, and will be removed in AGP 10.0.",
)
abstract class AbstractAppExtension(
    dslServices: DslServices,
    bootClasspathConfig: BootClasspathConfig,
    buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
    sourceSetManager: SourceSetManager,
    isBaseModule: Boolean,
    stats: GradleBuildProject.Builder?
) : TestedExtension(
    dslServices,
    bootClasspathConfig,
    buildOutputs,
    sourceSetManager,
    isBaseModule,
    stats
) {

    /**
     * Returns a collection of [build variants](https://developer.android.com/studio/build/build-variants.html) that
     * the app project includes.
     *
     *
     * To process elements in this collection, you should use the
     * [`all`](https://docs.gradle.org/current/javadoc/org/gradle/api/DomainObjectCollection.html#all(org.gradle.api.Action))
     * iterator. That's because the plugin populates this collection only after
     * the project is evaluated. Unlike the `each` iterator, using `all`
     * processes future elements as the plugin creates them.
     *
     *
     * The following sample iterates through all `applicationVariants` elements to [
     * inject a build variable into the manifest
     * ](https://developer.android.com/studio/build/manifest-build-variables.html):
     *
     * ```groovy
     * android.applicationVariants.all { variant ->
     *     def mergedFlavor = variant.getMergedFlavor()
     *     // Defines the value of a build variable you can use in the manifest.
     *     mergedFlavor.manifestPlaceholders = [hostName:"www.example.com/${variant.versionName}"]
     * }
     * ```
     */
    val applicationVariants: DomainObjectSet<ApplicationVariant>
        get() {
            recordOldVariantApiUsage()
           return _applicationVariants
        }

    private val _applicationVariants: DomainObjectSet<ApplicationVariant> =
        dslServices.domainObjectSet(ApplicationVariant::class.java)

    override fun addVariant(variant: BaseVariant) {
        if (dslServices.projectOptions[BooleanOption.USE_NEW_DSL]) return
        _applicationVariants.add(variant as ApplicationVariant)
    }
}

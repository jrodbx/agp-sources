package com.android.build.gradle

import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.ProjectOptions
import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectContainer

/**
 * The `android` extension for application plugins.
 *
 *
 * For the base module, see [com.android.build.gradle.BaseExtension]
 *
 *
 * For optional apks, this class is used directly.
 */
abstract class AbstractAppExtension(
    dslScope: DslScope,
    projectOptions: ProjectOptions,
    globalScope: GlobalScope,
    buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
    sourceSetManager: SourceSetManager,
    extraModelInfo: ExtraModelInfo,
    isBaseModule: Boolean
) : TestedExtension(
    dslScope,
    projectOptions,
    globalScope,
    buildOutputs,
    sourceSetManager,
    extraModelInfo,
    isBaseModule
) {

    /**
     * Returns a collection of [build variants](https://developer.android.com/studio/build/build-variants.html) that
     * the app project includes.
     *
     *
     * To process elements in this collection, you should use the [
 * `all`](https://docs.gradle.org/current/javadoc/org/gradle/api/DomainObjectCollection.html#all(org.gradle.api.Action)) iterator. That's because the plugin populates this collection only after
     * the project is evaluated. Unlike the `each` iterator, using `all`
     * processes future elements as the plugin creates them.
     *
     *
     * The following sample iterates through all `applicationVariants` elements to [inject a
 * build variable into the manifest](https://developer.android.com/studio/build/manifest-build-variables.html):
     *
     * <pre>
     * android.applicationVariants.all { variant -&gt;
     * def mergedFlavor = variant.getMergedFlavor()
     * // Defines the value of a build variable you can use in the manifest.
     * mergedFlavor.manifestPlaceholders = [hostName:"www.example.com/${variant.versionName}"]
     * }
    </pre> *
     */
    val applicationVariants: DomainObjectSet<ApplicationVariant> =
        dslScope.objectFactory.domainObjectSet(ApplicationVariant::class.java)

    override fun addVariant(variant: BaseVariant, variantScope: VariantScope) {
        applicationVariants.add(variant as ApplicationVariant)
    }
}

/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.api.variant.impl

import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.Component
import com.android.build.api.component.analytics.AnalyticsEnabledLibraryVariant
import com.android.build.api.component.impl.ConsumableCreationConfigImpl
import com.android.build.api.extension.impl.VariantApiOperationsRegistrar
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.*
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.build.gradle.internal.variant.VariantPathHelper
import com.android.builder.dexing.DexingType
import com.android.builder.model.CodeShrinker
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.Provider
import javax.inject.Inject

open class  LibraryVariantImpl @Inject constructor(
        override val variantBuilder: LibraryVariantBuilderImpl,
        buildFeatureValues: BuildFeatureValues,
        variantDslInfo: VariantDslInfo,
        variantDependencies: VariantDependencies,
        variantSources: VariantSources,
        paths: VariantPathHelper,
        artifacts: ArtifactsImpl,
        variantScope: VariantScope,
        variantData: BaseVariantData,
        transformManager: TransformManager,
        internalServices: VariantPropertiesApiServices,
        taskCreationServices: TaskCreationServices,
        globalScope: GlobalScope
) : VariantImpl(
    variantBuilder,
    buildFeatureValues,
    variantDslInfo,
    variantDependencies,
    variantSources,
    paths,
    artifacts,
    variantScope,
    variantData,
    transformManager,
    internalServices,
    taskCreationServices,
    globalScope
), LibraryVariant, LibraryCreationConfig {

    // ---------------------------------------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------------------------------------

    override val applicationId: Provider<String> =
        internalServices.providerOf(String::class.java, variantDslInfo.packageName)

    override fun <ParamT : InstrumentationParameters> transformClassesWith(
        classVisitorFactoryImplClass: Class<out AsmClassVisitorFactory<ParamT>>,
        scope: InstrumentationScope,
        instrumentationParamsConfig: (ParamT) -> Unit
    ) {
        if (scope == InstrumentationScope.ALL) {
            throw RuntimeException(
                "Can't register ${classVisitorFactoryImplClass.name} to " +
                        "instrument library dependencies.\n" +
                        "Instrumenting library dependencies will have no effect on library " +
                        "consumers, move the dependencies instrumentation to be done in the " +
                        "consuming app or test component."
            )
        }
        super.transformClassesWith(classVisitorFactoryImplClass, scope, instrumentationParamsConfig)
    }

    override val packagingOptions: LibraryPackagingOptions by lazy {
        LibraryPackagingOptionsImpl(globalScope.extension.packagingOptions, internalServices)
    }

    override fun packagingOptions(action: LibraryPackagingOptions.() -> Unit) {
        action.invoke(packagingOptions)
    }

    // ---------------------------------------------------------------------------------------------
    // INTERNAL API
    // ---------------------------------------------------------------------------------------------

    private val delegate by lazy { ConsumableCreationConfigImpl(this, globalScope, variantDslInfo) }

    override val dexingType: DexingType
        get() = delegate.dexingType

    override val debuggable: Boolean
        get() = variantDslInfo.isDebuggable

    override fun <T : Component> createUserVisibleVariantObject(
            projectServices: ProjectServices,
            operationsRegistrar: VariantApiOperationsRegistrar<VariantBuilder, Variant>,
            stats: GradleBuildVariant.Builder
    ): T =
            projectServices.objectFactory.newInstance(
                    AnalyticsEnabledLibraryVariant::class.java,
                    this,
                    stats
            ) as T

    override val codeShrinker: CodeShrinker?
        get() = delegate.getCodeShrinker()

    override fun getNeedsMergedJavaResStream(): Boolean = delegate.getNeedsMergedJavaResStream()

    override fun getJava8LangSupportType(): VariantScope.Java8LangSupport = delegate.getJava8LangSupportType()

    override val needsShrinkDesugarLibrary: Boolean
        get() = delegate.needsShrinkDesugarLibrary

    override val minSdkVersionWithTargetDeviceApi: AndroidVersion
        get() = delegate.minSdkVersionWithTargetDeviceApi
}

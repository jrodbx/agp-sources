/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.methods

import com.android.build.gradle.internal.tasks.mlkit.codegen.ClassNames
import com.android.tools.mlkit.MlNames
import com.android.tools.mlkit.TensorInfo
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier

/**
 * Injector to inject getter method when there is no metadata.
 */
class NoMetadataGetMethodInjector : MethodInjector() {
    override fun inject(classBuilder: TypeSpec.Builder, tensorInfo: TensorInfo) {
        val defaultType = ClassNames.TENSOR_BUFFER
        val methodSpecBuilder = MethodSpec.methodBuilder(
            MlNames.formatGetterName(
                tensorInfo.identifierName, ClassNames.TENSOR_BUFFER.simpleName()
            )
        )
            .addModifiers(Modifier.PUBLIC)
            .returns(defaultType)
            .addAnnotation(ClassNames.NON_NULL)
            .addStatement("return \$L", tensorInfo.identifierName)

        classBuilder.addMethod(methodSpecBuilder.build())
    }
}
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
import com.android.build.gradle.internal.tasks.mlkit.codegen.getOutputParameterType
import com.android.build.gradle.internal.tasks.mlkit.codegen.getProcessorName
import com.android.tools.mlkit.MlNames
import com.android.tools.mlkit.TensorInfo
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier

/**
 * Injector to inject default implementation for getter method, which returns [java.nio.ByteBuffer]
 * and assume data type has method `getBuffer`.
 */
class DefaultGetMethodInjector : MethodInjector() {
    override fun inject(classBuilder: TypeSpec.Builder, tensorInfo: TensorInfo) {
        val defaultType = ClassNames.TENSOR_BUFFER
        val advancedType = getOutputParameterType(tensorInfo)
        val methodSpecBuilder = MethodSpec.methodBuilder(
            MlNames.formatGetterName(
                tensorInfo.identifierName, defaultType.simpleName()
            )
        )
            .addModifiers(Modifier.PUBLIC)
            .returns(defaultType)
            .addAnnotation(ClassNames.NON_NULL)
        if (advancedType == ClassNames.TENSOR_IMAGE) {
            methodSpecBuilder.addStatement(
                "return \$L.process(\$L).getTensorBuffer()",
                getProcessorName(tensorInfo),
                tensorInfo.identifierName
            )
        } else {
            methodSpecBuilder.addStatement(
                "return \$L.process(\$L)",
                getProcessorName(tensorInfo),
                tensorInfo.identifierName
            )
        }

        classBuilder.addMethod(methodSpecBuilder.build())
    }
}
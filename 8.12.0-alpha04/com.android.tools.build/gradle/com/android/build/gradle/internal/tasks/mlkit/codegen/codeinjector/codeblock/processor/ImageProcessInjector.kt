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
package com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.processor

import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.CodeBlockInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.getImageHeightFieldName
import com.android.build.gradle.internal.tasks.mlkit.codegen.getImageWidthFieldName
import com.android.build.gradle.internal.tasks.mlkit.codegen.getParameterType
import com.android.build.gradle.internal.tasks.mlkit.codegen.getProcessedTypeName
import com.android.build.gradle.internal.tasks.mlkit.codegen.getProcessorName
import com.android.tools.mlkit.TensorInfo
import com.squareup.javapoet.MethodSpec

/** Injector to inject image process code.  */
class ImageProcessInjector : CodeBlockInjector() {
    override fun inject(methodBuilder: MethodSpec.Builder, tensorInfo: TensorInfo) {
        methodBuilder.addStatement(
            "\$L = \$L.getHeight()",
            getImageHeightFieldName(tensorInfo),
            tensorInfo.identifierName
        )
        methodBuilder.addStatement(
            "\$L = \$L.getWidth()",
            getImageWidthFieldName(tensorInfo),
            tensorInfo.identifierName
        )

        val typeName = getParameterType(tensorInfo)
        val processedTypeName = getProcessedTypeName(tensorInfo)
        methodBuilder.addStatement(
            "\$T \$L = \$L.process(\$L)",
            typeName,
            processedTypeName,
            getProcessorName(tensorInfo),
            tensorInfo.identifierName
        )
    }
}
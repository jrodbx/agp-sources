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

import com.android.build.gradle.internal.tasks.mlkit.codegen.ClassNames
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock.CodeBlockInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.getDataType
import com.android.build.gradle.internal.tasks.mlkit.codegen.getFloatArrayString
import com.android.build.gradle.internal.tasks.mlkit.codegen.getProcessorBuilderName
import com.android.build.gradle.internal.tasks.mlkit.codegen.getProcessorName
import com.android.tools.mlkit.TensorInfo
import com.squareup.javapoet.MethodSpec

/**
 * Injector to init a image preprocessor, which does image resize, normalization, quantization and
 * cast.
 */
class ImagePreprocessorInitInjector : CodeBlockInjector() {
    override fun inject(methodBuilder: MethodSpec.Builder, tensorInfo: TensorInfo) {
        methodBuilder.addCode(
            "\$T.Builder \$L = new \$T.Builder()\n",
            ClassNames.IMAGE_PROCESSOR,
            getProcessorBuilderName(tensorInfo),
            ClassNames.IMAGE_PROCESSOR
        )
        methodBuilder.addCode(
            "  .add(new \$T(\$L, \$L, \$T.NEAREST_NEIGHBOR))\n",
            ClassNames.RESIZE_OP,
            tensorInfo.shape[1],
            tensorInfo.shape[2],
            ClassNames.RESIZE_METHOD
        )
        val normalizationParams = tensorInfo.normalizationParams
        methodBuilder.addCode(
            "  .add(new \$T(\$L, \$L))\n",
            ClassNames.NORMALIZE_OP,
            getFloatArrayString(normalizationParams.mean),
            getFloatArrayString(normalizationParams.std)
        )
        val quantizationParams =
            tensorInfo.quantizationParams
        methodBuilder.addCode(
            "  .add(new \$T(\$Lf, \$Lf))\n",
            ClassNames.QUANTIZE_OP,
            quantizationParams.zeroPoint,
            quantizationParams.scale
        )
        methodBuilder.addCode(
            "  .add(new \$T(\$T.\$L));\n",
            ClassNames.CAST_OP,
            ClassNames.DATA_TYPE,
            getDataType(tensorInfo.dataType)
        )
        methodBuilder.addStatement(
            "\$L = \$L.build()",
            getProcessorName(tensorInfo),
            getProcessorBuilderName(tensorInfo)
        )
    }
}
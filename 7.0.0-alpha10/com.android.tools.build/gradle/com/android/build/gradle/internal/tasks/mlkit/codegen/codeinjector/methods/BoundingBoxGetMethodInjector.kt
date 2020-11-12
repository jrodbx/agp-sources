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

import com.android.annotations.Nullable
import com.android.build.gradle.internal.tasks.mlkit.codegen.ClassNames
import com.android.build.gradle.internal.tasks.mlkit.codegen.getImageHeightFieldName
import com.android.build.gradle.internal.tasks.mlkit.codegen.getImageWidthFieldName
import com.android.build.gradle.internal.tasks.mlkit.codegen.getIntArrayString
import com.android.build.gradle.internal.tasks.mlkit.codegen.getOutputParameterType
import com.android.build.gradle.internal.tasks.mlkit.codegen.getOutputParameterTypeName
import com.android.build.gradle.internal.tasks.mlkit.codegen.getProcessorName
import com.android.tools.mlkit.MlNames
import com.android.tools.mlkit.ModelInfo
import com.android.tools.mlkit.TensorInfo
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier

/**
 * Injects a getter method to get list of bounding box that represented by android.graphics.RectF.
 */
class BoundingBoxGetMethodInjector(private val modelInfo : ModelInfo) : MethodInjector() {

    override fun inject(classBuilder: TypeSpec.Builder, tensorInfo: TensorInfo) {
        val returnType = getOutputParameterType(tensorInfo)
        val targetedImageTensor = getTargetedImageTensor() ?: return
        val methodName = MlNames.formatGetterName(
            tensorInfo.identifierName, getOutputParameterTypeName(tensorInfo)
        )
        val methodSpec = MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PRIVATE)
            .returns(returnType)
            .addAnnotation(ClassNames.NON_NULL)
            .addStatement("\$T originalBoxes = \$T.convert(\$L, \$L, \$L, \$T.Type.\$L, \$T.CoordinateType.\$L, \$L, \$L)",
                ClassNames.RECTF_LIST,
                ClassNames.BOUNDING_BOX_UTIL,
                tensorInfo.identifierName,
                tensorInfo.boundingBoxProperties?.index?.let { getIntArrayString(it) },
                tensorInfo.contentRange.min,
                ClassNames.BOUNDING_BOX_UTIL,
                tensorInfo.boundingBoxProperties?.type.toString(),
                ClassNames.BOUNDING_BOX_UTIL,
                tensorInfo.boundingBoxProperties?.coordinateType.toString(),
                targetedImageTensor.shape[1],
                targetedImageTensor.shape[2]
            )
            .addStatement("\$T processedBoxes = new \$T<>()", ClassNames.RECTF_LIST, ClassNames.ARRAY_LIST)
            .beginControlFlow("for (\$L box : originalBoxes)", ClassNames.RECT_F)
            .addStatement("processedBoxes.add(\$L.inverseTransform(box, \$L, \$L))",
                getProcessorName(targetedImageTensor),
                getImageHeightFieldName(targetedImageTensor),
                getImageWidthFieldName(targetedImageTensor))
            .endControlFlow()
            .addStatement("return processedBoxes")
            .build()

        classBuilder.addMethod(methodSpec)
    }

    @Nullable
    private fun getTargetedImageTensor(): TensorInfo? {
        // Only support one TensorImage in input for now, so get first RGB image.
        for (tensorInfo in modelInfo!!.inputs) {
            if (tensorInfo.isRGBImage) {
                return tensorInfo
            }
        }

        return null
    }
}
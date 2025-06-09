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

import com.android.build.gradle.internal.tasks.mlkit.codegen.ClassMetadata
import com.android.build.gradle.internal.tasks.mlkit.codegen.ClassNames
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.CodeInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.getOutputParameterTypeName
import com.android.tools.mlkit.MlNames
import com.android.tools.mlkit.ModelInfo
import com.android.tools.mlkit.TensorInfo
import com.android.utils.usLocaleCapitalize
import com.squareup.javapoet.ArrayTypeName
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier

/** Injects getter methods for tensor groups. It will generate code with following format:
 * <pre>
 *     public List<TensorGroupObject> getTensorGroupNameList {
 *         // If type1 has advanced support, we can get a list
 *         List<Type1> tensor1 = getTensor1AsType1List();
 *         // If not, we get a TensorBuffer and convert it to array
 *         float[] tensor2 = getTensor2AsTensorBuffer().asFloatArray();
 *
 *         // ...asserts all tensor has same size here..
 *
 *         List<TensorGroupObject> results = new ArrayList<>();
 *         for (int i = 0; i < tensor1.size(); i++) {
 *             results.add(new TensorGroupObject(tensor1.get(i), tensor2[i]));
 *         }
 *
 *         return results;
 *     }
 *
 * </pre>
 */
class GroupGetMethodInjector(private val metadata: ClassMetadata) :
    CodeInjector<TypeSpec.Builder, ModelInfo> {
    override fun inject(classBuilder: TypeSpec.Builder, modelInfo: ModelInfo) {
        for (tensorGroupInfo in modelInfo.outputTensorGroups) {
            val outputType: TypeName = ClassName.get(metadata.packageName, metadata.className)
                .nestedClass(tensorGroupInfo.identifierName.usLocaleCapitalize())
            val outputListType: TypeName = ParameterizedTypeName.get(ClassNames.LIST, outputType)
            val methodSpecBuilder = MethodSpec.methodBuilder(
                MlNames.formatGroupGetterName(tensorGroupInfo.identifierName)
            )
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassNames.NON_NULL)
                .returns(outputListType)

            val tensorInfos: List<TensorInfo> =
                modelInfo.outputs.filter { tensorGroupInfo.tensorNames.contains(it.name) }

            for (tensorInfo in tensorInfos) {
                methodSpecBuilder.addStatement(
                    "\$T \$L = \$L",
                    getParameterType(tensorInfo),
                    tensorInfo.identifierName,
                    getTensorInitStatement(tensorInfo)
                )
            }

            //TODO(b/155690627): Add assertion here to check all tensors has same size.

            methodSpecBuilder.addStatement(
                "\$T \$L = new \$T<>()",
                outputListType,
                tensorGroupInfo.identifierName,
                ClassNames.ARRAY_LIST
            )
                .beginControlFlow(
                    "for (int i = 0; i < \$L; i++)",
                    getTensorSizeInitStatement(tensorInfos[0])
                )
                .addStatement(
                    "\$L.add(new \$T(\$L))",
                    tensorGroupInfo.identifierName,
                    outputType,
                    getGroupParameterListStatement(tensorInfos)
                )
                .endControlFlow()
                .addStatement("return \$L", tensorGroupInfo.identifierName)

            classBuilder.addMethod(methodSpecBuilder.build())
        }
    }

    private companion object {
        fun getParameterType(tensorInfo: TensorInfo): TypeName {
            return if (tensorInfo.contentType == TensorInfo.ContentType.BOUNDING_BOX) {
                ClassNames.RECTF_LIST
            } else if (tensorInfo.fileType == TensorInfo.FileType.TENSOR_VALUE_LABELS) {
                ClassNames.STRING_LIST
            } else if (tensorInfo.dataType == TensorInfo.DataType.FLOAT32) {
                ArrayTypeName.of(TypeName.FLOAT)
            } else {
                ArrayTypeName.of(TypeName.INT)
            }
        }

        fun getTensorInitStatement(tensorInfo: TensorInfo): String {
            val stringBuilder = StringBuilder(
                MlNames.formatGetterName(
                    tensorInfo.identifierName, getOutputParameterTypeName(tensorInfo) + "()"
                )
            )
            if (!isParameterArrayList(tensorInfo)) {
                if (tensorInfo.dataType == TensorInfo.DataType.FLOAT32) {
                    stringBuilder.append(".getFloatArray()")
                } else {
                    stringBuilder.append(".getIntArray()")
                }
            }

            return stringBuilder.toString()
        }

        fun getTensorSizeInitStatement(tensorInfo: TensorInfo): String {
            val stringBuilder = StringBuilder(tensorInfo.identifierName)
            if (isParameterArrayList(tensorInfo)) {
                stringBuilder.append(".size()")
            } else {
                stringBuilder.append(".length")
            }
            return stringBuilder.toString()
        }

        fun isParameterArrayList(tensorInfo: TensorInfo): Boolean {
            return tensorInfo.contentType == TensorInfo.ContentType.BOUNDING_BOX
                    || tensorInfo.fileType == TensorInfo.FileType.TENSOR_VALUE_LABELS
        }

        fun getGroupParameterListStatement(tensorInfos: List<TensorInfo>): String {
            val stringBuilder = StringBuilder()
            for (tensorInfo in tensorInfos) {
                if (isParameterArrayList(tensorInfo)) {
                    stringBuilder.append(String.format("%s.get(i), ", tensorInfo.identifierName))
                } else {
                    stringBuilder.append(String.format("%s[i], ", tensorInfo.identifierName))
                }
            }
            stringBuilder.deleteCharAt(stringBuilder.length - 2)

            return stringBuilder.toString()
        }
    }
}
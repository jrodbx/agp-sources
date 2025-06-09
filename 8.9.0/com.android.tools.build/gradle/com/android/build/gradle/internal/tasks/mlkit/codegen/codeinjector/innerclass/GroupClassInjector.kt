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
package com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.innerclass

import com.android.build.gradle.internal.tasks.mlkit.codegen.ClassNames
import com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.CodeInjector
import com.android.build.gradle.internal.tasks.mlkit.codegen.getGroupClassParameterType
import com.android.tools.mlkit.MlNames
import com.android.tools.mlkit.ModelInfo
import com.android.tools.mlkit.TensorInfo
import com.android.utils.usLocaleCapitalize
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier

/** Injector to inject inner class to represent tensor group. It generates class with following format:
 * <pre>
 *    public class TensorGroupName {
 *        private Type tensor1;
 *        private TensorGroup(Type tensor1) {
 *            this.tensor1 = tensor1;
 *        }
 *
 *        public Type getTensor1AsType() {
 *            return tensor1;
 *        }
 *
 *        // More getters for other tensors if exist.
 *        // ...
 *    }
 * </pre>
 */
class GroupClassInjector : CodeInjector<TypeSpec.Builder, ModelInfo> {
    override fun inject(classBuilder: TypeSpec.Builder, modelInfo: ModelInfo) {
        for (tensorGroupInfo in modelInfo.outputTensorGroups) {
            val builder = TypeSpec.classBuilder(tensorGroupInfo.identifierName.usLocaleCapitalize())
            builder.addModifiers(Modifier.PUBLIC)

            val tensorInfos: List<TensorInfo> =
                modelInfo.outputs.filter { tensorGroupInfo.tensorNames.contains(it.name) }

            // Add constructor.
            val constructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
            for (tensorInfo in tensorInfos) {
                constructorBuilder.addParameter(getGroupClassParameterType(tensorInfo), tensorInfo.identifierName)
                constructorBuilder.addStatement("this.\$L = \$L", tensorInfo.identifierName, tensorInfo.identifierName)
            }
            builder.addMethod(constructorBuilder.build())

            // Add data fields.
            for (tensorInfo in tensorInfos) {
                val fieldSpec =
                    FieldSpec.builder(getGroupClassParameterType(tensorInfo), tensorInfo.identifierName)
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .build()
                builder.addField(fieldSpec)
            }

            // Add getter methods
            for (tensorInfo in tensorInfos) {
                val methodSpecBuilder = MethodSpec.methodBuilder(
                    MlNames.formatGetterName(
                        tensorInfo.identifierName, getTypeName(tensorInfo)
                    )
                )
                    .addModifiers(Modifier.PUBLIC)
                    .returns(getGroupClassParameterType(tensorInfo))
                    .addAnnotation(ClassNames.NON_NULL)
                    .addStatement("return \$L", tensorInfo.identifierName)
                builder.addMethod(methodSpecBuilder.build())
            }

            classBuilder.addType(builder.build())
        }
    }

    private companion object {
        fun getTypeName(tensorInfo: TensorInfo): String {
            return when {
                tensorInfo.fileType == TensorInfo.FileType.TENSOR_VALUE_LABELS -> "String"
                tensorInfo.contentType == TensorInfo.ContentType.BOUNDING_BOX -> "RectF"
                tensorInfo.dataType == TensorInfo.DataType.FLOAT32 -> "Float"
                else -> "Int"
            }
        }
    }
}
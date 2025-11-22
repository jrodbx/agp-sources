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
package com.android.build.gradle.internal.tasks.mlkit.codegen.codeinjector.codeblock

import com.android.build.gradle.internal.tasks.mlkit.codegen.ClassNames
import com.android.build.gradle.internal.tasks.mlkit.codegen.getIdentifierFromFileName
import com.android.tools.mlkit.TensorInfo
import com.google.common.base.Strings
import com.squareup.javapoet.MethodSpec

/**
 * Injector to load associated file. It will create code like:
 *
 * <pre>file = FileUtil.loadLabels(extractor.getAssociatedFile(fileName))</pre>
 */
class AssociatedFileInjector : CodeBlockInjector() {
    override fun inject(methodBuilder: MethodSpec.Builder, tensorInfo: TensorInfo) {
        if (!Strings.isNullOrEmpty(tensorInfo.fileName)) {
            methodBuilder.addStatement(
                "\$L = \$T.loadLabels(extractor.getAssociatedFile(\$S))",
                getIdentifierFromFileName(tensorInfo.fileName),
                ClassNames.FILE_UTIL,
                tensorInfo.fileName
            )
        }
    }
}
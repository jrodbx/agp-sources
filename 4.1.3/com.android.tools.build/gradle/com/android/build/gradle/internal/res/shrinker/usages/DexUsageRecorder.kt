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

package com.android.build.gradle.internal.res.shrinker.usages

import com.android.SdkConstants.DOT_DEX
import com.android.build.gradle.internal.res.shrinker.ResourceShrinkerModel
import com.android.build.gradle.internal.res.shrinker.obfuscation.ClassAndMethod
import com.android.build.gradle.internal.res.shrinker.usages.AppCompat.isAppCompatClass
import com.android.builder.dexing.AnalysisCallback
import com.android.builder.dexing.runResourceShrinkerAnalysis
import com.android.ide.common.resources.usage.ResourceUsageModel
import com.android.resources.ResourceType
import java.nio.file.Files
import java.nio.file.Path

/**
 * Records resource usages, detects usages of WebViews and {@code Resources#getIdentifier},
 * gathers string constants from compiled .dex files.
 *
 * @param root directory starting from which all .dex files are analyzed.
 */
class DexUsageRecorder(val root: Path) : ResourceUsageRecorder {

    override fun recordUsages(model: ResourceShrinkerModel) {
        // Record resource usages from dex classes. The following cases are covered:
        // 1. Integer constant which refers to resource id.
        // 2. Reference to static field in R classes.
        // 3. Usages of android.content.res.Resources.getIdentifier(...) and
        //    android.webkit.WebView.load...
        // 4. All strings which might be used to reference resources by name via
        //    Resources.getIdentifier.

        Files.walk(root)
            .filter { Files.isRegularFile(it) }
            .filter { it.toString().endsWith(DOT_DEX, ignoreCase = true) }
            .forEach { path ->
                runResourceShrinkerAnalysis(
                    Files.readAllBytes(path),
                    path,
                    DexFileAnalysisCallback(path, model)
                )
            }
    }
}

private class DexFileAnalysisCallback(
    private val path: Path,
    private val model: ResourceShrinkerModel
) : AnalysisCallback {
    companion object {
        const val ANDROID_RES = "android_res/"

        private fun String.toSourceClassName(): String {
            return this.replace('/', '.')
        }
    }

    override fun shouldProcess(internalName: String): Boolean =
        !isResourceClass(internalName)

    /** Returns whether the given class file name points to an aapt-generated compiled R class. */
    fun isResourceClass(internalName: String): Boolean {
        val realClassName =
            model.obfuscatedClasses.resolveOriginalClass(internalName.toSourceClassName())
        val lastPart = realClassName.substringAfterLast('.')
        if (lastPart.startsWith("R$")) {
            val typeName = lastPart.substring(2)
            return ResourceType.fromClassName(typeName) != null
        }
        return false
    }

    override fun referencedInt(value: Int) {
        val resource = model.usageModel.getResource(value)
        if (ResourceUsageModel.markReachable(resource)) {
            model.debugReporter.debug {
                "Marking $resource reachable: referenced from $path"
            }
        }
    }

    override fun referencedStaticField(internalName: String, fieldName: String) {
        val realMethod = model.obfuscatedClasses.resolveOriginalMethod(
            ClassAndMethod(internalName.toSourceClassName(), fieldName)
        )

        if (isValidResourceType(realMethod.className)) {
            val typePart = realMethod.className.substringAfterLast('$')
            ResourceType.fromClassName(typePart)?.let { type ->
                model.usageModel.getResource(type, realMethod.methodName)?.let {
                    ResourceUsageModel.markReachable(it)
                }
            }
        }
    }

    override fun referencedString(value: String) {
        // See if the string is at all eligible; ignore strings that aren't identifiers (has java
        // identifier chars and nothing but .:/), or are empty or too long.
        // We also allow "%", used for formatting strings.
        if (value.isEmpty() || value.length > 80) {
            return
        }
        fun isSpecialCharacter(c: Char) = c == '.' || c == ':' || c == '/' || c == '%'

        if (value.all { Character.isJavaIdentifierPart(it) || isSpecialCharacter(it) } &&
            value.any { Character.isJavaIdentifierPart(it) }) {
            model.addStringConstant(value)
            model.isFoundWebContent = model.isFoundWebContent || value.contains(ANDROID_RES)
        }
    }

    override fun referencedMethod(
        internalName: String,
        methodName: String,
        methodDescriptor: String
    ) {
        if (internalName == "android/content/res/Resources" &&
            methodName == "getIdentifier" &&
            methodDescriptor == "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I"
        ) {
            // "benign" usages: don't trigger reflection mode just because the user has included
            // appcompat
            if (isAppCompatClass(internalName.toSourceClassName(), model.obfuscatedClasses)) {
                return
            }
            model.isFoundGetIdentifier = true
            // TODO: Check previous instruction and see if we can find a literal String; if so, we
            // can more accurately dispatch the resource here rather than having to check the whole
            // string pool!
        }
        if (internalName == "android/webkit/WebView" && methodName.startsWith("load")) {
            model.isFoundWebContent = true
        }
    }

    private fun isValidResourceType(className: String): Boolean =
        className.substringAfterLast('.').startsWith("R$")
}

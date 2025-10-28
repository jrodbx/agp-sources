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

package com.android.build.shrinker.obfuscation

import com.google.common.collect.ImmutableMap

/** Contains mappings between obfuscated classes and methods to original ones. */
class ObfuscatedClasses private constructor(builder: Builder) {

    companion object {
        @JvmField
        val NO_OBFUSCATION = Builder().build()
    }

    private val obfuscatedClasses = ImmutableMap.copyOf(builder.obfuscatedClasses)
    private val obfuscatedMethods = ImmutableMap.copyOf(builder.obfuscatedMethods)

    fun resolveOriginalMethod(obfuscatedMethod: ClassAndMethod): ClassAndMethod {
        return obfuscatedMethods.getOrElse(obfuscatedMethod) {
            val realClassName = obfuscatedClasses[obfuscatedMethod.className] ?: obfuscatedMethod.className
            ClassAndMethod(realClassName, obfuscatedMethod.methodName)
        }
    }

    fun resolveOriginalClass(obfuscatedClass: String): String {
        return obfuscatedClasses[obfuscatedClass] ?: obfuscatedClass
    }

    /**
     * Builder that allows to build obfuscated mappings in a way when next method mapping is added
     * to previous class mapping. Example:
     * builder
     *   .addClassMapping(Pair(classA, obfuscatedClassA))
     *   .addMethodMapping(Pair(classAMethod1, obfuscatedClassAMethod1))
     *   .addMethodMapping(Pair(classAMethod2, obfuscatedClassAMethod2))
     *   .addClassMapping(Pair(classB, obfuscatedClassB))
     *   .addMethodMapping(Pair(classBMethod1, obfuscatedClassBMethod1))
     */
    class Builder {

        val obfuscatedClasses: MutableMap<String, String> = mutableMapOf()
        val obfuscatedMethods: MutableMap<ClassAndMethod, ClassAndMethod> = mutableMapOf()

        var currentClassMapping: Pair<String, String>? = null

        /**
         * Adds class mapping: original class name -> obfuscated class name.
         *
         * @param mapping Pair(originalClassName, obfuscatedClassName)
         */
        fun addClassMapping(mapping: Pair<String, String>): Builder {
            currentClassMapping = mapping
            obfuscatedClasses += Pair(mapping.second, mapping.first)
            return this
        }

        /**
         * Adds method mapping: original method name -> obfuscated method name to the latest added
         * class mapping.
         *
         * @param mapping Pair(originalMethodName, obfuscatedMethodName)
         */
        fun addMethodMapping(mapping: Pair<String, String>): Builder {
            if (currentClassMapping != null) {
                obfuscatedMethods += Pair(
                    ClassAndMethod(currentClassMapping!!.second, mapping.second),
                    ClassAndMethod(currentClassMapping!!.first, mapping.first)
                )
            }
            return this
        }

        fun build(): ObfuscatedClasses =
            ObfuscatedClasses(this)
    }
}

data class ClassAndMethod(val className: String, val methodName: String)

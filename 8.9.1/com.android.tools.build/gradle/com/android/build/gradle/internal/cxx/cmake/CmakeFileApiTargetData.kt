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

package com.android.build.gradle.internal.cxx.cmake


/**
 * Closed messages for stream from target-{hash}.json file.
 * This is where source files and flags arrive.
 */
sealed class TargetDataItem {
    /**
     * "path" : "/project/build/intermediates/cmake/debug/obj/x86_64/libhello-jni.so"
     */
    data class Artifacts(val targetId : String, val artifacts : List<String>) : TargetDataItem()

    /**
     *  "compileCommandFragments" : [{
     *    "fragment" : "-g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security -DTEST_C_FLAG -DTEST_C_FLAG_2 -O0 -fno-limit-debug-info  -fPIC  "
     *   }],
     *  "defines" : [{
     *    "define" : "hello_jni_EXPORTS"
     *  }],
     *  "language" : "C",
     *  "sourceIndexes" : [0],
     *  "sysroot" : {
     *    "path" : "/sdk/ndk/21.3.6528147/toolchains/llvm/prebuilt/darwin-x86_64/sysroot"
     *  }
     */
    data class CompileGroups(val compileGroups : List<TargetCompileGroupData>) : TargetDataItem()

    /**
     *  "backtrace" : 2,
     *  "id" : "foo::@6890427a1f51a3e7e1df"
     */
    data class Dependencies(val targetId: String, val dependencyIds: List<String>) : TargetDataItem()

    /**
     *  "name" : "Source Files",
     *  "sourceIndexes" : [0,2,4,6,9,11,13,15,17]
     */
    data class SourceGroups(val sourceGroups : List<String>) : TargetDataItem()

    /**
     *  "build" : "Externals/cpp-optparse",
     *  "source" : "Externals/cpp-optparse"
     */
    data class Paths(val build : String, val source : String) : TargetDataItem()

    /**
     * "backtrace" : 1,
     * "compileGroupIndex" : 0,
     * "path" : "src/main/cxx/hello-jni.c",
     * "sourceGroupIndex" : 0
     *
     * compileGroupIndex can be missing (null). This usually indicates a .h source file.
     */
    data class Source(
            val compileGroupIndex : Int?,
            val sourceGroupIndex : Int,
            val path : String
    ) : TargetDataItem()

    /**
     * "type" : "SHARED_LIBRARY"
     */
    data class Type(val type : String) : TargetDataItem()

    /**
     *  "commandFragments" : [ {
     *    "fragment" : "-Wl,--exclude-libs,libgcc.a -Wl,--exclude-libs,libgcc_real.a ...",
     *    "role" : "flags"
     *  }, { "fragment" : "-llog",
     *    "role" : "libraries"
     *  }, {
     *    "backtrace" : 2,
     *    "fragment" : "/root/jetified-curl/prefab/modules/curl/libs/android.x86_64/libcurl.so",
     *    "role" : "libraries"
     *  } ],
     *  "language" : "CXX",
     *  "sysroot" : {
     *     "path" : "/ndk/21.3.6528147/toolchains/llvm/prebuilt/darwin-x86_64/sysroot
     *   }
     */
    data class Link(
            val targetId : String,
            val compileCommandFragments : List<CommandFragmentData>,
            val language : String,
            val sysroot : String) : TargetDataItem()

}

data class TargetCompileGroupData(
        val compileCommandFragments : List<String>,
        val defines : List<String>,
        val includes : List<String>,
        val language : String, // ex "C"
        val sysroot : String
)

data class CommandFragmentData(val fragment : String, val role : String)

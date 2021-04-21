/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.DexPackagingOptions
import com.android.build.api.dsl.JniLibsPackagingOptions
import com.android.build.api.dsl.ResourcesPackagingOptions
import com.android.build.gradle.internal.packaging.defaultExcludes
import com.android.build.gradle.internal.packaging.defaultMerges
import com.android.build.gradle.internal.services.DslServices
import org.gradle.api.Action
import javax.inject.Inject

/**
 * DSL object for configuring APK packaging options.
 *
 * <p>Packaging options are configured with three sets of paths: first-picks, merges and excludes:
 *
 * <dl>
 *   <dt>First-pick
 *   <dd>Paths that match a first-pick pattern will be selected into the APK. If more than one path
 *       matches the first-pick, only the first found will be selected.
 *   <dt>Merge
 *   <dd>Paths that match a merge pattern will be concatenated and merged into the APK. When merging
 *       two files, a newline will be appended to the end of the first file, if it doesn't end with
 *       a newline already. This is done for all files, regardless of the type of contents.
 *   <dt>Exclude
 *   <dd>Paths that match an exclude pattern will not be included in the APK.
 * </dl>
 *
 * To decide the action on a specific path, the following algorithm is used:
 *
 * <ol>
 *   <li>If any of the first-pick patterns match the path and that path has not been included in the
 *       FULL_APK, add it to the FULL_APK.
 *   <li>If any of the first-pick patterns match the path and that path has already been included in
 *       the FULL_APK, do not include the path in the FULL_APK.
 *   <li>If any of the merge patterns match the path and that path has not been included in the APK,
 *       add it to the APK.
 *   <li>If any of the merge patterns match the path and that path has already been included in the
 *       FULL_APK, concatenate the contents of the file to the ones already in the FULL_APK.
 *   <li>If any of the exclude patterns match the path, do not include it in the APK.
 *   <li>If none of the patterns above match the path and the path has not been included in the APK,
 *       add it to the APK.
 *   <li>Id none of the patterns above match the path and the path has been included in the APK,
 *       fail the build and signal a duplicate path error.
 * </ol>
 *
 * <p>Patterns in packaging options are specified as globs following the syntax in the <a
 * href="https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileSystem.html#getPathMatcher-java.lang.String-">
 * Java Filesystem API</a>. All paths should be configured using forward slashes ({@code /}).
 *
 * <p>All paths to be matched are provided as absolute paths from the root of the apk archive. So,
 * for example, {@code classes.dex} is matched as {@code /classes.dex}. This allows defining
 * patterns such as <code>&#042;&#042;/foo</code> to match the file {@code foo} in any directory,
 * including the root. Any pattern that does not start with a forward slash (or wildcard) is
 * automatically prepended with a forward slash. So, {@code file} and {@code /file} are effectively
 * the same pattern.
 *
 * <p>The default values are:
 *
 * <ul>
 *   <li>Pick first: none
 *   <li>Merge: <code>/META-INF/services/&#042;&#042;</code>
 *   <li>Exclude:
 *       <ul>
 *         <li>{@code /META-INF/LICENSE}
 *         <li>{@code /META-INF/LICENSE.txt}
 *         <li>{@code /META-INF/NOTICE}
 *         <li>{@code /META-INF/NOTICE.txt}
 *         <li>{@code /LICENSE}
 *         <li>{@code /LICENSE.txt}
 *         <li>{@code /NOTICE}
 *         <li>{@code /NOTICE.txt}
 *         <li><code>/META-INF/&#042;.DSA</code> (all DSA signature files)
 *         <li><code>/META-INF/&#042;.EC</code> (all EC signature files)
 *         <li><code>/META-INF/&#042;.SF</code> (all signature files)
 *         <li><code>/META-INF/&#042;.RSA</code> (all RSA signature files)
 *         <li><code>/META-INF/maven/&#042;&#042;</code> (all files in the {@code maven} meta inf
 *             directory)
 *         <li><code>/META-INF/proguard/&#042;</code> (all files in the {@code proguard} meta inf
 *             directory)
 *         <li><code>&#042;&#042;/.svn/&#042;&#042;</code> (all {@code .svn} directory contents)
 *         <li><code>&#042;&#042;/CVS/&#042;&#042;</code> (all {@code CVS} directory contents)
 *         <li><code>&#042;&#042;/SCCS/&#042;&#042;</code> (all {@code SCCS} directory contents)
 *         <li><code>&#042;&#042;/.&#042;</code> (all UNIX hidden files)
 *         <li><code>&#042;&#042;/.&#042;/&#042;&#042;</code> (all contents of UNIX hidden
 *             directories)
 *         <li><code>&#042;&#042;/&#042;~</code> (temporary files)
 *         <li><code>&#042;&#042;/thumbs.db</code>
 *         <li><code>&#042;&#042;/picasa.ini</code>
 *         <li><code>&#042;&#042;/protobuf.meta</code>
 *         <li><code>&#042;&#042;/about.html</code>
 *         <li><code>&#042;&#042;/package.html</code>
 *         <li><code>&#042;&#042;/overview.html</code>
 *         <li><code>&#042;&#042;/_&#042;</code>
 *         <li><code>&#042;&#042;/_&#042;/&#042;&#042;</code>
 *         <li><code>&#042;&#042;/&#042;.kotlin_metadata</code> (kotlin metadata)
 *       </ul>
 * </ul>
 *
 * <p>Example that adds the first {@code anyFileWillDo} file found and ignores all the others and
 * that excludes anything inside a {@code secret-data} directory that exists in the root:
 *
 * <pre>
 * packagingOptions {
 *     pickFirst "anyFileWillDo"
 *     exclude "/secret-data/&#042;&#042;"
 * }
 * </pre>
 *
 * <p>Example that removes all patterns:
 *
 * <pre>
 * packagingOptions {
 *     pickFirsts = [] // Not really needed because the default is empty.
 *     merges = []
 *     excludes = []
 * }
 * </pre>
 *
 * <p>Example that merges all {@code LICENSE.txt} files in the root.
 *
 * <pre>
 * packagingOptions {
 *     merge "/LICENSE.txt" // Same as: merges += ["/LICENSE.txt"]
 *     excludes -= ["/LICENSE.txt"] // Not really needed because merges take precedence over excludes.
 * }
 * </pre>
 */
open class PackagingOptions @Inject constructor(dslServices: DslServices) :
    com.android.builder.model.PackagingOptions,
    com.android.build.api.dsl.PackagingOptions {

    override val excludes: MutableSet<String> = defaultExcludes.toMutableSet()

    fun setExcludes(patterns: Set<String>) {
        val newExcludes = patterns.toList()
        excludes.clear()
        excludes.addAll(newExcludes)
    }

    override fun exclude(pattern: String) {
        excludes.add(pattern);
    }

    override val pickFirsts: MutableSet<String> = mutableSetOf()

    fun setPickFirsts(patterns: Set<String>) {
        val newPickFirsts = patterns.toList()
        pickFirsts.clear()
        pickFirsts.addAll(newPickFirsts)
    }

    override fun pickFirst(pattern: String) {
        pickFirsts.add(pattern);
    }

    override val merges: MutableSet<String> = defaultMerges.toMutableSet()

    fun setMerges(patterns: Set<String>) {
        val newMerges = patterns.toList()
        merges.clear()
        merges.addAll(newMerges)
    }

    override fun merge(pattern: String) {
        merges.add(pattern);
    }

    override val doNotStrip: MutableSet<String> = mutableSetOf()

    fun setDoNotStrip(patterns: Set<String>) {
        val newDoNotStrip = patterns.toList()
        doNotStrip.clear()
        doNotStrip.addAll(newDoNotStrip)
    }

    override fun doNotStrip(pattern: String) {
        doNotStrip.add(pattern);
    }

    override val dex: DexPackagingOptions =
        dslServices.newInstance(DexPackagingOptionsImpl::class.java)

    override fun dex(action: DexPackagingOptions.() -> Unit) {
        action.invoke(dex)
    }

    fun dex(action: Action<DexPackagingOptions>) {
        action.execute(dex)
    }

    override val jniLibs: JniLibsPackagingOptions =
        dslServices.newInstance(JniLibsPackagingOptionsImpl::class.java)

    override fun jniLibs(action: JniLibsPackagingOptions.() -> Unit) {
        action.invoke(jniLibs)
    }

    fun jniLibs(action: Action<JniLibsPackagingOptions>) {
        action.execute(jniLibs)
    }

    override val resources: ResourcesPackagingOptions =
        dslServices.newInstance(ResourcesPackagingOptionsImpl::class.java)

    override fun resources(action: ResourcesPackagingOptions.() -> Unit) {
        action.invoke(resources)
    }

    fun resources(action: Action<ResourcesPackagingOptions>) {
        action.execute(resources)
    }
}

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

import com.android.build.api.dsl.Lint
import com.android.build.gradle.internal.dsl.decorator.annotation.NonNullableSetter
import com.android.build.gradle.internal.dsl.decorator.annotation.WithLazyInitialization
import com.android.build.gradle.internal.errors.DeprecationReporter.DeprecationTarget
import com.android.build.gradle.internal.services.DslServices
import com.android.builder.model.LintOptions.Companion.SEVERITY_DEFAULT_ENABLED
import com.android.builder.model.LintOptions.Companion.SEVERITY_ERROR
import com.android.builder.model.LintOptions.Companion.SEVERITY_FATAL
import com.android.builder.model.LintOptions.Companion.SEVERITY_IGNORE
import com.android.builder.model.LintOptions.Companion.SEVERITY_INFORMATIONAL
import com.android.builder.model.LintOptions.Companion.SEVERITY_WARNING
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import java.io.File
import javax.inject.Inject
import org.gradle.api.GradleException

abstract class LintOptions
    @Inject @WithLazyInitialization("lazyInit")
        constructor(private val dslServices: DslServices?):
    com.android.builder.model.LintOptions,
    com.android.build.api.dsl.LintOptions,
    Lint {

    @Suppress("unused") // the call is injected by DslDecorator
    protected fun lazyInit() {
        isAbortOnError = true
        isAbsolutePaths = true
        isExplainIssues = true
        isCheckReleaseBuilds = true
        htmlReport = true
        xmlReport = true
    }

    protected val severities: MutableMap<String, Int> = Maps.newHashMap()

    @set:NonNullableSetter
    abstract override var lintConfig: File?

    override var disable: MutableSet<String> = Sets.newHashSet()
        set(value) {
            disable.addAll(value)
        }

    override var enable: MutableSet<String> = Sets.newHashSet()
        set(value) {
            enable.addAll(value)
        }

    @Deprecated(message = "", replaceWith = ReplaceWith("checkOnly"))
    override var check: MutableSet<String>
        get() = checkOnly
        set(value) {
            checkOnly.addAll(value)
        }
    override var isCheckTestSources: Boolean = false
        set(value) {
            field = value
            if (value) {
                isIgnoreTestSources = false
            }
        }
    override var isIgnoreTestSources: Boolean = false
        set(value) {
            field = value
            if (value) {
                isCheckTestSources = false
            }
        }
    override var textOutput: File? = null
        set(value) {
            checkNotNull(value)
            textReport = true
            field = value
        }
    override var htmlOutput: File? = null
        set(value) {
            checkNotNull(value)
            htmlReport = true
            field = value
        }
    override var xmlOutput: File? = null
        set(value) {
            checkNotNull(value)
            if (value.name.equals("lint.xml")) {
                throw GradleException(
                    "Don't set the xmlOutput file to \"lint.xml\"; that's a "
                            + "reserved filename used for for lint configuration files, not reports.")
            }
            xmlReport = true
            field = value
        }
    override var sarifOutput: File? = null
        set(value) {
            checkNotNull(value)
            sarifReport = true
            field = value
        }

    override val severityOverrides: MutableMap<String, Int>?
        get() {
            if (severities.isEmpty()) return null
            return severities
        }

    // -- DSL Methods.
    override fun baseline(baseline: String) {
        var file = File(baseline)
        if (!file.isAbsolute) {
            // If I had the project context, I could do
            //   project.file(baselineFile.getPath())
            file = file.absoluteFile
        }
        this.baselineFile = file
    }

    override fun baseline(baselineFile: File) {
        this.baselineFile = baselineFile
    }

    override fun check(id: String) {
        emitCheckWarning()
        checkOnly(id)
    }

    override fun check(vararg ids: String) {
        emitCheckWarning()
        checkOnly(*ids)
    }

    private fun emitCheckWarning() {
        assert(dslServices != null)
        dslServices!!.deprecationReporter
                .reportDeprecatedUsage(
                        "android.lintOptions.checkOnly",
                        "android.lintOptions.check",
                        DeprecationTarget.LINT_CHECK_ONLY)
    }

    override fun checkOnly(id: String) {
        checkOnly.add(id)
    }

    override fun checkOnly(vararg ids: String) {
        ids.forEach {
            checkOnly(it)
        }
    }

    override fun enable(id: String) {
        enable.add(id)
        severities[id] = SEVERITY_DEFAULT_ENABLED
    }

    override fun enable(vararg ids: String) {
        ids.forEach {
            enable(it)
        }
    }

    override fun disable(id: String) {
        disable.add(id)
        severities[id] = SEVERITY_IGNORE
    }

    override fun disable(vararg ids: String) {
        ids.forEach {
            disable(it)
        }
    }

    // For textOutput 'stdout' or 'stderr' (normally a file)
    override fun textOutput(textOutput: String) {
        this.textOutput = File(textOutput)
    }

    // For textOutput file()
    override fun textOutput(textOutput: File) {
        this.textOutput = textOutput
    }

    override fun informational(id: String) {
        severities[id] = SEVERITY_INFORMATIONAL
    }

    override fun informational(vararg ids: String) {
        ids.forEach {
            informational(it)
        }
    }

    override fun ignore(id: String) {
        severities[id] = SEVERITY_IGNORE
    }

    override fun ignore(vararg ids: String) {
        ids.forEach {
            ignore(it)
        }
    }

    override fun warning(id: String) {
        severities[id] = SEVERITY_WARNING
    }

    override fun warning(vararg ids: String) {
        ids.forEach {
            warning(it)
        }
    }

    override fun error(id: String) {
        severities[id] = SEVERITY_ERROR
    }

    override fun error(vararg ids: String) {
        ids.forEach {
            error(it)
        }
    }

    override fun fatal(id: String) {
        severities[id] = SEVERITY_FATAL
    }

    override fun fatal(vararg ids: String) {
        ids.forEach {
            fatal(it)
        }
    }
}

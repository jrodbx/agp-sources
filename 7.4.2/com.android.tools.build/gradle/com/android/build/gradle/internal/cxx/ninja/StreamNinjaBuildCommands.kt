/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.ninja

import com.android.build.gradle.internal.cxx.io.ProgressCallback
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.LiteralType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.VariableType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.CommentType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.EscapedSpaceType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.EscapedDollarType
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.EscapedColonType
import com.android.build.gradle.internal.cxx.collections.DoubleStringBuilder
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapeTokenType.VariableWithCurliesType
import org.apache.commons.io.input.CharSequenceReader
import java.io.File
import java.util.Locale

/**
 * This function builds on and is one level higher than [streamNinjaStatements]. It adds variable
 * namespace scoping and evaluation. The scope precedence order is global variables then 'build'
 * statement properties.
 *
 * The output is a stream of [NinjaBuildUnexpandedCommand], one for each 'build' statement. It
 * has the input files, the output files, the 'rule' properties, and an [expand] function that
 * can be used to look up properties values in the current scope according Ninja property scoping
 * rules.
 *
 * Each of the values in [NinjaBuildUnexpandedCommand] have properties unexpanded. So, for example,
 * an unexpanded compile command might look like this:
 *
 *    /path/to/clang++
 *          --target=aarch64-none-linux-android21
 *          --gcc-toolchain=/path/to/ndk/21.4.7075529/toolchains/llvm/prebuilt/darwin-x86_64
 *          --sysroot=/path/to/ndk/21.4.7075529/toolchains/llvm/prebuilt/darwin-x86_64/sysroot
 *          $DEFINES
 *          $INCLUDES
 *          $FLAGS
 *          -MD
 *          -MT $out
 *          -MF
 *          $DEP_FILE
 *          -o
 *          $out
 *          -c $in
 *
 * Notice the $ variables.
 *
 * The caller of [streamNinjaBuildCommands] can replace $ variables by looking them up with the
 * supplied [expand] function.
 *
 * The variables $in and $out are special in Ninja. They don't literally exist in 'build.ninja'.
 * Instead, they are constructed from the current 'build' statements inputs and outputs.
 */
fun streamNinjaBuildCommands(
    file : File,
    progress: ProgressCallback? = null,
    action : NinjaBuildUnexpandedCommand.() -> Unit) {
    val state = EvaluationState()


    streamNinjaStatements(file, progress) { node ->
        when(node) {
            is NinjaStatement.Assignment -> state.assignPropertyValue(node.name, node.value.toString())
            is NinjaStatement.RuleDef -> state.assignRule(node.name, node)
            is NinjaStatement.BuildDef -> {
                val rule = state.getRule(node.rule)
                val unexpanded = NinjaBuildUnexpandedCommand(
                    buildDef = node,
                    ruleDef = rule,
                    globalProperties = state.variables
                )
                action(unexpanded)
            }
            is NinjaStatement.Default -> { }
            else -> error("$node")
        }
    }
}
/**
 * Represents a Ninja 'build' statement along with the 'rule' that applies to it and an [expand]
 * function that can be used to expand property values.
 */
data class NinjaBuildUnexpandedCommand(
    private val ruleDef : NinjaStatement.RuleDef,
    private val buildDef : NinjaStatement.BuildDef,
    private val globalProperties : Map<String, String>
) {
    val ruleName : String get() = ruleDef.name

    // NOTE, the fields below use a pattern like:
    //   var _field : String? = null
    //   val field get() = _field ?: computeField().also { _field = it }
    // This is roughly equivalent to:
    //   val field by lazy { computeField() }
    // The main difference is the former is more performant and also emits less boilerplate IL.
    // This issue and its solution was pointed out by the Linter and I confirmed the byte-code
    // difference is significant.

    // All input files separated by spaces
    private var _ins : String? = null
    private val ins get() = _ins ?:
        (buildDef.explicitInputs + buildDef.implicitInputs)
            .joinToString(" ").also { _ins = it }

    // All output files separated by spaces
    private var _outs : String? = null
    private val outs get() = _outs ?:
        (buildDef.explicitOutputs + buildDef.implicitOutputs)
            .joinToString(" ").also { _outs = it }

    // Explicit inputs to a build step
    private var _explicitInputs : List<String>? = null
    val explicitInputs get() = _explicitInputs ?:
        buildDef.explicitInputs.map { expand(it) }
            .also {  _explicitInputs = it }

    // Implicit inputs of a build step
    private var _implicitInputs : List<String>? = null
    val implicitInputs get() = _implicitInputs ?:
        buildDef.implicitInputs.map { expand(it) }
            .also {  _implicitInputs = it }

    // Inputs used only to determine build order
    private var _orderOnlyInputs : List<String>? = null
    val orderOnlyInputs get() = _orderOnlyInputs ?:
        buildDef.orderOnlyInputs.map { expand(it) }
            .also { _orderOnlyInputs = it }

    // Explicit outputs of a build step
    private var _explicitOutputs : List<String>? = null
    val explicitOutputs get() = _explicitOutputs ?:
        buildDef.explicitOutputs.map { expand(it) }
            .also { _explicitOutputs = it }

    // Implicit outputs of a build step
    private var _implicitOutputs : List<String>? = null
    val implicitOutputs get() = _implicitOutputs ?:
        buildDef.implicitOutputs.map { expand(it) }
            .also { _implicitOutputs = it }

    // Rule properties before ${macros} are expanded
    private var _unexpandedRuleProperties : Map<String, String>? = null
    private val unexpandedRuleProperties get() = _unexpandedRuleProperties ?:
        ruleDef.properties.map { it.key.lowercase(Locale.getDefault()) to it.value }.toMap()
            .also { _unexpandedRuleProperties = it }

    private fun getProperty(name : String) : String {
        return when(name) {
            "in" -> ins
            "out" -> outs
            else -> buildDef.properties[name]
                ?: globalProperties[name]
                ?: ""
        }
    }

    /**
     * Expand the $ variables in the given [value].
     */
    fun expand(value : String, buffer : DoubleStringBuilder = DoubleStringBuilder()) : String {
        if (!value.contains("$")) return value
        buffer.front.clear()
        buffer.front.append(value)
        var loops = 0
        while(true) {
            if (!buffer.front.contains("$")) return buffer.front.toString()
            if (loops > 100) error("maximum recursion depth exceeded while expanding '$value'")
            buffer.back.clear()
            CharSequenceReader(buffer.front).streamUnescapedNinja { type, value ->
                when (type) {
                    LiteralType -> buffer.back.append(value)
                    VariableType -> buffer.back.append(getProperty(value.toString()))
                    VariableWithCurliesType -> buffer.back.append(getProperty(value.toString()))
                    CommentType -> { }
                    EscapedColonType -> buffer.back.append(":")
                    EscapedDollarType -> buffer.back.append("$")
                    EscapedSpaceType -> buffer.back.append(" ")
                }
            }
            buffer.flip()
            ++loops
        }
    }

    /**
     * Expand this command including replacing @rspFile with content of that file.
     */
    fun expandWithResponseFile(buffer : DoubleStringBuilder ): String {
        return if (rspfile == null || rspfileContent == null) expand(command, buffer)
        else expand(command.replace("@$rspfile", rspfileContent!!), buffer)
    }

    /**
     * The 'command' property is the only property on 'rule' that is required. If the property
     * doesn't exist then "" is returned and the caller is expected to handle it as an error.
     */
    val command : String get() = unexpandedRuleProperties["command"] ?: ""
    /**
     * A generator rule is one that's meant to regenerate the 'build.ninja' file.
     */
    val generator : String? get() = unexpandedRuleProperties["generator"]
    /**
     * Rule's response file.
     */
    val rspfile : String? get() = unexpandedRuleProperties["rspfile"]
    /**
     * Rule's response file content.
     */
    val rspfileContent : String? get() = unexpandedRuleProperties["rspfile_content"]

}
private data class EvaluationState(
    /**
     * Key is the name of the property.
     * Value is the property value for that name at the current scope level.
     */
    var variables : MutableMap<String, String> = mutableMapOf(),
    /**
     * Key is the name of the rule.
     * Value is the definition of the rule at the current scope level.
     */
    var rules : MutableMap<String, NinjaStatement.RuleDef> = mutableMapOf()
) {
    fun assignPropertyValue(name : String, value : String) {
        val existing = variables[name]
        if (existing == value) {
            return
        }
        if (existing == null) {
            variables[name] = value
            return
        }
        // Replacing an existing value, make a copy so that existing detached copies of variables
        // remain unchanged.
        variables = variables.toMutableMap()
        variables[name] = value
    }
    fun assignRule(name : String, value : NinjaStatement.RuleDef) {
        rules[name] = value
    }
    fun getRule(name : String) : NinjaStatement.RuleDef {
        if (name == "phony") {
            // The "phony" rule is built in to ninja
            // See https://github.com/ninja-build/ninja/blob/master/doc/manual.asciidoc#the-phony-rule
            return NinjaStatement.RuleDef("phony", mapOf())
        }
        return rules[name] ?: error("Ninja rule '$name' was not found")
    }
}

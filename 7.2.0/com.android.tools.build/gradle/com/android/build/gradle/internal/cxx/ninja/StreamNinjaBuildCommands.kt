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

import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapedToken.Comment
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapedToken.EscapedColon
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapedToken.EscapedDollar
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapedToken.EscapedSpace
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapedToken.Literal
import com.android.build.gradle.internal.cxx.ninja.NinjaUnescapedToken.Variable
import java.io.File
import java.io.StringReader

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
    action : NinjaBuildUnexpandedCommand.() -> Unit) {
    val state = EvaluationState()
    state.scope().use {
        streamNinjaStatements(file) { node ->
            when(node) {
                is NinjaStatement.Assignment -> state.assignPropertyValue(node.name, node.value)
                is NinjaStatement.RuleDef -> state.assignRule(node.name, node)
                is NinjaStatement.BuildDef -> state.scope().use {
                    node.properties.forEach { (name, value) ->
                        state.assignPropertyValue(name, value)
                    }
                    val ins = (node.explicitInputs + node.implicitInputs).joinToString(" ")
                    val outs = (node.explicitOutputs + node.implicitOutputs).joinToString(" ")
                    state.assignPropertyValue("in", ins)
                    state.assignPropertyValue("out", outs)
                    val rule = state.getRule(node.rule)

                    val unexpanded = NinjaBuildUnexpandedCommand(
                        implicitInputs = node.implicitInputs,
                        explicitInputs = node.explicitInputs,
                        orderOnlyInputs = node.orderOnlyInputs,
                        implicitOutputs = node.implicitOutputs,
                        explicitOutputs = node.explicitOutputs,
                        unexpandedRuleProperties = rule.properties.map { it.key.toLowerCase() to it.value }.toMap(),
                        getProperty = { s -> state.getPropertyValue(s) }
                    )
                    action(unexpanded)

                }
                is NinjaStatement.Default -> { }
                else -> error("$node")
            }
        }
    }
}

/**
 * Represents a Ninja 'build' statement along with the 'rule' that applies to it and an [expand]
 * function that can be used to expand property values.
 */
data class NinjaBuildUnexpandedCommand(
    val explicitInputs : List<String>,
    val implicitInputs : List<String>,
    val orderOnlyInputs : List<String>,
    val explicitOutputs : List<String>,
    val implicitOutputs : List<String>,
    val unexpandedRuleProperties : Map<String, String>,
    private val getProperty : (String) -> String
) {
    /**
     * Expand the $ variables in the given [value].
     */
    fun expand(value : String) : String {
        var current = value
        var loops = 0
        while(true) {
            if (!current.contains("$")) return current
            if (loops > 100) error("mutual recursion between ninja properties while expanding '$value'")
            val sb = StringBuilder()
            StringReader(current).streamUnescapedNinja { token ->
                when (token) {
                    is Literal -> sb.append(token.value)
                    is Variable -> sb.append(getProperty(token.name))
                    is Comment -> { }
                    is EscapedColon -> sb.append(":")
                    is EscapedDollar -> sb.append("$")
                    is EscapedSpace -> sb.append(" ")
                }
            }
            current = sb.toString()
            ++loops
        }
    }

    /**
     * The 'command' property is the only property on 'rule' that is required so there's a special
     * accessor. If the property doesn't exist then "" is returned and the caller is expected to
     * handle it as an error.
     */
    val command : String get() = unexpandedRuleProperties["command"] ?: ""
}

private data class EvaluationState(
    /**
     * Key is the name of the property.
     * Value is the property value for that name at the current scope level.
     */
    val variableScopes : MutableList<MutableMap<String, String>> = mutableListOf(),
    /**
     * Key is the name of the rule.
     * Value is the definition of the rule at the current scope level.
     */
    val ruleScopes : MutableList<MutableMap<String, NinjaStatement.RuleDef>> = mutableListOf(),
) {
    fun scope() : AutoCloseable {
        variableScopes.add(0, mutableMapOf())
        ruleScopes.add(0, mutableMapOf())
        return AutoCloseable {
            ruleScopes.removeAt(0)
            variableScopes.removeAt(0)
        }
    }

    fun assignPropertyValue(name : String, value : String) {
        variableScopes[0][name] = value
    }

    fun getPropertyValue(name : String) : String {
        for (scope in variableScopes) {
            val value = scope[name]
            if (value != null) {
                return value
            }
        }
        return ""
    }

    fun assignRule(name : String, value : NinjaStatement.RuleDef) {
        ruleScopes[0][name] = value
    }

    fun getRule(name : String) : NinjaStatement.RuleDef {
        if (name == "phony") {
            // The "phony" rule is built in to ninja
            // See https://github.com/ninja-build/ninja/blob/master/doc/manual.asciidoc#the-phony-rule
            return NinjaStatement.RuleDef("phony", mapOf())
        }
        for (scope in ruleScopes) {
            val value = scope[name]
            if (value != null) {
                return value
            }
        }
        error("Ninja rule '$name' was not found")
    }
}


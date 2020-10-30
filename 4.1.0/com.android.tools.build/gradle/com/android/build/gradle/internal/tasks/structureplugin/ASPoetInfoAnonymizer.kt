/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.structureplugin

fun ASPoetInfo.anonymize() {
    // Build module tree structure to anonymize
    val root = Node("")
    val nodeMap = mutableMapOf<String, Node>()
    for (module in modules) {
        var path = ""
        var lastNode = root
        for (name in module.name.split('_')) {
            path = path.appendToPath(name)
            if (!nodeMap.containsKey(path)) {
                val newNode = Node(name)
                nodeMap[path] = newNode
                lastNode.addChild(newNode)
            }
            lastNode = nodeMap[path]!!
        }
        nodeMap[module.name] = lastNode
    }

    root.anonymize()

    // Anonymize modules names and dependencies.
    for (module in modules) {
        module.name = nodeMap[module.name]!!.name
        for (dep in module.dependencies) {
            if (dep.type == DependencyType.MODULE) {
                dep.dependency = nodeMap[dep.dependency]!!.name
            }
        }
    }
}

fun String.appendToPath(path: String): String {
    return if (this == "") path else "${this}_$path"
}

private data class Node(
    var name: String,
    val subModules: MutableMap<String, Node> = mutableMapOf()) {

    fun addChild(node: Node) {
        if (subModules.containsKey(node.name)) return
        subModules[node.name] = node
    }

    fun anonymize() {
        var index = 0
        for (m in subModules.toSortedMap()) {
            index = m.value.anonymize("", index++)
        }
    }

    private fun anonymize(prefix: String, anonIndex: Int): Int {
        var index = anonIndex
        name = "${prefix}Module$index"
        index++
        for (m in subModules.toSortedMap()) {
            index = m.value.anonymize("${name}_", index++)
        }
        return index
    }
}
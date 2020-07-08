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
package com.android.support

import com.android.SdkConstants.ATTR_TYPE
import com.android.utils.XmlUtils
import java.io.InputStreamReader

private const val ROOT_ELEMENT = "migration-map"
// migrate package/class element and attribute names
private const val MIGRATE_ENTRY_NAME = "migrate"
private const val ATTR_OLD_NAME = "old-name"
private const val ATTR_NEW_NAME = "new-name"
private const val ATTR_TYPE = "type"
private const val TYPE_CLASS = "CLASS"
private const val TYPE_PACKAGE = "PACKAGE"
// migrate-dependency element and attribute names
private const val MIGRATE_DEPENDENCY_NAME = "migrate-dependency"
private const val ATTR_OLD_GROUP_NAME = "old-group-name"
private const val ATTR_OLD_ARTIFACT_NAME = "old-artifact-name"
private const val ATTR_NEW_GROUP_NAME = "new-group-name"
private const val ATTR_NEW_ARTIFACT_NAME = "new-artifact-name"
private const val ATTR_NEW_BASE_VERSION_NAME = "base-version"
// upgrade-dependency element and attributes
private const val UPGRADE_DEPENDENCY_NAME = "upgrade-dependency"
private const val ATTR_GROUP_NAME = "group-name"
private const val ATTR_ARTIFACT_NAME = "artifact-name"

class InvalidDataException(message: String? = null) : RuntimeException(message)

interface MigrationParserVisitor {
    fun visitClass(old: String, new: String)
    fun visitPackage(old: String, new: String)
    fun visitGradleCoordinate(
      oldGroupName: String, oldArtifactName: String,
      newGroupName: String, newArtifactName: String, newBaseVersion: String
    )

    /** Called for coordinate upgrades where the artifact name and group do not need to be refactored */
    fun visitGradleCoordinateUpgrade(groupName: String, artifactName: String, newBaseVersion: String)
}

/**
 * Parses the Androidx migration data and calls the given visitor
 */
fun parseMigrationFile(visitor: MigrationParserVisitor) {
    val stream = visitor.javaClass.classLoader.getResourceAsStream("migrateToAndroidx/migration.xml")
    stream.use {
        val document = XmlUtils.parseDocument(InputStreamReader(stream), false)
        val root = document.documentElement
        if (ROOT_ELEMENT != root.nodeName) {
            throw InvalidDataException("Migration file does not start with <$ROOT_ELEMENT>")
        }

        XmlUtils.getSubTags(root).forEach { node ->
            if (node.nodeName == MIGRATE_ENTRY_NAME) {
                val oldName = node.getAttribute(ATTR_OLD_NAME)
                val newName = node.getAttribute(ATTR_NEW_NAME)
                val type = node.getAttribute(ATTR_TYPE)
                when (type) {
                    TYPE_PACKAGE -> visitor.visitPackage(oldName, newName)
                    TYPE_CLASS -> visitor.visitClass(oldName, newName)
                    else -> throw InvalidDataException("Invalid type $type")
                }
            } else if (node.nodeName == MIGRATE_DEPENDENCY_NAME) {
                val oldGroupName = node.getAttribute(ATTR_OLD_GROUP_NAME)
                val oldArtifactName = node.getAttribute(ATTR_OLD_ARTIFACT_NAME)
                val newGroupName = node.getAttribute(ATTR_NEW_GROUP_NAME)
                val newArtifactName = node.getAttribute(ATTR_NEW_ARTIFACT_NAME)
                val newBaseVersion = node.getAttribute(ATTR_NEW_BASE_VERSION_NAME)
                visitor.visitGradleCoordinate(
                    oldGroupName,
                    oldArtifactName,
                    newGroupName,
                    newArtifactName,
                    newBaseVersion
                )
            } else if (node.nodeName == UPGRADE_DEPENDENCY_NAME) {
                val groupName = node.getAttribute(ATTR_GROUP_NAME)
                val artifactName = node.getAttribute(ATTR_ARTIFACT_NAME)
                val baseVersion = node.getAttribute(ATTR_NEW_BASE_VERSION_NAME)
                visitor.visitGradleCoordinateUpgrade(groupName, artifactName, baseVersion)
            }
        }
    }
}
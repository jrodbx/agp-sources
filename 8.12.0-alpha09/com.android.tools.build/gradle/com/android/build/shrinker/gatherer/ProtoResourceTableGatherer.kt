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

package com.android.build.shrinker.gatherer

import com.android.build.shrinker.ResourceShrinkerModel
import com.android.build.shrinker.entriesSequence
import com.android.ide.common.resources.resourceNameToFieldName
import com.android.resources.ResourceType
import java.nio.file.Path

/**
 * Gathers application resources from proto compiled resource table. Flattens each resource name to
 * be compatible with field names in R classes.
 *
 * @param resourceTablePath path to resource table in proto format.
 */
class ProtoResourceTableGatherer(private val resourceTablePath: Path) : ResourcesGatherer {

    override fun gatherResourceValues(model: ResourceShrinkerModel) {
        model.readResourceTable(resourceTablePath).entriesSequence()
            .forEach { (id, packageName, type, entry) ->
                // We need to flatten resource names here to match fields names in R classes via
                // invoking ResourcesUtil.resourceNameToFieldName because we need to record R fields
                // usages in code.
                ResourceType.fromClassName(type)
                    ?.takeIf { it != ResourceType.STYLEABLE }
                    ?.let {
                        model.addResource(it, packageName, resourceNameToFieldName(entry.name), id)
                    }
            }
    }
}

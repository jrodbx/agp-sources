/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.shrinker

import com.android.SdkConstants.DOT_9PNG
import com.android.SdkConstants.DOT_PNG
import com.android.SdkConstants.DOT_XML
import com.android.aapt.Resources
import com.android.build.shrinker.DummyContent.TINY_9PNG
import com.android.build.shrinker.DummyContent.TINY_9PNG_CRC
import com.android.build.shrinker.DummyContent.TINY_BINARY_XML
import com.android.build.shrinker.DummyContent.TINY_BINARY_XML_CRC
import com.android.build.shrinker.DummyContent.TINY_PNG
import com.android.build.shrinker.DummyContent.TINY_PNG_CRC
import com.android.build.shrinker.DummyContent.TINY_PROTO_XML
import com.android.build.shrinker.DummyContent.TINY_PROTO_XML_CRC
import com.android.build.shrinker.gatherer.ResourcesGatherer
import com.android.build.shrinker.graph.ResourcesGraphBuilder
import com.android.build.shrinker.obfuscation.ObfuscationMappingsRecorder
import com.android.build.shrinker.usages.ResourceUsageRecorder
import com.android.ide.common.resources.findUnusedResources
import com.android.ide.common.resources.usage.ResourceStore
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource
import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.google.common.io.ByteStreams
import com.google.common.io.Files
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Unit that analyzes all resources (after resource merging, compilation and code shrinking has
 * been completed) and figures out which resources are unused, and replaces them with dummy content
 * inside zip archive file.
 *
 * Resource shrinker implementation that allows to customize:
 * <ul>
 *     <li>application resource gatherer (from R files, resource tables, etc);
 *     <li>recorder for mappings from obfuscated class/methods to original class/methods;
 *     <li>sources from which resource usages are recorded (Dex files, compiled JVM classes,
 *         AndroidManifests, etc);
 *     <li>resources graph builder that connects resources dependent on each other (analyzing
 *         raw resources content in XML, HTML, CSS, JS, analyzing resource content in proto
 *         compiled format);
 * </ul>
 */
class ResourceShrinkerImpl(
    private val resourcesGatherers: List<ResourcesGatherer>,
    private val obfuscationMappingsRecorder: ObfuscationMappingsRecorder?,
    private val usageRecorders: List<ResourceUsageRecorder>,
    private val graphBuilders: List<ResourcesGraphBuilder>,
    private val debugReporter: ShrinkerDebugReporter,
    val supportMultipackages: Boolean,
    private val usePreciseShrinking: Boolean
) : ResourceShrinker {
    val model = ResourceShrinkerModel(debugReporter, supportMultipackages)
    private lateinit var unused: List<Resource>

    override fun analyze() {
        resourcesGatherers.forEach { it.gatherResourceValues(model) }
        obfuscationMappingsRecorder?.recordObfuscationMappings(model)
        usageRecorders.forEach { it.recordUsages(model) }
        graphBuilders.forEach { it.buildGraph(model) }

        model.resourceStore.processToolsAttributes()
        model.keepPossiblyReferencedResources()

        debugReporter.debug { model.resourceStore.dumpResourceModel() }

        unused = findUnusedResources(model.resourceStore.resources) { roots ->
            debugReporter.debug { "The root reachable resources are:" }
            debugReporter.debug { roots.joinToString("\n", transform = { " $it" }) }
        }
        debugReporter.debug { "Unused resources are: " }
        debugReporter.debug { unused.joinToString("\n", transform = { " $it" })}

    }

    override fun close() {
        debugReporter.close()
    }

    override fun getUnusedResourceCount(): Int {
        return unused.size
    }

    override fun rewriteResourcesInApkFormat(
        source: File,
        dest: File,
        format: LinkedResourcesFormat
    ) {
        rewriteResourceZip(source, dest, ApkArchiveFormat(model.resourceStore, format))
    }

    override fun rewriteResourcesInBundleFormat(
        source: File,
        dest: File,
        moduleNameToPackageNameMap: Map<String, String>
    ) {
        rewriteResourceZip(
            source,
            dest,
            BundleArchiveFormat(model.resourceStore, moduleNameToPackageNameMap)
        )
    }

    private fun rewriteResourceZip(source: File, dest: File, format: ArchiveFormat) {
        if (dest.exists() && !dest.delete()) {
            throw IOException("Could not delete $dest")
        }
        JarOutputStream(BufferedOutputStream(FileOutputStream(dest))).use { zos ->
            ZipFile(source).use { zip ->
                // Rather than using Deflater.DEFAULT_COMPRESSION we use 9 here,  since that seems
                // to match the compressed sizes we observe in source .ap_ files encountered by the
                // resource shrinker:
                zos.setLevel(9)
                zip.entries().asSequence().forEach {
                    if (format.fileIsNotReachable(it)) {
                        // If we don't use precise shrinking we don't remove the files, see:
                        // https://b.corp.google.com/issues/37010152
                        if (!usePreciseShrinking) {
                            replaceWithDummyEntry(zos, it, format.resourcesFormat)
                        }
                    } else if (it.name.endsWith("resources.pb") && usePreciseShrinking) {
                            removeResourceUnusedTableEntries(zip.getInputStream(it), zos, it)
                    } else {
                        copyToOutput(zip.getInputStream(it), zos, it)
                    }
                }
            }
        }
        // If net negative, copy original back. This is unusual, but can happen
        // in some circumstances, such as the one described in
        // https://plus.google.com/+SaidTahsinDane/posts/X9sTSwoVUhB
        // "Removed unused resources: Binary resource data reduced from 588KB to 595KB: Removed -1%"
        // Guard against that, and worst case, just use the original.
        val before = source.length()
        val after = dest.length()
        if (after > before) {
            debugReporter.info {
                "Resource shrinking did not work (grew from $before to $after); using original " +
                "instead"
            }
            Files.copy(source, dest)
        }
    }

    private fun removeResourceUnusedTableEntries(zis: InputStream,
                                                 zos: JarOutputStream,
                                                 srcEntry: ZipEntry) {
        val resourceIdsToRemove =
            model.resourceStore.resources.filter { !it.isReachable }.map { it.value }.toList()
        val shrunkenResourceTable = Resources.ResourceTable.parseFrom(zis)
                .nullOutEntriesWithIds(resourceIdsToRemove)
        val bytes = shrunkenResourceTable.toByteArray()
        val outEntry = JarEntry(srcEntry.name)
        if (srcEntry.time != -1L) {
            outEntry.time = srcEntry.time
        }
        if (srcEntry.method == JarEntry.STORED) {
            outEntry.method = JarEntry.STORED
            outEntry.size = bytes.size.toLong()
            val crc = CRC32()
            crc.update(bytes, 0, bytes.size)
            outEntry.crc = crc.getValue()
        }
        zos.putNextEntry(outEntry)
        zos.write(bytes)
        zos.closeEntry()
    }

    /** Replaces the given entry with a minimal valid file of that type.  */
    private fun replaceWithDummyEntry(
        zos: JarOutputStream,
        entry: ZipEntry,
        format: LinkedResourcesFormat
    ) {
        // Create a new entry so that the compressed len is recomputed.
        val name = entry.name
        val (bytes, crc) = when {
            // DOT_9PNG (.9.png) must be always before DOT_PNG (.png)
            name.endsWith(DOT_9PNG) -> TINY_9PNG to TINY_9PNG_CRC
            name.endsWith(DOT_PNG) -> TINY_PNG to TINY_PNG_CRC
            name.endsWith(DOT_XML) && format == LinkedResourcesFormat.BINARY ->
                TINY_BINARY_XML to TINY_BINARY_XML_CRC
            name.endsWith(DOT_XML) && format == LinkedResourcesFormat.PROTO ->
                TINY_PROTO_XML to TINY_PROTO_XML_CRC
            else -> ByteArray(0) to 0L
        }

        val outEntry = JarEntry(name)
        if (entry.time != -1L) {
            outEntry.time = entry.time
        }
        if (entry.method == JarEntry.STORED) {
            outEntry.method = JarEntry.STORED
            outEntry.size = bytes.size.toLong()
            outEntry.crc = crc
        }
        zos.putNextEntry(outEntry)
        zos.write(bytes)
        zos.closeEntry()
        debugReporter.info {
            "Skipped unused resource $name: ${entry.size} bytes (replaced with small dummy file " +
            "of size ${bytes.size} bytes)"
        }
    }

    private fun copyToOutput(zis: InputStream, zos: JarOutputStream, entry: ZipEntry) {
        // We can't just compress all files; files that are not compressed in the source .ap_ file
        // must be left uncompressed here, since for example RAW files need to remain uncompressed
        // in the APK such that they can be mmap'ed at runtime.
        // Preserve the STORED method of the input entry.
        val outEntry = when (entry.method) {
            JarEntry.STORED -> JarEntry(entry)
            else -> JarEntry(entry.name)
        }
        if (entry.time != -1L) {
            outEntry.time = entry.time
        }
        zos.putNextEntry(outEntry)
        if (!entry.isDirectory) {
            zos.write(ByteStreams.toByteArray(zis))
        }
        zos.closeEntry()
    }
}

private interface ArchiveFormat {
    val resourcesFormat: LinkedResourcesFormat
    fun fileIsNotReachable(entry: ZipEntry): Boolean
}

private class ApkArchiveFormat(
    private val store: ResourceStore,
    override val resourcesFormat: LinkedResourcesFormat
) : ArchiveFormat {

    override fun fileIsNotReachable(entry: ZipEntry): Boolean {
        if (entry.isDirectory || !entry.name.startsWith("res/")) {
            return false
        }
        val (_, folder, name) = entry.name.split('/', limit = 3)
        return !store.isJarPathReachable(folder, name)
    }
}

private class BundleArchiveFormat(
    private val store: ResourceStore,
    private val moduleNameToPackageName: Map<String, String>
) : ArchiveFormat {

    override val resourcesFormat = LinkedResourcesFormat.PROTO

    override fun fileIsNotReachable(entry: ZipEntry): Boolean {
        val module = entry.name.substringBefore('/')
        val packageName = moduleNameToPackageName[module]
        if (entry.isDirectory || packageName == null || !entry.name.startsWith("$module/res/")) {
            return false
        }
        val (_, _, folder, name) = entry.name.split('/', limit = 4)
        return !store.isJarPathReachable(folder, name)
    }
}

private fun ResourceStore.isJarPathReachable(
    folder: String,
    name: String
): Boolean {
    val folderType = ResourceFolderType.getFolderType(folder) ?: return true
    val resourceName = name.substringBefore('.')
    // Bundle format has a restriction: in case the same resource is duplicated in multiple modules
    // its content should be the same in all of them. This restriction means that we can't replace
    // resource with dummy content if its duplicate is used in some module.
    return FolderTypeRelationship.getRelatedResourceTypes(folderType)
        .filterNot { it == ResourceType.ID }
        .flatMap { getResources(it, resourceName) }
        .any { it.isReachable }
}

private fun ResourceStore.getResourceId(
    folder: String,
    name: String
): Int {
    val folderType = ResourceFolderType.getFolderType(folder) ?: return -1
    val resourceName = name.substringBefore('.')
    return FolderTypeRelationship.getRelatedResourceTypes(folderType)
        .filterNot { it == ResourceType.ID }
        .flatMap { getResources(it, resourceName) }
        .map { it.value }
        .getOrElse(0) { -1 }

}

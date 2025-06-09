package com.android.ide.common.resources.usage

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.utils.XmlUtils
import java.io.File
import java.nio.charset.Charset

fun getResourcesFromExplodedAarToFile(explodedAar: File) : List<String> {
    val resourceDir = explodedAar.resolve(SdkConstants.FD_RESOURCES)
    return getResourcesFromDirectory(resourceDir)
}

/**
 * Returns a string list of resource symbols in the format 'resourceType:resourceName:resourceId'
 * from the declared and referenced resources from the ResourceDirectory()
 */
fun getResourcesFromDirectory(directory : File) : List<String> {
    if (directory.list() == null || directory.listFiles()!!.none()) {
        return emptyList()
    }
    val resourceUsageModel = ResourceUsageModel()
    // Extract resource declarations and usages from exploded AAR into resourceUsageModel.
    directory
        .walk()
        .filter { it.isFile }
        .toSet()
        .forEach {
            getDeclaredAndReferencedResourcesFrom(it, resourceUsageModel)
        }

    return resourceUsageModel.resources.map { it.toString() }
}

/**
 * Returns a list of ResourceUsageModel.Resource within the resource file.
 */
fun getDeclaredAndReferencedResourcesFrom(
        resourceFile: File, resourceUsageModel: ResourceUsageModel = ResourceUsageModel()
): List<ResourceUsageModel.Resource> {
    val resFolderType = ResourceFolderType.getTypeByName(resourceFile.parentFile.name)
    if (resourceFile.name.endsWith(SdkConstants.DOT_XML)
        && resFolderType != ResourceFolderType.RAW
    ) {
        val resourceString = resourceFile.readText(Charset.defaultCharset())
        val document = XmlUtils.parseDocument(resourceString, false)
        resourceUsageModel
            .visitXmlDocument(resourceFile, resFolderType, document)
    } else {
        resourceUsageModel.visitBinaryResource(resFolderType, resourceFile)
    }
    return resourceUsageModel.resources
}
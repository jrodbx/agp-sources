package com.android.build.gradle.internal.res


import com.android.aaptcompiler.ResourceCompilerOptions
import com.android.aaptcompiler.compileResource
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.ide.common.resources.CompileResourceRequest
import org.gradle.api.provider.ListProperty

abstract class ResourceCompilerRunnable : ProfileAwareWorkAction<ResourceCompilerRunnable.Params>() {

  override fun run() {
    parameters.request.get().forEach {
      compileSingleResource(it)
    }
  }

  abstract class Params: ProfileAwareWorkAction.Parameters() {
    abstract val request: ListProperty<CompileResourceRequest>
  }

  companion object {
    @JvmStatic
    fun compileSingleResource(request: CompileResourceRequest) {
      val options = ResourceCompilerOptions(
        pseudolocalize = request.isPseudoLocalize,
        partialRFile = request.partialRFile,
        legacyMode = true,
        sourcePath = request.sourcePath)

      // TODO: find a way to re-use the blame logger between requests
      val blameLogger = blameLoggerFor(request, LoggerWrapper.getLogger(this::class.java))
      compileResource(request.inputFile, request.outputDirectory, options, blameLogger)
    }
  }
}

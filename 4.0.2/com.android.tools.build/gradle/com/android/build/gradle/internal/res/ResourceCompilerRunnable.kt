package com.android.build.gradle.internal.res


import com.android.aaptcompiler.compileResource
import com.android.aaptcompiler.ResourceCompilerOptions
import com.android.ide.common.resources.CompileResourceRequest
import java.io.Serializable
import javax.inject.Inject

class ResourceCompilerRunnable @Inject constructor(
  private val params: Params
) : Runnable {

  override fun run() {
    compileSingleResource(params.request)
  }

  class Params(
    val request: CompileResourceRequest
  ) : Serializable

  companion object {
    @JvmStatic
    fun compileSingleResource(request: CompileResourceRequest) {
      val options = ResourceCompilerOptions(
        pseudolocalize = request.isPseudoLocalize,
        legacyMode = true)
      compileResource(request.inputFile, request.outputDirectory, options)
    }
  }
}

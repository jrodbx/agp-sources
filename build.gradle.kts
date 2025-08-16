@file:Suppress("UnstableApiUsage")

plugins {
  kotlin("jvm") version embeddedKotlinVersion
}

/**
 * 1) Update AGP versions in gradle/xx.versions.toml
 * 2) Run './gradlew dumpSources'
 * 3) Check changeset into source control
 */

// Match all directories that look like version numbers, e.g. 8.11.1, 8.13.0-alpha02.
val versionDirPattern = """
  ^\d+\.\d+\.\d+(-alpha\d+)?$
""".trimIndent().toRegex()

rootDir.listFiles().orEmpty()
  .filter { it.isDirectory && versionDirPattern.matches(it.name) }
  .forEach { dir ->
    sourceSets.register(dir.name) {
      java.srcDir(dir)
    }
  }

val shared by configurations.registering {
  isCanBeResolved = true
  description = "Shared configuration for all source sets."
}

configurations.configureEach {
  // Share dependencies between all source sets.
  if (name != shared.name) {
    extendsFrom(shared.get())
  }
}

val agpGroupPrefix = "com.android.tools"

dependencies {
  shared(gradleApi())

  // Add all AGP dependencies but the AGP itself.
  configurations.detachedConfiguration(create(stable.agp.get()))
    .resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
      with(artifact.moduleVersion.id) {
        if (group.startsWith(agpGroupPrefix)) return@forEach
        shared("$group:$name:$version")
      }
    }
}

// Anchor task.
val dumpSources by tasks.registering

// https://mvnrepository.com/artifact/com.android.tools.build/gradle
listOf(
  stable.agp,
  alpha.agp,
).forEach { agp ->
  val agpVersion = requireNotNull(agp.get().version)
  // Create configuration for specific version of AGP.
  val agpConfiguration = configurations.create("agp$agpVersion") {
    // TODO: https://github.com/google/guava/issues/6801
    //  Fix `Cannot choose between the following variants of com.google.guava:guava:33.3.1-jre: androidRuntimeElements, jreRuntimeElements`.
    attributes {
      attribute(
        TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
        objects.named<TargetJvmEnvironment>(TargetJvmEnvironment.STANDARD_JVM),
      )
    }
  }

  // Add that version of AGP as a dependency to this configuration.
  agpConfiguration.dependencies.add(dependencies.create(agp.get()))

  // Create a task dedicated to extracting sources for that version.
  val dumpSingleAgpSources = tasks.register<Copy>("dump${agpVersion}Sources") {
    inputs.files(agpConfiguration)
    into(layout.projectDirectory.dir(agpVersion))
    // There should be no duplicates in sources, so fail if any are found.
    duplicatesStrategy = DuplicatesStrategy.FAIL

    val componentIds = agpConfiguration
      .incoming
      .resolutionResult
      .allDependencies
      .filterIsInstance<ResolvedDependencyResult>()
      .map { it.selected.id }
      .filterIsInstance<ModuleComponentIdentifier>()
      .filter { it.group.startsWith(agpGroupPrefix) }
      .toSet()

    dependencies
      .createArtifactResolutionQuery()
      .forComponents(componentIds)
      .withArtifacts(JvmLibrary::class.java, SourcesArtifact::class.java)
      .execute()
      .resolvedComponents
      .flatMap { it.getArtifacts(SourcesArtifact::class.java) }
      .filterIsInstance<ResolvedArtifactResult>()
      .forEach {
        logger.lifecycle("Found sources jar: ${it.file}")
        val id = it.id.componentIdentifier as ModuleComponentIdentifier
        from(zipTree(it.file)) {
          into("${id.group}/${id.module}")
        }
      }
  }

  // Hook anchor task to all version-specific tasks.
  dumpSources.configure {
    dependsOn(dumpSingleAgpSources)
  }
}

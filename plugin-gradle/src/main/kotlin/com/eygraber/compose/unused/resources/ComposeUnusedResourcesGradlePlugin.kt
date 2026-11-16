package com.eygraber.compose.unused.resources

import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.InternalSubpluginOption
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool
import java.io.File
import java.io.IOException

private const val COMPOSE_RESOURCE_INDEX_USAGE = "compose-resource-index"
internal const val COMPILER_EXTENSION_NAME = "unusedComposeResourcesCompiler"

public class ComposeUnusedResourcesGradlePlugin : KotlinCompilerPluginSupportPlugin {
  override fun apply(target: Project) {
    val extension = target.extensions.create(
      COMPILER_EXTENSION_NAME,
      UnusedComposeResourcesCompilerExtension::class.java,
    )
    extension.includeTestCompilations.convention(true)

    val usage = target.objects.named(Usage::class.java, COMPOSE_RESOURCE_INDEX_USAGE)
    target.configurations.consumable(COMPOSE_RESOURCE_INDEX_CONFIGURATION) {
      attributes.attribute(Usage.USAGE_ATTRIBUTE, usage)
    }
  }

  override fun getCompilerPluginId(): String = COMPILER_PLUGIN_ID

  override fun getPluginArtifact(): SubpluginArtifact {
    val manifestVersion = this::class.java.`package`.implementationVersion

    return SubpluginArtifact(
      groupId = "com.eygraber",
      artifactId = "compose-unused-resources-compiler-plugin",
      version = manifestVersion ?: "1.0.3",
    )
  }

  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
    if(kotlinCompilation.target.platformType == KotlinPlatformType.common) return false

    val extension = kotlinCompilation.target.project.extensions.getByType(
      UnusedComposeResourcesCompilerExtension::class.java,
    )
    if(kotlinCompilation.target.name in extension.excludedTargets.get()) return false

    val isTestCompilation = kotlinCompilation.associatedCompilations.isNotEmpty()
    return !isTestCompilation || extension.includeTestCompilations.get()
  }

  override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>,
  ): Provider<List<SubpluginOption>> = with(kotlinCompilation.target.project) {
    // The target name disambiguates same-named compilations across KMP targets (every target has a
    // "main"); it is empty in Kotlin/JVM projects, which only have one target.
    val targetName = kotlinCompilation.target.name.ifEmpty { "kotlin" }
    val indexDir = layout.buildDirectory.dir(
      "compose-unused-resources/$targetName/${kotlinCompilation.name}",
    )
    val indexFile = indexDir.map { dir -> dir.file("resources.index") }
    val rootDir = layout.projectDirectory.asFile

    kotlinCompilation.compileTaskProvider.configure {
      // The index is claimed as a compile output so that cache hits restore it.
      // The output is the directory, not the index file, because the Kotlin plugin restores each
      // compile output as a directory.
      outputs.dir(indexDir).withPropertyName("composeUnusedResourcesIndex")

      if(this is KotlinCompileTool) {
        val sourceFiles = sources
        doLast {
          pruneIndexToSourceSet(indexFile.get().asFile, sourceFiles.files, rootDir)
        }
      }
    }

    configurations.named(COMPOSE_RESOURCE_INDEX_CONFIGURATION) {
      outgoing.artifact(indexFile) {
        builtBy(kotlinCompilation.compileTaskProvider)
      }
    }

    // Internal options stay out of the task's input hash. An absolute path there would make the
    // cache key machine-specific.
    indexFile.map { file ->
      listOf(
        InternalSubpluginOption(
          key = COMPILER_PLUGIN_OPTION_OUTPUT_FILE,
          value = file.asFile.absolutePath,
        ),
        InternalSubpluginOption(
          key = COMPILER_PLUGIN_OPTION_ROOT_DIR,
          value = rootDir.absolutePath,
        ),
      )
    }
  }
}

/**
 * Drops records of files that are no longer in the compilation's source set. The compiler plugin
 * cannot do this itself, because it only sees the files of the current round.
 */
internal fun pruneIndexToSourceSet(indexFile: File, sourceFiles: Set<File>, rootDir: File) {
  if(!indexFile.isFile) return

  val sourcePaths = sourceFiles.mapTo(mutableSetOf()) { it.relativeToOrSelf(rootDir).invariantSeparatorsPath }
  val records = try {
    ResourceIndexFormat.parseRecords(indexFile.readText())
  }
  catch(_: IOException) {
    return
  }
  catch(_: IllegalArgumentException) {
    return
  }
  catch(_: IllegalStateException) {
    return
  }

  indexFile.writeText(ResourceIndexFormat.serialize(records.filter { it.sourceFile in sourcePaths }))
}

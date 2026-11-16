package com.eygraber.compose.unused.resources

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * Aggregates every module's per-compilation Compose resource indexes and registers the
 * `findUnusedComposeResources` check. Applied to the root project. Modules are discovered by depending on the
 * [COMPOSE_RESOURCE_INDEX_CONFIGURATION] configuration of each subproject; the lenient artifact view drops
 * those that don't expose it (non-Compose modules). The `unusedComposeResources` extension
 * configures the baseline and suppression patterns; `updateComposeUnusedResourcesBaseline` writes
 * the current findings to the baseline.
 */
public class UnusedComposeResourcesPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    val extension = target.extensions.create(
      "unusedComposeResources",
      UnusedComposeResourcesExtension::class.java,
    )
    extension.baselineFile.convention(
      target.layout.projectDirectory.file("compose-unused-resources-baseline.txt"),
    )
    extension.failOnStaleBaseline.convention(false)

    // Strict configuration roles forbid declaring dependencies on a resolvable configuration, so
    // they go on a dependency-scope configuration the resolvable one extends.
    val declared = target.configurations.dependencyScope("composeResourceIndex")

    target.subprojects.forEach { subproject ->
      target.dependencies.add(
        declared.name,
        target.dependencies.project(
          mapOf(
            "path" to subproject.isolated.path,
            "configuration" to COMPOSE_RESOURCE_INDEX_CONFIGURATION,
          ),
        ),
      )
    }

    val consumer = target.configurations.resolvable("composeResourceIndexClasspath") {
      extendsFrom(declared.get())
      // Each module exposes only its own index; non-transitive avoids pulling iOS/native variants.
      isTransitive = false
    }

    val indexArtifacts = consumer.map { it.incoming.artifactView { lenient(true) }.files }

    target.tasks.register<FindUnusedComposeResourcesTask>("findUnusedComposeResources") {
      description = "Reports Compose resources that are never referenced in code."
      group = "Verification"
      indexFiles.from(indexArtifacts)
      // Absent when the file doesn't exist, so @InputFile validation doesn't reject the default.
      baselineFile.set(extension.baselineFile.filter { it.asFile.exists() })
      suppressions.set(extension.suppressions)
      failOnStaleBaseline.set(extension.failOnStaleBaseline)
    }

    target.tasks.register<UpdateComposeUnusedResourcesBaselineTask>("updateComposeUnusedResourcesBaseline") {
      description = "Writes the unused Compose resources to the baseline file."
      group = "Verification"
      indexFiles.from(indexArtifacts)
      suppressions.set(extension.suppressions)
      baselineFile.set(extension.baselineFile)
    }
  }
}

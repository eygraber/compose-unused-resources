package com.eygraber.compose.unused.resources

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File
import kotlin.collections.joinToString

/**
 * Reports Compose resources that no module references, by aggregating the per-compilation index
 * files the compiler plugin writes and subtracting referenced accessors from declared ones.
 * Resources in the baseline or covered by a suppression pattern are left out of the report.
 */
@CacheableTask
public abstract class FindUnusedComposeResourcesTask : DefaultTask() {

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public abstract val indexFiles: ConfigurableFileCollection

  /** Baseline of tolerated unused resources; see [BaselineFormat]. */
  @get:InputFile
  @get:Optional
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public abstract val baselineFile: RegularFileProperty

  /** Suppression patterns of the form `package: type/name`; see [SuppressionPattern]. */
  @get:Input
  public abstract val suppressions: SetProperty<String>

  @get:Input
  public abstract val failOnStaleBaseline: Property<Boolean>

  @get:Internal
  public var isCheckOnly: Boolean = false
    private set

  init {
    failOnStaleBaseline.convention(false)
  }

  @Option(option = "check", description = "Only report unused resources and fail if any are found; for CI.")
  public fun setCheckOnly(check: Boolean) {
    isCheckOnly = check
  }

  @TaskAction
  public fun execute() {
    val unused = unusedComposeResources(indexFiles.files)
    val patterns = parseSuppressionPatterns(suppressions.get())
    val baseline = parseBaselineFile()

    val reported = unused.filter { key -> key !in baseline && patterns.none { it.matches(key) } }
    val suppressedNote = when(val count = unused.size - reported.size) {
      0 -> ""
      else -> " ($count suppressed)"
    }

    reportStaleBaselineEntries(baseline, unused)

    if(reported.isEmpty()) {
      logger.lifecycle("No unused Compose resources found.$suppressedNote")
      return
    }

    val report = reported.joinToString("\n") { "  ${it.asEntry()}" }

    if(isCheckOnly) {
      throw GradleException(
        "Found ${reported.size} unused Compose resource(s)$suppressedNote:\n$report\n" +
          "Remove them, reference them in code, or add them to the baseline.",
      )
    }

    logger.lifecycle("Found ${reported.size} unused Compose resource(s)$suppressedNote:\n$report")
  }

  private fun parseBaselineFile(): Set<ResourceKey> {
    val file = baselineFile.orNull?.asFile ?: return emptySet()
    if(!file.isFile) return emptySet()
    return try {
      BaselineFormat.parse(file.readText())
    }
    catch(e: IllegalArgumentException) {
      throw GradleException("Malformed Compose resource baseline: ${file.path}", e)
    }
  }

  private fun reportStaleBaselineEntries(baseline: Set<ResourceKey>, unused: List<ResourceKey>) {
    val stale = baseline - unused.toSet()
    if(stale.isEmpty()) return

    val entries = stale.sortedWith(ResourceKeyOrder).joinToString("\n") { "  ${it.asEntry()}" }
    val message = "Stale Compose resource baseline entries (${stale.size}):\n$entries\n" +
      "Remove them, or run updateComposeUnusedResourcesBaseline."

    if(failOnStaleBaseline.get()) throw GradleException(message)
    logger.warn(message)
  }
}

/** Aggregates [indexFiles] and returns declared accessors that no index references, sorted. */
internal fun unusedComposeResources(
  indexFiles: Iterable<File>,
): List<ResourceKey> {
  val declared = mutableSetOf<ResourceKey>()
  val referenced = mutableSetOf<ResourceKey>()

  indexFiles
    .filter { it.isFile && it.extension == "index" }
    .forEach { file ->
      val parsed = try {
        ResourceIndexFormat.parse(file.readText())
      }
      catch(e: IllegalArgumentException) {
        throw GradleException("Malformed Compose resource index: ${file.path}", e)
      }
      catch(e: IllegalStateException) {
        throw GradleException("Malformed Compose resource index: ${file.path}", e)
      }
      declared += parsed.declared
      referenced += parsed.referenced
    }

  return (declared - referenced).sortedWith(ResourceKeyOrder)
}

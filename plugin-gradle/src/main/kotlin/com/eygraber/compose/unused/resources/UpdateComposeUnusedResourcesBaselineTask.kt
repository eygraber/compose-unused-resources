package com.eygraber.compose.unused.resources

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Writes the currently unused Compose resources to the baseline file, so that
 * [FindUnusedComposeResourcesTask] tolerates them. Resources that a suppression pattern already
 * covers are left out.
 */
@DisableCachingByDefault(because = "Writes a file into the source tree on demand")
public abstract class UpdateComposeUnusedResourcesBaselineTask : DefaultTask() {

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public abstract val indexFiles: ConfigurableFileCollection

  @get:Input
  public abstract val suppressions: SetProperty<String>

  @get:OutputFile
  public abstract val baselineFile: RegularFileProperty

  @TaskAction
  public fun execute() {
    val patterns = parseSuppressionPatterns(suppressions.get())
    val unused = unusedComposeResources(indexFiles.files)
      .filter { key -> patterns.none { it.matches(key) } }

    val file = baselineFile.get().asFile
    file.parentFile?.mkdirs()
    file.writeText(BaselineFormat.serialize(unused))

    logger.lifecycle("Wrote ${unused.size} unused Compose resource(s) to ${file.path}")
  }
}

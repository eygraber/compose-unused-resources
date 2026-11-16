package com.eygraber.compose.unused.resources

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

/** Configures the `findUnusedComposeResources` check. */
public abstract class UnusedComposeResourcesExtension {

  /**
   * Baseline of tolerated unused resources, one exact `package: type/name` entry per line.
   * Defaults to `compose-unused-resources-baseline.txt` in the root project.
   */
  public abstract val baselineFile: RegularFileProperty

  /**
   * Suppression patterns of the form `package: type/name`, where a `*` inside a segment matches
   * any characters within that segment.
   */
  public abstract val suppressions: SetProperty<String>

  /** Fails the build when the baseline holds an entry that is no longer unused. Defaults to false. */
  public abstract val failOnStaleBaseline: Property<Boolean>

  /** Adds [patterns] to [suppressions]. */
  public fun suppress(vararg patterns: String) {
    suppressions.addAll(patterns.toList())
  }
}

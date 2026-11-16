package com.eygraber.compose.unused.resources

import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

/** Selects the compilations of a module that the compiler plugin indexes. */
public abstract class UnusedComposeResourcesCompilerExtension {

  /** Names of the Kotlin targets, such as `iosArm64`, that the compiler plugin does not index. Defaults to none. */
  public abstract val excludedTargets: SetProperty<String>

  /**
   * Indexes test compilations, so a reference from a test marks a resource as used. A test compilation
   * is a compilation associated with another compilation. Defaults to true.
   */
  public abstract val includeTestCompilations: Property<Boolean>

  /** Adds [targetNames] to [excludedTargets]. */
  public fun excludeTargets(vararg targetNames: String) {
    excludedTargets.addAll(targetNames.toList())
  }
}

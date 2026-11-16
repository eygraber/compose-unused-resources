package com.eygraber.compose.unused.resources

/**
 * One accessor a source file declares or references. The source file is what lets an incremental
 * compilation rewrite only the records of the files it recompiled and carry the rest over, instead
 * of replacing the whole index with the little it saw.
 */
public data class ResourceRecord(
  val kind: Kind,
  val key: ResourceKey,
  val sourceFile: String,
) {
  public enum class Kind {
    Declared,
    Referenced,
  }
}

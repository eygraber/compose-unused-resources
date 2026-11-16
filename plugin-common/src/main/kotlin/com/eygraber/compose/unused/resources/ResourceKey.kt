package com.eygraber.compose.unused.resources

/**
 * Identifies a resource accessor by its declaring package, kind, and name. Including the package
 * keeps same-named resources in different modules distinct, which text matching on `Res.string.back`
 * cannot do.
 */
public data class ResourceKey(
  val packageFqName: String,
  val type: String,
  val name: String,
)

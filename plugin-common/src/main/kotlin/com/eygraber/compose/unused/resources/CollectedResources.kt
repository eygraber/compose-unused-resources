package com.eygraber.compose.unused.resources

/** Resource accessors a compilation declares and references, collected by the IR extension. */
public class CollectedResources {
  public val records: MutableSet<ResourceRecord> = mutableSetOf()

  public val declared: Set<ResourceKey> get() = keysOf(ResourceRecord.Kind.Declared)
  public val referenced: Set<ResourceKey> get() = keysOf(ResourceRecord.Kind.Referenced)

  public fun unused(): Set<ResourceKey> = declared - referenced

  private fun keysOf(kind: ResourceRecord.Kind): Set<ResourceKey> =
    records.filterTo(mutableSetOf()) { it.kind == kind }.mapTo(mutableSetOf()) { it.key }
}

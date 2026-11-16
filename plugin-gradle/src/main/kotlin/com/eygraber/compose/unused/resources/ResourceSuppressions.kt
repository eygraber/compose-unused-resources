package com.eygraber.compose.unused.resources

import org.gradle.api.GradleException

internal val ResourceKeyOrder = compareBy<ResourceKey>({ it.packageFqName }, { it.type }, { it.name })

/** Renders [this] the way the report and the baseline spell an entry, `package: type/name`. */
internal fun ResourceKey.asEntry(): String = "$packageFqName: $type/$name"

/**
 * A suppression entry of the form `package: type/name`, where a `*` inside a segment matches any
 * characters within that segment.
 */
internal class SuppressionPattern private constructor(
  private val packageRegex: Regex,
  private val typeRegex: Regex,
  private val nameRegex: Regex,
) {
  fun matches(key: ResourceKey): Boolean =
    packageRegex.matches(key.packageFqName) &&
      typeRegex.matches(key.type) &&
      nameRegex.matches(key.name)

  companion object {
    fun parse(pattern: String): SuppressionPattern {
      val (packageFqName, type, name) = splitEntry(pattern)
      return SuppressionPattern(
        packageRegex = globToRegex(packageFqName),
        typeRegex = globToRegex(type),
        nameRegex = globToRegex(name),
      )
    }

    private fun globToRegex(segment: String): Regex =
      segment.split('*').joinToString(".*") { literal -> Regex.escape(literal) }.toRegex()
  }
}

internal fun parseSuppressionPatterns(patterns: Collection<String>): List<SuppressionPattern> =
  patterns.sorted().map { pattern ->
    try {
      SuppressionPattern.parse(pattern)
    }
    catch(e: IllegalArgumentException) {
      throw GradleException("Invalid Compose resource suppression pattern: $pattern", e)
    }
  }

/**
 * The baseline file format: one exact `package: type/name` entry per line, `#` starts a comment,
 * blank lines are ignored. Entries are exact so that stale ones can be detected; patterns belong
 * in the `unusedComposeResources` extension.
 */
internal object BaselineFormat {

  fun parse(text: String): Set<ResourceKey> =
    text.lineSequence()
      .map { line -> line.trim() }
      .filter { line -> line.isNotEmpty() && !line.startsWith("#") }
      .map { line ->
        require('*' !in line) {
          "Baseline entries must be exact; use suppress(...) in the unusedComposeResources " +
            "extension for patterns: $line"
        }
        val (packageFqName, type, name) = splitEntry(line)
        ResourceKey(packageFqName = packageFqName, type = type, name = name)
      }
      .toSet()

  fun serialize(keys: Collection<ResourceKey>): String = buildString {
    appendLine("# Unused Compose resources that findUnusedComposeResources tolerates.")
    appendLine("# Update with: ./gradlew updateComposeUnusedResourcesBaseline")
    keys.sortedWith(ResourceKeyOrder).forEach { key -> appendLine(key.asEntry()) }
  }
}

private fun splitEntry(entry: String): Triple<String, String, String> {
  val rest = entry.substringAfter(':', "").trim()
  val type = rest.substringBefore('/').trim()
  val name = rest.substringAfter('/', "").trim()
  require(':' in entry && type.isNotEmpty() && name.isNotEmpty()) {
    "Malformed resource entry, expected `package: type/name`: $entry"
  }
  return Triple(first = entry.substringBefore(':').trim(), second = type, third = name)
}

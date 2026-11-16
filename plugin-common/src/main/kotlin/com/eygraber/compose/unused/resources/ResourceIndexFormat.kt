package com.eygraber.compose.unused.resources

/**
 * Line-based serialization for a module's resource index. The first line is the [HEADER] version
 * marker, and each record is one line: `D|<packageFqName>|<type>|<name>|<sourceFile>` for a
 * declared accessor, `R|…` for a referenced one. The source file comes last, so a path that
 * contains the separator still parses.
 */
public object ResourceIndexFormat {

  public const val HEADER: String = "compose-unused-resources-index:2"

  private const val FIELD_COUNT = 5

  private val RECORD_ORDER = compareBy<ResourceRecord>(
    { it.kind },
    { it.key.packageFqName },
    { it.key.type },
    { it.key.name },
    { it.sourceFile },
  )

  public fun serialize(records: Collection<ResourceRecord>): String = buildString {
    appendLine(HEADER)
    records.sortedWith(RECORD_ORDER).forEach { appendLine(line(it)) }
  }

  public fun parseRecords(text: String): List<ResourceRecord> {
    val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
    if(lines.isEmpty()) return emptyList()
    require(lines.first() == HEADER) { "Unsupported resource index version: ${lines.first()}" }
    return lines.drop(1).map { rawLine ->
      val parts = rawLine.split('|', limit = FIELD_COUNT)
      require(parts.size == FIELD_COUNT) { "Malformed resource index line: $rawLine" }
      ResourceRecord(
        kind = when(parts[0]) {
          "D" -> ResourceRecord.Kind.Declared
          "R" -> ResourceRecord.Kind.Referenced
          else -> error("Unknown resource index record type: ${parts[0]}")
        },
        key = ResourceKey(packageFqName = parts[1], type = parts[2], name = parts[3]),
        sourceFile = parts[4],
      )
    }
  }

  public fun parse(text: String): ParsedIndex {
    val declared = mutableSetOf<ResourceKey>()
    val referenced = mutableSetOf<ResourceKey>()

    parseRecords(text).forEach { record ->
      when(record.kind) {
        ResourceRecord.Kind.Declared -> declared += record.key
        ResourceRecord.Kind.Referenced -> referenced += record.key
      }
    }

    return ParsedIndex(declared = declared, referenced = referenced)
  }

  private fun line(record: ResourceRecord): String {
    val prefix = when(record.kind) {
      ResourceRecord.Kind.Declared -> "D"
      ResourceRecord.Kind.Referenced -> "R"
    }
    return "$prefix|${record.key.packageFqName}|${record.key.type}|${record.key.name}|${record.sourceFile}"
  }
}

public data class ParsedIndex(
  val declared: Set<ResourceKey>,
  val referenced: Set<ResourceKey>,
)

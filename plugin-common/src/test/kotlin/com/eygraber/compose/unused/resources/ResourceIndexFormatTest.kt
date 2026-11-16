package com.eygraber.compose.unused.resources

import com.eygraber.compose.unused.resources.ResourceRecord.Kind
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.Test

@Suppress("UnnamedParameterUse")
class ResourceIndexFormatTest {
  @Test
  fun `round trips declared and referenced keys`() {
    val collected = CollectedResources().apply {
      records += ResourceRecord(Kind.Declared, ResourceKey("com.example.a", "string", "back"), "Res.kt")
      records += ResourceRecord(Kind.Declared, ResourceKey("com.example.a", "plurals", "items"), "Res.kt")
      records += ResourceRecord(Kind.Referenced, ResourceKey("com.example.a", "string", "back"), "Screen.kt")
    }

    val parsed = ResourceIndexFormat.parse(ResourceIndexFormat.serialize(collected.records))

    parsed.declared.shouldContainExactlyInAnyOrder(
      ResourceKey("com.example.a", "string", "back"),
      ResourceKey("com.example.a", "plurals", "items"),
    )
    parsed.referenced.shouldContainExactlyInAnyOrder(
      ResourceKey("com.example.a", "string", "back"),
    )
  }

  @Test
  fun `parsing ignores blank lines`() {
    val text = "${ResourceIndexFormat.HEADER}\nD|com.example|string|a|Res.kt\n\n  \nR|com.example|string|a|Screen.kt\n"

    val parsed = ResourceIndexFormat.parse(text)

    parsed.declared.shouldContainExactlyInAnyOrder(ResourceKey("com.example", "string", "a"))
    parsed.referenced.shouldContainExactlyInAnyOrder(ResourceKey("com.example", "string", "a"))
  }

  @Test
  fun `a line with the wrong field count throws`() {
    shouldThrow<IllegalArgumentException> {
      ResourceIndexFormat.parse("${ResourceIndexFormat.HEADER}\nD|com.example|string")
    }
  }

  @Test
  fun `a source path containing the separator still parses`() {
    val parsed = ResourceIndexFormat.parseRecords(
      "${ResourceIndexFormat.HEADER}\nR|com.example|string|a|/tmp/od|d/Screen.kt",
    )

    parsed.single().sourceFile shouldBe "/tmp/od|d/Screen.kt"
  }

  @Test
  fun `an unknown record type throws`() {
    shouldThrow<IllegalStateException> {
      ResourceIndexFormat.parse("${ResourceIndexFormat.HEADER}\nX|com.example|string|a|Res.kt")
    }
  }

  @Test
  fun `an index without the current version header throws`() {
    shouldThrow<IllegalArgumentException> {
      ResourceIndexFormat.parse("D|com.example|string|a|Res.kt")
    }
  }

  @Test
  fun `empty text parses to an empty index`() {
    val parsed = ResourceIndexFormat.parse("")

    parsed.declared.shouldBeEmpty()
    parsed.referenced.shouldBeEmpty()
  }
}

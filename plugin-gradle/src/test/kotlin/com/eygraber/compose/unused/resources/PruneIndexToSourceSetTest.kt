package com.eygraber.compose.unused.resources

import com.eygraber.compose.unused.resources.ResourceRecord.Kind
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@Suppress("UnnamedParameterUse")
class PruneIndexToSourceSetTest {
  @get:Rule
  val temp = TemporaryFolder()

  @Test
  fun `drops records of files outside the source set and keeps the rest`() {
    val index = index(
      ResourceRecord(Kind.Declared, ResourceKey("com.example", "string", "a"), "src/Res.kt"),
      ResourceRecord(Kind.Referenced, ResourceKey("com.example", "string", "a"), "src/Screen.kt"),
      ResourceRecord(Kind.Referenced, ResourceKey("com.example", "string", "b"), "src/Excluded.kt"),
    )

    pruneIndexToSourceSet(
      indexFile = index,
      sourceFiles = setOf(File(temp.root, "src/Res.kt"), File(temp.root, "src/Screen.kt")),
      rootDir = temp.root,
    )

    ResourceIndexFormat.parseRecords(index.readText()).map { it.sourceFile }.shouldContainExactly(
      "src/Res.kt",
      "src/Screen.kt",
    )
  }

  @Test
  fun `leaves a missing index alone`() {
    val missing = File(temp.root, "resources.index")

    pruneIndexToSourceSet(indexFile = missing, sourceFiles = emptySet(), rootDir = temp.root)

    missing.exists() shouldBe false
  }

  @Test
  fun `leaves an unparseable index alone`() {
    val index = temp.newFile("resources.index").apply { writeText("not an index") }

    pruneIndexToSourceSet(indexFile = index, sourceFiles = emptySet(), rootDir = temp.root)

    index.readText() shouldBe "not an index"
  }

  private fun index(vararg records: ResourceRecord): File =
    temp.newFile("resources.index").apply {
      writeText(ResourceIndexFormat.serialize(records.toList()))
    }
}

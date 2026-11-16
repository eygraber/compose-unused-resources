package com.eygraber.compose.unused.resources

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@Suppress("UnnamedParameterUse")
class UpdateComposeUnusedResourcesBaselineTaskTest {
  @get:Rule
  val temp = TemporaryFolder()

  @Test
  fun `writes the unused resources as sorted entries that parse back`() {
    val task = task(
      index("a.index", "D|com.z|string|b|Fixture.kt", "D|com.a|string|a|Fixture.kt", "D|com.a|string|used|Fixture.kt"),
      index("b.index", "R|com.a|string|used|Fixture.kt"),
    )

    task.execute()

    val text = task.baselineFile.get().asFile.readText()
    BaselineFormat.parse(text).shouldContainExactlyInAnyOrder(
      ResourceKey("com.a", "string", "a"),
      ResourceKey("com.z", "string", "b"),
    )
    text.lines().filter { it.isNotEmpty() && !it.startsWith("#") }.shouldContainExactly(
      "com.a: string/a",
      "com.z: string/b",
    )
  }

  @Test
  fun `leaves out resources a suppression pattern covers`() {
    val task =
      task(
        index(
          "a.index",
          "D|com.example|drawable|ic_seasonal_sale|Fixture.kt",
          "D|com.example|string|unused|Fixture.kt",
        ),
      )
    task.suppressions.add("com.example: drawable/ic_seasonal_*")

    task.execute()

    BaselineFormat.parse(task.baselineFile.get().asFile.readText()).shouldContainExactlyInAnyOrder(
      ResourceKey("com.example", "string", "unused"),
    )
  }

  @Test
  fun `writes an empty baseline when nothing is unused`() {
    val task = task(index("a.index", "D|com.example|string|used|Fixture.kt", "R|com.example|string|used|Fixture.kt"))

    task.execute()

    BaselineFormat.parse(task.baselineFile.get().asFile.readText()).shouldContainExactlyInAnyOrder()
  }

  private fun index(name: String, vararg lines: String): File =
    temp.newFile(name).apply {
      writeText((listOf(ResourceIndexFormat.HEADER) + lines).joinToString("\n"))
    }

  private fun task(vararg indexes: File): UpdateComposeUnusedResourcesBaselineTask {
    val project = ProjectBuilder.builder().withProjectDir(temp.newFolder()).build()
    val task = project.tasks
      .register("updateBaseline", UpdateComposeUnusedResourcesBaselineTask::class.java)
      .get()
    task.indexFiles.from(indexes.toList())
    task.baselineFile.set(File(temp.root, "baseline.txt"))
    return task
  }
}

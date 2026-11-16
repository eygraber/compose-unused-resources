package com.eygraber.compose.unused.resources

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@Suppress("UnnamedParameterUse")
class FindUnusedComposeResourcesTaskTest {
  @get:Rule
  val temp = TemporaryFolder()

  @Test
  fun `references in one index count against declarations in another`() {
    val a = index("a.index", "D|com.example.a|string|unused|Fixture.kt", "D|com.example.a|string|shared|Fixture.kt")
    val b = index("b.index", "R|com.example.a|string|shared|Fixture.kt")

    unusedComposeResources(listOf(a, b)).shouldContainExactly(
      ResourceKey("com.example.a", "string", "unused"),
    )
  }

  @Test
  fun `unused resources are sorted by package then type then name`() {
    val file = index(
      "a.index",
      "D|com.z|string|a|Fixture.kt",
      "D|com.a|string|z|Fixture.kt",
      "D|com.a|drawable|a|Fixture.kt",
      "D|com.a|string|a|Fixture.kt",
    )

    unusedComposeResources(listOf(file)).shouldContainExactly(
      ResourceKey("com.a", "drawable", "a"),
      ResourceKey("com.a", "string", "a"),
      ResourceKey("com.a", "string", "z"),
      ResourceKey("com.z", "string", "a"),
    )
  }

  @Test
  fun `missing files, directories, and files without the index extension are ignored`() {
    val missing = File(temp.root, "never-written.index")
    val directory = temp.newFolder("dir.index")
    val wrongExtension = index("notes.txt", "D|com.example|string|a|Fixture.kt")

    unusedComposeResources(listOf(missing, directory, wrongExtension)).shouldBeEmpty()
  }

  @Test
  fun `a malformed line fails with the offending file path`() {
    val malformed = index("bad.index", "D|too|few")

    shouldThrow<GradleException> {
      unusedComposeResources(listOf(malformed))
    }.message.shouldContain(malformed.path)
  }

  @Test
  fun `an unknown record type fails with the offending file path`() {
    val malformed = index("bad.index", "X|com.example|string|a|Res.kt")

    shouldThrow<GradleException> {
      unusedComposeResources(listOf(malformed))
    }.message.shouldContain(malformed.path)
  }

  @Test
  fun `check mode fails when unused resources exist`() {
    val task = task(index("a.index", "D|com.example|string|unused|Fixture.kt"))
    task.setCheckOnly(true)

    shouldThrow<GradleException> {
      task.execute()
    }.message.shouldContain("com.example: string/unused")
  }

  @Test
  fun `check mode succeeds when every resource is referenced`() {
    val task = task(index("a.index", "D|com.example|string|used|Fixture.kt", "R|com.example|string|used|Fixture.kt"))
    task.setCheckOnly(true)

    shouldNotThrowAny { task.execute() }
  }

  @Test
  fun `reporting mode does not fail when unused resources exist`() {
    val task = task(index("a.index", "D|com.example|string|unused|Fixture.kt"))

    shouldNotThrowAny { task.execute() }
  }

  @Test
  fun `a baseline entry suppresses its resource but not the others`() {
    val task = task(index("a.index", "D|com.example|string|kept|Fixture.kt", "D|com.example|string|unused|Fixture.kt"))
    task.setCheckOnly(true)
    task.baselineFile.set(baseline("# tolerated", "com.example: string/kept"))

    val message = shouldThrow<GradleException> { task.execute() }.message

    message.shouldContain("string/unused")
    message.shouldContain("(1 suppressed)")
    message.shouldNotContain("string/kept")
  }

  @Test
  fun `check mode succeeds when the baseline suppresses every unused resource`() {
    val task = task(index("a.index", "D|com.example|string|kept|Fixture.kt"))
    task.setCheckOnly(true)
    task.baselineFile.set(baseline("com.example: string/kept"))

    shouldNotThrowAny { task.execute() }
  }

  @Test
  fun `a suppression pattern matches with wildcards inside a segment`() {
    val task = task(
      index(
        "a.index",
        "D|com.example|drawable|ic_seasonal_sale|Fixture.kt",
        "D|com.example|drawable|ic_logo|Fixture.kt",
      ),
    )
    task.setCheckOnly(true)
    task.suppressions.add("com.example: drawable/ic_seasonal_*")

    val message = shouldThrow<GradleException> { task.execute() }.message

    message.shouldContain("drawable/ic_logo")
    message.shouldNotContain("ic_seasonal_sale")
  }

  @Test
  fun `an invalid suppression pattern fails with the pattern`() {
    val task = task(index("a.index", "D|com.example|string|unused|Fixture.kt"))
    task.suppressions.add("garbage")

    shouldThrow<GradleException> { task.execute() }.message.shouldContain("garbage")
  }

  @Test
  fun `a wildcard in a baseline entry fails with the file path`() {
    val task = task(index("a.index", "D|com.example|string|unused|Fixture.kt"))
    val file = baseline("com.example: string/*")
    task.baselineFile.set(file)

    shouldThrow<GradleException> { task.execute() }.message.shouldContain(file.path)
  }

  @Test
  fun `a malformed baseline entry fails with the file path`() {
    val task = task(index("a.index", "D|com.example|string|unused|Fixture.kt"))
    val file = baseline("garbage")
    task.baselineFile.set(file)

    shouldThrow<GradleException> { task.execute() }.message.shouldContain(file.path)
  }

  @Test
  fun `a stale baseline entry does not fail by default`() {
    val task = task(index("a.index", "D|com.example|string|used|Fixture.kt", "R|com.example|string|used|Fixture.kt"))
    task.setCheckOnly(true)
    task.baselineFile.set(baseline("com.example: string/used"))

    shouldNotThrowAny { task.execute() }
  }

  @Test
  fun `a stale baseline entry fails when failOnStaleBaseline is set`() {
    val task = task(index("a.index", "D|com.example|string|used|Fixture.kt", "R|com.example|string|used|Fixture.kt"))
    task.baselineFile.set(baseline("com.example: string/used"))
    task.failOnStaleBaseline.set(true)

    shouldThrow<GradleException> { task.execute() }.message.shouldContain("string/used")
  }

  private fun baseline(vararg lines: String): File =
    temp.newFile("baseline.txt").apply { writeText(lines.joinToString("\n")) }

  private fun index(name: String, vararg lines: String): File =
    temp.newFile(name).apply {
      writeText((listOf(ResourceIndexFormat.HEADER) + lines).joinToString("\n"))
    }

  private fun task(vararg indexes: File): FindUnusedComposeResourcesTask {
    val project = ProjectBuilder.builder().withProjectDir(temp.newFolder()).build()
    val task = project.tasks
      .register("findUnusedComposeResources", FindUnusedComposeResourcesTask::class.java)
      .get()
    task.indexFiles.from(indexes.toList())
    return task
  }
}

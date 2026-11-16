package com.eygraber.compose.unused.resources

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private const val ROOT_PLUGIN_ID = "com.eygraber.compose.unused.resources"
private const val AGGREGATION_TASK = "findUnusedComposeResources"

/** Prints the outgoing index artifacts relative to the build dir, without running any compilation. */
private val DUMP_TASK = """
  val buildDirFile = layout.buildDirectory.get().asFile

  tasks.register("dumpIndexArtifacts") {
    doLast {
      configurations["$COMPOSE_RESOURCE_INDEX_CONFIGURATION"].outgoing.artifacts.files.files
        .map { file -> file.relativeTo(buildDirFile).invariantSeparatorsPath }
        .sorted()
        .forEach { path -> println("INDEX:" + path) }
    }
  }
""".trimIndent()

/** Prints a compilation's declared output files relative to the build dir. */
private val DUMP_OUTPUTS_TASK = """
  val buildDirFile = layout.buildDirectory.get().asFile

  tasks.register("dumpCompileOutputs") {
    doLast {
      tasks.named("compileKotlin").get().outputs.files.files
        .map { file -> file.relativeTo(buildDirFile).invariantSeparatorsPath }
        .sorted()
        .forEach { path -> println("OUTPUT:" + path) }
    }
  }
""".trimIndent()

@Suppress("UnnamedParameterUse")
class ComposeUnusedResourcesFunctionalTest {
  @get:Rule
  val projectDir = TemporaryFolder()

  @Test
  fun `a compilation declares its index as an output so the build cache restores it`() {
    write("settings.gradle.kts", """rootProject.name = "fixture"""")
    write(
      "build.gradle.kts",
      """
      plugins {
        id("org.jetbrains.kotlin.jvm")
        id("$COMPILER_PLUGIN_ID")
      }
      """.trimIndent() + "\n\n" + DUMP_OUTPUTS_TASK,
    )

    val result = runner("dumpCompileOutputs", "-q").build()

    result.output.shouldContain("OUTPUT:compose-unused-resources/kotlin/main")
  }

  @Test
  fun `a file dropped from the source set stops contributing records`() {
    val compilerPluginJars = System.getProperty("testCompilerPluginClasspath")
      .split(File.pathSeparator)
      .joinToString { path -> "\"$path\"" }

    write("settings.gradle.kts", """rootProject.name = "fixture"""")
    write(
      "build.gradle.kts",
      """
      plugins {
        id("org.jetbrains.kotlin.jvm")
        id("$COMPILER_PLUGIN_ID")
      }

      repositories {
        mavenCentral()
      }

      configurations.matching { it.name.startsWith("kotlinCompilerPluginClasspath") }.configureEach {
        exclude(group = "com.eygraber")
      }

      dependencies {
        "kotlinCompilerPluginClasspathMain"(files($compilerPluginJars))
      }

      if(providers.gradleProperty("dropOther").isPresent) {
        kotlin.sourceSets["main"].kotlin.exclude("**/Other.kt")
      }
      """.trimIndent(),
    )
    write(
      "src/main/kotlin/ComposeResources.kt",
      """
      package org.jetbrains.compose.resources
      class StringResource
      class DrawableResource
      """.trimIndent(),
    )
    write(
      "src/main/kotlin/Res.kt",
      """
      package com.example.feature

      import org.jetbrains.compose.resources.DrawableResource
      import org.jetbrains.compose.resources.StringResource

      object Res {
        object string
        object drawable
      }

      val Res.string.used: StringResource get() = StringResource()
      val Res.drawable.icon: DrawableResource get() = DrawableResource()
      """.trimIndent(),
    )
    write(
      "src/main/kotlin/Screen.kt",
      """
      package com.example.feature

      fun render(): Any = Res.string.used
      """.trimIndent(),
    )
    write(
      "src/main/kotlin/Other.kt",
      """
      package com.example.feature

      fun renderOther(): Any = Res.drawable.icon
      """.trimIndent(),
    )

    runner("compileKotlin").build()

    val index = File(projectDir.root, "build/compose-unused-resources/kotlin/main/resources.index")
    val firstRound = ResourceIndexFormat.parseRecords(index.readText())
    firstRound.map { record -> record.key }.shouldContain(ResourceKey("com.example.feature", "drawable", "icon"))
    firstRound.map { record -> record.sourceFile }.shouldContain("src/main/kotlin/Other.kt")

    runner("compileKotlin", "-PdropOther=true").build()

    val secondRound = ResourceIndexFormat.parseRecords(index.readText())
    secondRound
      .filter { record -> record.kind == ResourceRecord.Kind.Referenced }
      .map { record -> record.key }
      .shouldContain(ResourceKey("com.example.feature", "string", "used"))
    secondRound.map { record -> record.sourceFile }.shouldNotContain("src/main/kotlin/Other.kt")
  }

  @Test
  fun `a kotlin jvm project registers one index artifact per compilation`() {
    write("settings.gradle.kts", """rootProject.name = "fixture"""")
    write(
      "build.gradle.kts",
      """
      plugins {
        id("org.jetbrains.kotlin.jvm")
        id("$COMPILER_PLUGIN_ID")
      }
      """.trimIndent() + "\n\n" + DUMP_TASK,
    )

    val result = runner("dumpIndexArtifacts", "-q").build()

    result.output.shouldContain("INDEX:compose-unused-resources/kotlin/main/resources.index")
    result.output.shouldContain("INDEX:compose-unused-resources/kotlin/test/resources.index")
  }

  @Test
  fun `a multiplatform project registers distinct index artifacts for same-named compilations`() {
    write("settings.gradle.kts", """rootProject.name = "fixture"""")
    write(
      "build.gradle.kts",
      """
      plugins {
        id("org.jetbrains.kotlin.multiplatform")
        id("$COMPILER_PLUGIN_ID")
      }

      kotlin {
        jvm()
      }
      """.trimIndent() + "\n\n" + DUMP_TASK,
    )

    val result = runner("dumpIndexArtifacts", "-q").build()

    result.output.shouldContain("INDEX:compose-unused-resources/metadata/main/resources.index")
    result.output.shouldContain("INDEX:compose-unused-resources/jvm/main/resources.index")
    result.output.shouldContain("INDEX:compose-unused-resources/jvm/test/resources.index")
  }

  @Test
  fun `aggregation reports resources no module references and skips modules without an index`() {
    writeAggregationFixture(
      aIndexLines = listOf(
        "D|com.example.a|string|unused_a|Fixture.kt",
        "D|com.example.a|string|shared|Fixture.kt",
      ),
      bIndexLines = listOf("R|com.example.a|string|shared|Fixture.kt"),
    )

    val result = runner(AGGREGATION_TASK).build()

    result.task(":$AGGREGATION_TASK")?.outcome shouldBe TaskOutcome.SUCCESS
    result.output.shouldContain("Found 1 unused Compose resource(s):")
    result.output.shouldContain("com.example.a: string/unused_a")
    result.output.shouldNotContain("string/shared")
  }

  @Test
  fun `aggregation succeeds when every resource is referenced`() {
    writeAggregationFixture(
      aIndexLines = listOf("D|com.example.a|string|shared|Fixture.kt"),
      bIndexLines = listOf("R|com.example.a|string|shared|Fixture.kt"),
    )

    val result = runner(AGGREGATION_TASK).build()

    result.output.shouldContain("No unused Compose resources found.")
  }

  @Test
  fun `check mode fails the build when unused resources exist`() {
    writeAggregationFixture(
      aIndexLines = listOf("D|com.example.a|string|unused_a|Fixture.kt"),
      bIndexLines = emptyList(),
    )

    val result = runner(AGGREGATION_TASK, "--check").buildAndFail()

    result.task(":$AGGREGATION_TASK")?.outcome shouldBe TaskOutcome.FAILED
    result.output.shouldContain("Found 1 unused Compose resource(s):")
  }

  @Test
  fun `a baseline entry keeps check mode green`() {
    writeAggregationFixture(
      aIndexLines = listOf("D|com.example.a|string|unused_a|Fixture.kt"),
      bIndexLines = emptyList(),
    )
    write("compose-unused-resources-baseline.txt", "com.example.a: string/unused_a")

    val result = runner(AGGREGATION_TASK, "--check").build()

    result.output.shouldContain("No unused Compose resources found. (1 suppressed)")
  }

  @Test
  fun `a suppression pattern from the extension keeps check mode green`() {
    writeAggregationFixture(
      aIndexLines = listOf("D|com.example.a|string|unused_a|Fixture.kt"),
      bIndexLines = emptyList(),
      rootExtras = """
      unusedComposeResources {
        suppress("com.example.a: string/unused_*")
      }
      """.trimIndent(),
    )

    val result = runner(AGGREGATION_TASK, "--check").build()

    result.output.shouldContain("No unused Compose resources found. (1 suppressed)")
  }

  @Test
  fun `the update task writes a baseline that the check then accepts`() {
    writeAggregationFixture(
      aIndexLines = listOf("D|com.example.a|string|unused_a|Fixture.kt"),
      bIndexLines = emptyList(),
    )

    runner("updateComposeUnusedResourcesBaseline").build()

    val baseline = File(projectDir.root, "compose-unused-resources-baseline.txt").readText()
    baseline.shouldContain("com.example.a: string/unused_a")

    val result = runner(AGGREGATION_TASK, "--check").build()
    result.output.shouldContain("No unused Compose resources found. (1 suppressed)")
  }

  private fun runner(vararg arguments: String): GradleRunner =
    GradleRunner.create()
      .withProjectDir(projectDir.root)
      .withPluginClasspath()
      .withArguments(*arguments, "--stacktrace")

  private fun write(path: String, content: String): File =
    File(projectDir.root, path).apply {
      parentFile.mkdirs()
      writeText(content + "\n")
    }

  /**
   * A root project applying the aggregation plugin over two fake producers that expose handwritten
   * indexes the way the compilation plugin does, plus a module with no index configuration at all.
   */
  private fun writeAggregationFixture(
    aIndexLines: List<String>,
    bIndexLines: List<String>,
    rootExtras: String = "",
  ) {
    write(
      "settings.gradle.kts",
      """
      rootProject.name = "fixture"

      include(":a")
      include(":b")
      include(":no-index")
      """.trimIndent(),
    )
    write(
      "build.gradle.kts",
      """
      plugins {
        id("$ROOT_PLUGIN_ID")
      }
      """.trimIndent() + "\n\n" + rootExtras,
    )
    write("a/build.gradle.kts", producerBuildScript(aIndexLines))
    write("b/build.gradle.kts", producerBuildScript(bIndexLines))
    File(projectDir.root, "no-index").mkdirs()
  }

  private fun producerBuildScript(indexLines: List<String>): String {
    val content = when {
      indexLines.isEmpty() -> ""
      else -> (listOf(ResourceIndexFormat.HEADER) + indexLines).joinToString("\\n")
    }
    val writeTask = """
    val writeIndex by tasks.registering {
      val indexFile = layout.buildDirectory.file("index/" + project.name + ".index")
      outputs.file(indexFile)
      doLast {
        indexFile.get().asFile.writeText("$content")
      }
    }
    """.trimIndent()
    val configuration = """
    configurations.consumable("$COMPOSE_RESOURCE_INDEX_CONFIGURATION") {
      attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, "compose-resource-index"))
      outgoing.artifact(writeIndex.map { task -> task.outputs.files.singleFile })
    }
    """.trimIndent()
    return writeTask + "\n\n" + configuration
  }
}

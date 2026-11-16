@file:OptIn(ExperimentalCompilerApi::class)

package com.eygraber.compose.unused.resources

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import java.io.File

@Suppress("UnnamedParameterUse")
class ComposeUnusedResourcesPluginEndToEndTest {
  @Test
  fun `compiler plugin writes the resource index to the configured output file`() {
    val tempDir = File.createTempFile("compose-resource-index", "").apply {
      delete()
      mkdirs()
    }
    val output = File(tempDir, "resource-index.txt")

    val result = KotlinCompilation().apply {
      sources = listOf(RES_STUB, ACCESSORS, USAGE)
      commandLineProcessors = listOf(ComposeUnusedResourcesCommandLineProcessor())
      compilerPluginRegistrars = listOf(ComposeUnusedResourcesCompilerPluginRegistrar())
      pluginOptions = listOf(
        PluginOption(COMPILER_PLUGIN_ID, COMPILER_PLUGIN_OPTION_OUTPUT_FILE, output.absolutePath),
      )
      inheritClassPath = true
      messageOutputStream = System.out
    }.compile()

    withClue(result.messages) { result.exitCode shouldBe KotlinCompilation.ExitCode.OK }
    output.shouldExist()

    val parsed = ResourceIndexFormat.parse(output.readText())
    parsed.declared.shouldContainExactlyInAnyOrder(
      ResourceKey("com.example.feature", "string", "used"),
      ResourceKey("com.example.feature", "string", "unused"),
      ResourceKey("com.example.feature", "drawable", "icon"),
      ResourceKey("com.example.feature", "font", "title"),
    )
    parsed.referenced.shouldContainExactlyInAnyOrder(
      ResourceKey("com.example.feature", "string", "used"),
      ResourceKey("com.example.feature", "drawable", "icon"),
    )
  }

  @Test
  fun `a round that recompiles some files keeps the records of the rest`() {
    val dir = File.createTempFile("compose-resource-index-incremental", "").apply {
      delete()
      mkdirs()
    }
    val stub = write(dir, "ComposeResources.kt", RES_STUB_TEXT)
    val accessors = write(dir, "Res.kt", ACCESSORS_TEXT)
    val screen = write(dir, "Screen.kt", SCREEN_TEXT)
    val other = write(dir, "Other.kt", OTHER_TEXT)
    val output = File(dir, "resources.index")

    compileFiles(listOf(stub, accessors, screen, other), output, rootDir = dir)

    val firstRound = ResourceIndexFormat.parseRecords(output.readText())
    firstRound.map { it.sourceFile }.shouldContain("Other.kt")
    ResourceIndexFormat.parse(output.readText()).referenced.shouldContainExactlyInAnyOrder(
      ResourceKey("com.example.feature", "string", "used"),
      ResourceKey("com.example.feature", "drawable", "icon"),
    )

    // Stands in for an incremental round: Other.kt still exists but was not recompiled, so its
    // reference has to survive even though this compilation never saw it.
    compileFiles(listOf(stub, accessors, screen), output, rootDir = dir)

    ResourceIndexFormat.parse(output.readText()).referenced.shouldContainExactlyInAnyOrder(
      ResourceKey("com.example.feature", "string", "used"),
      ResourceKey("com.example.feature", "drawable", "icon"),
    )
  }

  @Test
  fun `a round drops the records of a file that no longer exists`() {
    val dir = File.createTempFile("compose-resource-index-deleted", "").apply {
      delete()
      mkdirs()
    }
    val stub = write(dir, "ComposeResources.kt", RES_STUB_TEXT)
    val accessors = write(dir, "Res.kt", ACCESSORS_TEXT)
    val screen = write(dir, "Screen.kt", SCREEN_TEXT)
    val other = write(dir, "Other.kt", OTHER_TEXT)
    val output = File(dir, "resources.index")

    compileFiles(listOf(stub, accessors, screen, other), output, rootDir = dir)
    other.delete()
    compileFiles(listOf(stub, accessors, screen), output, rootDir = dir)

    ResourceIndexFormat.parse(output.readText()).referenced.shouldContainExactlyInAnyOrder(
      ResourceKey("com.example.feature", "string", "used"),
    )
  }

  private fun write(dir: File, name: String, content: String): File =
    File(dir, name).apply { writeText(content) }

  // fromPath keeps each round pointing at the same source paths, which is what the carry-over keys
  // on; its deprecation is about KSP, which this test does not use.
  @Suppress("DEPRECATION")
  private fun compileFiles(files: List<File>, output: File, rootDir: File? = null) {
    val result = KotlinCompilation().apply {
      sources = files.map { SourceFile.fromPath(it) }
      commandLineProcessors = listOf(ComposeUnusedResourcesCommandLineProcessor())
      compilerPluginRegistrars = listOf(ComposeUnusedResourcesCompilerPluginRegistrar())
      pluginOptions = listOfNotNull(
        PluginOption(COMPILER_PLUGIN_ID, COMPILER_PLUGIN_OPTION_OUTPUT_FILE, output.absolutePath),
        rootDir?.let { PluginOption(COMPILER_PLUGIN_ID, COMPILER_PLUGIN_OPTION_ROOT_DIR, it.absolutePath) },
      )
      inheritClassPath = true
      messageOutputStream = System.out
    }.compile()

    withClue(result.messages) { result.exitCode shouldBe KotlinCompilation.ExitCode.OK }
  }

  private companion object {
    val RES_STUB = SourceFile.kotlin(
      "ComposeResources.kt",
      """
      package org.jetbrains.compose.resources
      class StringResource
      class PluralStringResource
      class StringArrayResource
      class DrawableResource
      class FontResource
      """.trimIndent(),
    )

    val ACCESSORS = SourceFile.kotlin(
      "Res.kt",
      """
      package com.example.feature

      import org.jetbrains.compose.resources.DrawableResource
      import org.jetbrains.compose.resources.FontResource
      import org.jetbrains.compose.resources.StringResource

      object Res {
        object string
        object drawable
        object font
      }

      val Res.string.used: StringResource get() = StringResource()
      val Res.string.unused: StringResource get() = StringResource()
      val Res.drawable.icon: DrawableResource get() = DrawableResource()
      val Res.font.title: FontResource get() = FontResource()
      """.trimIndent(),
    )

    val USAGE = SourceFile.kotlin(
      "Screen.kt",
      """
      package com.example.feature

      fun render(): Any = listOf(Res.string.used, Res.drawable.icon)
      """.trimIndent(),
    )

    val RES_STUB_TEXT =
      """
      package org.jetbrains.compose.resources
      class StringResource
      class PluralStringResource
      class StringArrayResource
      class DrawableResource
      class FontResource
      """.trimIndent()

    val ACCESSORS_TEXT =
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
      """.trimIndent()

    val SCREEN_TEXT =
      """
      package com.example.feature

      fun render(): Any = Res.string.used
      """.trimIndent()

    val OTHER_TEXT =
      """
      package com.example.feature

      fun renderOther(): Any = Res.drawable.icon
      """.trimIndent()
  }
}

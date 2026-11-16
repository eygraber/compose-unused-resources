@file:OptIn(ExperimentalCompilerApi::class)

package com.eygraber.compose.unused.resources

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.junit.Test
import java.io.File

@Suppress("UnnamedParameterUse")
class ComposeUnusedResourcesIrExtensionTest {
  @Test
  fun `resolves declared and referenced accessors by symbol and reports the unused one`() {
    val collected = CollectedResources()

    val result = compile(collected, RES_STUB, GENERATED_ACCESSORS, USAGE)

    withClue(result.messages) { result.exitCode shouldBe KotlinCompilation.ExitCode.OK }

    collected.declared.shouldContainExactlyInAnyOrder(
      ResourceKey("com.example.feature", "string", "used"),
      ResourceKey("com.example.feature", "string", "unused"),
      ResourceKey("com.example.feature", "plurals", "items"),
      ResourceKey("com.example.feature", "drawable", "logo"),
      ResourceKey("com.example.feature", "drawable", "decoration"),
      ResourceKey("com.example.feature", "font", "heading"),
    )
    collected.referenced shouldContain ResourceKey("com.example.feature", "string", "used")
    collected.referenced shouldContain ResourceKey("com.example.feature", "drawable", "logo")
    collected.unused().shouldContainExactlyInAnyOrder(
      ResourceKey("com.example.feature", "string", "unused"),
      ResourceKey("com.example.feature", "plurals", "items"),
      ResourceKey("com.example.feature", "drawable", "decoration"),
      ResourceKey("com.example.feature", "font", "heading"),
    )
  }

  @Test
  fun `same resource name in two packages resolves to distinct symbols and only the unreferenced one is unused`() {
    val collected = CollectedResources()

    val result = compile(
      collected,
      RES_STUB,
      ACCESSORS_PACKAGE_A,
      ACCESSORS_PACKAGE_B,
      CROSS_PACKAGE_USAGE,
    )

    withClue(result.messages) { result.exitCode shouldBe KotlinCompilation.ExitCode.OK }

    collected.unused().shouldContainExactlyInAnyOrder(
      ResourceKey("com.example.b", "string", "back"),
    )
  }

  @Test
  fun `a reference to an accessor from another module records its declaring package`() {
    val commonModule = compile(CollectedResources(), RES_STUB, COMMON_MODULE_ACCESSORS)

    withClue(commonModule.messages) { commonModule.exitCode shouldBe KotlinCompilation.ExitCode.OK }

    val collected = CollectedResources()

    val result = compile(
      collected,
      ALIASED_CROSS_MODULE_USAGE,
      PLAIN_CROSS_MODULE_USAGE,
      classpaths = listOf(commonModule.outputDirectory),
    )

    withClue(result.messages) { result.exitCode shouldBe KotlinCompilation.ExitCode.OK }

    collected.referenced.shouldContainExactlyInAnyOrder(
      ResourceKey("com.foo.common", "string", "common_ok"),
      ResourceKey("com.foo.common", "string", "common_no"),
    )
  }

  @Test
  fun `a reference from the compose generator's own output does not count as a usage`() {
    val collected = CollectedResources()

    val result = compile(collected, RES_STUB, GENERATED_ACCESSORS, GENERATED_COLLECTOR)

    withClue(result.messages) { result.exitCode shouldBe KotlinCompilation.ExitCode.OK }

    collected.unused().shouldContainExactlyInAnyOrder(
      ResourceKey("com.example.feature", "string", "used"),
      ResourceKey("com.example.feature", "string", "unused"),
      ResourceKey("com.example.feature", "plurals", "items"),
      ResourceKey("com.example.feature", "drawable", "logo"),
      ResourceKey("com.example.feature", "drawable", "decoration"),
      ResourceKey("com.example.feature", "font", "heading"),
    )
  }

  @Test
  fun `a property of a resource type that is not a generated accessor is neither declared nor referenced`() {
    val collected = CollectedResources()

    val result = compile(
      collected,
      RES_STUB,
      GENERATED_ACCESSORS,
      RESOURCE_TYPED_PROPERTIES,
      RESOURCE_TYPED_TEST_FAKE,
    )

    withClue(result.messages) { result.exitCode shouldBe KotlinCompilation.ExitCode.OK }

    collected.declared.shouldContainExactlyInAnyOrder(
      ResourceKey("com.example.feature", "string", "used"),
      ResourceKey("com.example.feature", "string", "unused"),
      ResourceKey("com.example.feature", "plurals", "items"),
      ResourceKey("com.example.feature", "drawable", "logo"),
      ResourceKey("com.example.feature", "drawable", "decoration"),
      ResourceKey("com.example.feature", "font", "heading"),
    )
    collected.referenced.shouldContainExactlyInAnyOrder(
      ResourceKey("com.example.feature", "string", "used"),
    )
  }

  private fun compile(
    collected: CollectedResources,
    vararg sources: SourceFile,
    classpaths: List<File> = emptyList(),
  ): JvmCompilationResult =
    KotlinCompilation().apply {
      this.sources = sources.toList()
      this.classpaths = classpaths
      compilerPluginRegistrars = listOf(registrarFor(collected))
      inheritClassPath = true
      messageOutputStream = System.out
    }.compile()

  private fun registrarFor(collected: CollectedResources): CompilerPluginRegistrar =
    object : CompilerPluginRegistrar() {
      override val pluginId: String = "compose-resources-detector-test"

      override val supportsK2: Boolean = true

      override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(ComposeUnusedResourcesIrExtension(collected))
      }
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

    val GENERATED_ACCESSORS = SourceFile.kotlin(
      "Res.kt",
      """
      package com.example.feature

      import org.jetbrains.compose.resources.DrawableResource
      import org.jetbrains.compose.resources.FontResource
      import org.jetbrains.compose.resources.PluralStringResource
      import org.jetbrains.compose.resources.StringResource

      object Res {
        object string
        object plurals
        object drawable
        object font
      }

      val Res.string.used: StringResource get() = StringResource()
      val Res.string.unused: StringResource get() = StringResource()
      val Res.plurals.items: PluralStringResource get() = PluralStringResource()
      val Res.drawable.logo: DrawableResource get() = DrawableResource()
      val Res.drawable.decoration: DrawableResource get() = DrawableResource()
      val Res.font.heading: FontResource get() = FontResource()
      """.trimIndent(),
    )

    val USAGE = SourceFile.kotlin(
      "Screen.kt",
      """
      package com.example.feature

      fun render(): Any {
        // a commented reference to Res.string.unused must NOT count as a usage
        return listOf(Res.string.used, Res.drawable.logo)
      }
      """.trimIndent(),
    )

    val RESOURCE_TYPED_PROPERTIES = SourceFile.kotlin(
      "DebugAction.kt",
      """
      package com.example.feature

      import org.jetbrains.compose.resources.StringResource

      interface DebugAction {
        val label: StringResource
        val doneMessage: StringResource
      }

      class ResetDebugAction : DebugAction {
        override val label = Res.string.used
        override val doneMessage: StringResource get() = StringResource()
      }

      val unused: StringResource = StringResource()

      fun readAll(action: DebugAction): List<Any> = listOf(
        action.label,
        action.doneMessage,
        unused,
      )
      """.trimIndent(),
    )

    val RESOURCE_TYPED_TEST_FAKE = SourceFile.kotlin(
      "src/commonTest/kotlin/FakeDebugAction.kt",
      """
      package com.example.feature

      import org.jetbrains.compose.resources.StringResource

      class FakeDebugAction : DebugAction {
        override val label: StringResource = StringResource()
        override val doneMessage: StringResource = StringResource()
      }
      """.trimIndent(),
    )

    val GENERATED_COLLECTOR = SourceFile.kotlin(
      "build/generated/compose/resourceGenerator/kotlin/ResourceCollectors.kt",
      """
      package com.example.feature

      fun collectAll(): List<Any> = listOf(
        Res.string.used,
        Res.string.unused,
        Res.plurals.items,
        Res.drawable.logo,
        Res.drawable.decoration,
        Res.font.heading,
      )
      """.trimIndent(),
    )

    val ACCESSORS_PACKAGE_A = SourceFile.kotlin(
      "ResA.kt",
      """
      package com.example.a

      import org.jetbrains.compose.resources.StringResource

      object Res { object string }
      val Res.string.back: StringResource get() = StringResource()
      """.trimIndent(),
    )

    val ACCESSORS_PACKAGE_B = SourceFile.kotlin(
      "ResB.kt",
      """
      package com.example.b

      import org.jetbrains.compose.resources.StringResource

      object Res { object string }
      val Res.string.back: StringResource get() = StringResource()
      """.trimIndent(),
    )

    val COMMON_MODULE_ACCESSORS = SourceFile.kotlin(
      "CommonRes.kt",
      """
      package com.foo.common

      import org.jetbrains.compose.resources.StringResource

      object Res { object string }

      val Res.string.common_ok: StringResource get() = StringResource()
      val Res.string.common_no: StringResource get() = StringResource()
      """.trimIndent(),
    )

    val ALIASED_CROSS_MODULE_USAGE = SourceFile.kotlin(
      "AliasedScreen.kt",
      """
      package com.example.consumer

      import com.foo.common.Res as CommonRes
      import com.foo.common.common_ok

      fun renderAliased(): Any = CommonRes.string.common_ok
      """.trimIndent(),
    )

    val PLAIN_CROSS_MODULE_USAGE = SourceFile.kotlin(
      "PlainScreen.kt",
      """
      package com.example.consumer2

      import com.foo.common.Res
      import com.foo.common.common_no

      fun renderPlain(): Any = Res.string.common_no
      """.trimIndent(),
    )

    val CROSS_PACKAGE_USAGE = SourceFile.kotlin(
      "CrossPackageScreen.kt",
      """
      package com.example.consumer

      import com.example.a.Res
            import com.example.a.back

      fun render(): Any = Res.string.back
      """.trimIndent(),
    )
  }
}

package com.eygraber.compose.unused.resources

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

public val OutputFileKey: CompilerConfigurationKey<String> =
  CompilerConfigurationKey.create("compose resource index output file")

public val RootDirKey: CompilerConfigurationKey<String> =
  CompilerConfigurationKey.create("compose resource index root dir")

@OptIn(ExperimentalCompilerApi::class)
public class ComposeUnusedResourcesCommandLineProcessor : CommandLineProcessor {
  override val pluginId: String = COMPILER_PLUGIN_ID

  override val pluginOptions: Collection<AbstractCliOption> = listOf(OUTPUT_FILE_OPTION, ROOT_DIR_OPTION)

  override fun processOption(
    option: AbstractCliOption,
    value: String,
    configuration: CompilerConfiguration,
  ) {
    when(option.optionName) {
      COMPILER_PLUGIN_OPTION_OUTPUT_FILE -> configuration.put(OutputFileKey, value)
      COMPILER_PLUGIN_OPTION_ROOT_DIR -> configuration.put(RootDirKey, value)
      else -> error("Unexpected compose-resources-detector option: ${option.optionName}")
    }
  }

  private companion object {
    val OUTPUT_FILE_OPTION = CliOption(
      optionName = COMPILER_PLUGIN_OPTION_OUTPUT_FILE,
      valueDescription = "<path>",
      description = "File to write this compilation's Compose resource index to.",
      required = true,
      allowMultipleOccurrences = false,
    )

    val ROOT_DIR_OPTION = CliOption(
      optionName = COMPILER_PLUGIN_OPTION_ROOT_DIR,
      valueDescription = "<path>",
      description = "Directory that recorded source paths are stored relative to.",
      required = false,
      allowMultipleOccurrences = false,
    )
  }
}

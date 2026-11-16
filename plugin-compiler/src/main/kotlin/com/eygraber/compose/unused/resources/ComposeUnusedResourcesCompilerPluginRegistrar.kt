package com.eygraber.compose.unused.resources

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.io.File

@OptIn(ExperimentalCompilerApi::class)
public class ComposeUnusedResourcesCompilerPluginRegistrar : CompilerPluginRegistrar() {
  override val pluginId: String = COMPILER_PLUGIN_ID

  override val supportsK2: Boolean = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    val outputPath = configuration[OutputFileKey] ?: return
    IrGenerationExtension.registerExtension(
      ComposeUnusedResourcesIrExtension(
        collected = CollectedResources(),
        outputFile = File(outputPath),
        rootDir = configuration[RootDirKey]?.let(::File),
      ),
    )
  }
}

@file:Suppress("DEPRECATION")

package com.eygraber.compose.unused.resources

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import java.io.File
import java.io.IOException

/**
 * Walks a module's resolved IR and records the Compose resource accessors it declares and the ones
 * it references, each keyed by its resolved declaring symbol so cross-module references and
 * same-named accessors resolve correctly.
 *
 * References from the Compose generator's own output are ignored — its resource collectors touch
 * every accessor, which would otherwise mark everything as referenced. Declarations are kept there,
 * since the accessors themselves are generated.
 *
 * When [outputFile] is set, the result is serialized to it. The in-process test reads [collected].
 * When [rootDir] is set, recorded source paths are relative to it, which keeps the index content
 * identical across checkout locations.
 */
public class ComposeUnusedResourcesIrExtension(
  private val collected: CollectedResources,
  private val outputFile: File? = null,
  private val rootDir: File? = null,
) : IrGenerationExtension {

  @OptIn(UnsafeDuringIrConstructionAPI::class)
  override fun generate(
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
  ) {
    moduleFragment.acceptVoid(object : IrVisitorVoid() {
      private var isInGeneratedResourceFile = false
      private var sourceFile = ""

      override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
      }

      override fun visitFile(declaration: IrFile) {
        isInGeneratedResourceFile = declaration.isComposeGeneratedFile()
        sourceFile = declaration.sourcePath()
        super.visitFile(declaration)
      }

      override fun visitProperty(declaration: IrProperty) {
        declaration.asResourceKey()?.let {
          collected.records += ResourceRecord(ResourceRecord.Kind.Declared, it, sourceFile)
        }
        super.visitProperty(declaration)
      }

      override fun visitCall(expression: IrCall) {
        if(!isInGeneratedResourceFile) {
          expression.symbol.owner.correspondingPropertySymbol?.owner
            ?.asResourceKey()
            ?.let {
              collected.records += ResourceRecord(ResourceRecord.Kind.Referenced, it, sourceFile)
            }
        }
        super.visitCall(expression)
      }
    })

    outputFile?.let { file ->
      file.parentFile?.mkdirs()
      file.writeText(ResourceIndexFormat.serialize(carriedOverRecords(file, moduleFragment) + collected.records))
    }
  }

  /**
   * Records from a previous run that this compilation did not revisit. An incremental compilation
   * only sees the files it recompiled, so without carrying the rest over the index would shrink to
   * whatever that round happened to touch and everything else would look unreferenced. Records of
   * files the compilation did recompile are dropped, since [collected] now holds their current
   * state, and so are records of files that no longer exist.
   */
  private fun carriedOverRecords(file: File, moduleFragment: IrModuleFragment): List<ResourceRecord> {
    if(!file.isFile) return emptyList()

    val recompiled = moduleFragment.files.mapTo(mutableSetOf()) { it.sourcePath() }
    val previous = try {
      ResourceIndexFormat.parseRecords(file.readText())
    }
    catch(_: IOException) {
      return emptyList()
    }
    catch(_: IllegalArgumentException) {
      // An index left by an older plugin version; start over rather than fail the compilation.
      return emptyList()
    }
    catch(_: IllegalStateException) {
      return emptyList()
    }

    return previous.filter { it.sourceFile !in recompiled && resolveSourcePath(it.sourceFile).exists() }
  }

  private fun IrFile.sourcePath(): String {
    val file = File(fileEntry.name)
    return (rootDir?.let(file::relativeToOrSelf) ?: file).invariantSeparatorsPath
  }

  private fun resolveSourcePath(sourcePath: String): File =
    rootDir?.resolve(sourcePath) ?: File(sourcePath)

  private fun IrFile.isComposeGeneratedFile(): Boolean =
    GENERATED_RESOURCE_PATH_MARKER in fileEntry.name.replace(File.separatorChar, '/')

  @OptIn(UnsafeDuringIrConstructionAPI::class)
  private fun IrProperty.asResourceKey(): ResourceKey? {
    val getter = getter ?: return null
    val returnTypeFqn = getter.returnType.classFqName?.asString() ?: return null
    val type = RESOURCE_TYPE_BY_FQN[returnTypeFqn] ?: return null
    val receiver = getter.parameters
      .firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }
      ?.type
      ?.classOrNull
      ?.owner
      ?: return null
    if(!receiver.isResourceTypeHolder(type)) return null
    return ResourceKey(
      packageFqName = getPackageFragment().packageFqName.asString(),
      type = type,
      name = name.asString(),
    )
  }

  private fun IrClass.isResourceTypeHolder(type: String): Boolean =
    isObject && name.asString() == type && (parent as? IrClass)?.isObject == true

  private companion object {
    const val GENERATED_RESOURCE_PATH_MARKER = "generated/compose/resourceGenerator"

    val RESOURCE_TYPE_BY_FQN = mapOf(
      "org.jetbrains.compose.resources.StringResource" to "string",
      "org.jetbrains.compose.resources.PluralStringResource" to "plurals",
      "org.jetbrains.compose.resources.StringArrayResource" to "array",
      "org.jetbrains.compose.resources.DrawableResource" to "drawable",
      "org.jetbrains.compose.resources.FontResource" to "font",
    )
  }
}

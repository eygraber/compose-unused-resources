plugins {
  `kotlin-dsl`
  id("com.eygraber.conventions-kotlin-library")
  id("com.eygraber.conventions-detekt2")
  id("com.eygraber.conventions-publish-maven-central")
}

// The Kotlin Gradle plugin goes on the plugin-under-test classpath so functional test fixtures can
// apply it from the same classloader as the plugins under test, which is compiled against it.
val testKotlinGradlePlugin = configurations.register("testKotlinGradlePlugin")

// Pass the compiler plugin jars directly to functional test fixtures instead of relying on a published version.
val testCompilerPlugin = configurations.register("testCompilerPlugin")

dependencies {
  compileOnly(libs.kotlin.gradlePluginApi)

  implementation(projects.pluginCommon)

  testImplementation(libs.kotlin.gradlePluginApi)
  testImplementation(libs.test.junit)
  testImplementation(libs.test.kotest.assertions.core)

  testKotlinGradlePlugin(libs.buildscript.kotlin)
  testCompilerPlugin(projects.pluginCompiler)
}

tasks.named<Test>("test") {
  inputs.files(testCompilerPlugin).withPropertyName("testCompilerPlugin")
  val compilerPluginClasspath = testCompilerPlugin.get().elements.map { jars ->
    jars.joinToString(File.pathSeparator) { jar -> jar.asFile.absolutePath }
  }
  jvmArgumentProviders.add(
    CommandLineArgumentProvider {
      listOf("-DtestCompilerPluginClasspath=${compilerPluginClasspath.get()}")
    },
  )
}

val version = project.version
tasks.jar {
  manifest {
    attributes("Implementation-Version" to version)
  }
}

gradlePlugin {
  plugins {
    create("composeUnusedResourcesPlugin") {
      id = "com.eygraber.compose.unused.resources.compiler.plugin"
      implementationClass = "com.eygraber.compose.unused.resources.ComposeUnusedResourcesGradlePlugin"
    }

    create("composeUnusedResourcesRootPlugin") {
      id = "com.eygraber.compose.unused.resources"
      implementationClass = "com.eygraber.compose.unused.resources.UnusedComposeResourcesPlugin"
    }
  }
}

tasks.named<PluginUnderTestMetadata>("pluginUnderTestMetadata") {
  pluginClasspath.from(testKotlinGradlePlugin)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  // kotlin-dsl passes -Xuse-fir-lt=false, which Kotlin 2.4.20 deprecates and -Werror rejects.
  compilerOptions.freeCompilerArgs.add("-Xwarning-level=DEPRECATED_CLI_ARG:disabled")
}

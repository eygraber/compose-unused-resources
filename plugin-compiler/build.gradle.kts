plugins {
  kotlin("jvm")
  id("com.eygraber.conventions-kotlin-library")
  id("com.eygraber.conventions-detekt2")
  id("com.eygraber.conventions-publish-maven-central")
}

// Kotlin/Native resolves compiler plugins without their transitive dependencies.
val shaded = configurations.register("shaded") {
  isTransitive = false
}

dependencies {
  compileOnly(libs.kotlin.compilerEmbeddable)

  compileOnly(projects.pluginCommon)
  shaded(projects.pluginCommon)

  testImplementation(projects.pluginCommon)
  testImplementation(libs.kotlin.compilerEmbeddable)
  testImplementation(libs.test.junit)
  testImplementation(libs.test.kotest.assertions.core)
  testImplementation(libs.test.kotlin.compiler)
}

tasks.jar {
  from(shaded.map { jars -> jars.map(::zipTree) }) {
    exclude("META-INF/MANIFEST.MF")
  }
}

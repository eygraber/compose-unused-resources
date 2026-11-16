plugins {
  kotlin("jvm")
  id("com.eygraber.conventions-kotlin-library")
  id("com.eygraber.conventions-detekt2")
  id("com.eygraber.conventions-publish-maven-central")
}

dependencies {
  testImplementation(libs.test.junit)
  testImplementation(libs.test.kotest.assertions.core)
}

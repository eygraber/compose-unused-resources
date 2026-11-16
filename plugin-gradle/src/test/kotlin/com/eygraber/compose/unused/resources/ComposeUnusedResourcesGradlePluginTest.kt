package com.eygraber.compose.unused.resources

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.gradle.api.attributes.Usage
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@Suppress("UnnamedParameterUse")
class ComposeUnusedResourcesGradlePluginTest {
  @get:Rule
  val temp = TemporaryFolder()

  @Test
  fun `applying the plugin creates the consumable index configuration`() {
    val project = ProjectBuilder.builder().withProjectDir(temp.newFolder()).build()

    project.plugins.apply(ComposeUnusedResourcesGradlePlugin::class.java)

    val configuration = project.configurations.getByName(COMPOSE_RESOURCE_INDEX_CONFIGURATION)
    configuration.isCanBeConsumed.shouldBeTrue()
    configuration.isCanBeResolved.shouldBeFalse()
    configuration.attributes
      .getAttribute(Usage.USAGE_ATTRIBUTE)
      .shouldNotBeNull()
      .name shouldBe "compose-resource-index"
  }

  @Test
  fun `applying the plugin creates the compiler extension with defaults`() {
    val project = ProjectBuilder.builder().withProjectDir(temp.newFolder()).build()

    project.plugins.apply(ComposeUnusedResourcesGradlePlugin::class.java)

    val extension = project.extensions.getByType(UnusedComposeResourcesCompilerExtension::class.java)
    extension.excludedTargets.get() shouldBe emptySet()
    extension.includeTestCompilations.get().shouldBeTrue()
  }
}

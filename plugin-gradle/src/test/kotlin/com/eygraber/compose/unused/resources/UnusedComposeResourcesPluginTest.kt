package com.eygraber.compose.unused.resources

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@Suppress("UnnamedParameterUse")
class UnusedComposeResourcesPluginTest {
  @get:Rule
  val temp = TemporaryFolder()

  @Test
  fun `registers the aggregation task in the verification group`() {
    val root = rootWithSubprojects()

    root.plugins.apply(UnusedComposeResourcesPlugin::class.java)

    val task = root.tasks.getByName("findUnusedComposeResources")
    task.shouldBeInstanceOf<FindUnusedComposeResourcesTask>()
    task.group shouldBe "Verification"
  }

  @Test
  fun `declares a dependency on every subproject's index configuration`() {
    val root = rootWithSubprojects("a", "b")

    root.plugins.apply(UnusedComposeResourcesPlugin::class.java)

    root.configurations.getByName("composeResourceIndex")
      .dependencies
      .map { dependency -> dependency.shouldBeInstanceOf<ProjectDependency>().path }
      .shouldContainExactlyInAnyOrder(":a", ":b")
  }

  @Test
  fun `resolves indexes through a non-transitive configuration`() {
    val root = rootWithSubprojects("a")

    root.plugins.apply(UnusedComposeResourcesPlugin::class.java)

    val consumer = root.configurations.getByName("composeResourceIndexClasspath")
    consumer.isTransitive.shouldBeFalse()
    consumer.extendsFrom.map { extended -> extended.name }.shouldContainExactly("composeResourceIndex")
  }

  @Test
  fun `registers the baseline update task`() {
    val root = rootWithSubprojects("a")

    root.plugins.apply(UnusedComposeResourcesPlugin::class.java)

    val task = root.tasks.getByName("updateComposeUnusedResourcesBaseline")
    task.shouldBeInstanceOf<UpdateComposeUnusedResourcesBaselineTask>()
    task.group shouldBe "Verification"
  }

  @Test
  fun `registers the extension with a default baseline location`() {
    val root = rootWithSubprojects()

    root.plugins.apply(UnusedComposeResourcesPlugin::class.java)

    val extension = root.extensions.getByType(UnusedComposeResourcesExtension::class.java)
    extension.baselineFile.get().asFile shouldBe root.projectDir.resolve("compose-unused-resources-baseline.txt")
  }

  @Test
  fun `the check's baseline input is only present once the file exists`() {
    val root = rootWithSubprojects()
    root.plugins.apply(UnusedComposeResourcesPlugin::class.java)
    val task = root.tasks.getByName("findUnusedComposeResources") as FindUnusedComposeResourcesTask

    task.baselineFile.isPresent.shouldBeFalse()

    root.projectDir.resolve("compose-unused-resources-baseline.txt").writeText("")

    task.baselineFile.isPresent.shouldBeTrue()
  }

  private fun rootWithSubprojects(vararg names: String): Project {
    val root = ProjectBuilder.builder()
      .withName("root")
      .withProjectDir(temp.newFolder())
      .build()
    names.forEach { name ->
      ProjectBuilder.builder().withName(name).withParent(root).build()
    }
    return root
  }
}

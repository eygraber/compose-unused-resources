# compose-unused-resources

`compose-unused-resources` finds [Compose Multiplatform resources] that no code references.

For each Kotlin compilation, a compiler plugin indexes the resource accessors that the module
declares and the accessors that its code references. A task in the root project aggregates the
indexes from every module and reports the accessors that no module references. A resource counts
as used when any module references it, even a module that does not declare it.

The plugin checks these resource types:

| Type       | Accessor return type   |
|------------|------------------------|
| `string`   | `StringResource`       |
| `plurals`  | `PluralStringResource` |
| `array`    | `StringArrayResource`  |
| `drawable` | `DrawableResource`     |
| `font`     | `FontResource`         |

Files in `composeResources/files` are not checked.

## Setup

Apply the root plugin in the root project's `build.gradle.kts`.

```kotlin
plugins {
  id("com.eygraber.compose.unused.resources") version "1.0.1"
}
```

Apply the compiler plugin in each module that declares or references Compose resources.

```kotlin
plugins {
  id("com.eygraber.compose.unused.resources.compiler.plugin") version "1.0.1"
}
```

The task skips a module without the compiler plugin. A resource that only a skipped module
references is reported as unused.

## Usage

Run the `findUnusedComposeResources` task from the root project.

```
./gradlew findUnusedComposeResources
```

The task compiles each module, aggregates the indexes, and prints a report:

```
Found 2 unused Compose resource(s):
  com.example.feature: string/old_title
  com.example.feature: drawable/legacy_logo
```

Add `--check` to fail the build when an unused resource exists:

```
./gradlew findUnusedComposeResources --check
```

## Suppress a resource

The task subtracts suppressed resources from the report before it fails a `--check` build. The
baseline file and the `unusedComposeResources` extension both suppress resources. Use the baseline
for resources that the team removes over time. Use a pattern for resources that only by-name
lookups reach.

The baseline file holds tolerated resources, one exact entry per line, in the report format. A `#`
starts a comment. The default location is `compose-unused-resources-baseline.txt` in the root
project.

```
# kept for the winter promo
com.example.feature: string/old_title
```

The `updateComposeUnusedResourcesBaseline` task writes every currently unused resource to the
file. A baseline entry for a resource that is no longer unused is stale. The task warns about
each stale entry, and `failOnStaleBaseline` turns the warning into a build failure.

The extension configures the baseline and holds suppression patterns. A `*` in a pattern segment
matches any characters within that segment.

```kotlin
unusedComposeResources {
  baselineFile.set(layout.projectDirectory.file("config/unused-resources-baseline.txt"))
  failOnStaleBaseline.set(true)
  suppress("com.example.feature: drawable/ic_seasonal_*")
}
```

## How detection works

The compiler plugin walks the compiled IR of each Kotlin compilation.

- A property with a getter that returns one of the checked resource types counts as a declaration.
- A call to such a property counts as a reference.
- A lookup through a by-name map, such as `Res.allDrawableResources`, does not count as a
  reference.
- References in generated Compose code do not count. The generated resource collectors reference
  every accessor.

Each compilation writes its index to `build/compose-unused-resources/<target>/<compilation>.index`.
The task subtracts the referenced accessors from the declared accessors across every index.

[Compose Multiplatform resources]: https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources.html

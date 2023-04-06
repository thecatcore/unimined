package xyz.wagyourtail.unimined.api.tasks

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.jvm.tasks.Jar
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.unimined.api.mappings.MappingNamespace
import xyz.wagyourtail.unimined.api.sourceSet
import xyz.wagyourtail.unimined.util.LazyMutable

/**
 * @since 0.4.10
 */
@Suppress("LeakingThis")
abstract class JarModTransformResolveTask : Jar() {


    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val transforms: Property<String?>

    @get:Internal
    var sourceSet: SourceSet by LazyMutable {
        project.sourceSet.getByName("main")
    }

    @get:Internal
    var remapJarTask: RemapJarTask by LazyMutable {
        project.tasks.getByName("remapJar") as RemapJarTask
    }

    init {
        transforms.convention(null as String?)
    }

}
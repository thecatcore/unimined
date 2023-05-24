package xyz.wagyourtail.unimined.internal.mods

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import xyz.wagyourtail.unimined.api.UniminedExtension
import xyz.wagyourtail.unimined.api.mapping.MappingNamespace
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.api.mod.ModRemapConfig
import xyz.wagyourtail.unimined.api.mod.ModsConfig
import xyz.wagyourtail.unimined.api.unimined
import xyz.wagyourtail.unimined.util.associateNonNull
import xyz.wagyourtail.unimined.util.defaultedMapOf
import xyz.wagyourtail.unimined.util.nonNullValues
import xyz.wagyourtail.unimined.util.withSourceSet
import java.io.File
import java.lang.IllegalArgumentException
import java.nio.file.Path
import kotlin.io.path.createDirectories

class ModsProvider(val project: Project, val minecraft: MinecraftConfig) : ModsConfig() {

    private val remapConfigs = mutableMapOf<Set<Configuration>, ModRemapProvider.() -> Unit>()

    private val modImplementation = project.configurations.maybeCreate("modImplementation".withSourceSet(minecraft.sourceSet)).also {
        project.configurations.getByName("implementation".withSourceSet(minecraft.sourceSet)).extendsFrom(it)
        remap(it)
    }

    private val remapConfigsResolved = mutableMapOf<Configuration, ModRemapProvider>()

    init {
        project.afterEvaluate {
            afterEvaluate()
        }
    }

    fun modTransformFolder(): Path {
        return project.unimined.getLocalCache().resolve("modTransform").createDirectories()
    }

    override fun remap(config: List<Configuration>, action: ModRemapConfig.() -> Unit) {
        val intersect = remapConfigs.keys.firstOrNull { config.intersect(it).isNotEmpty() }
        if (intersect != null) {
            throw IllegalArgumentException("cannot have configuration(s) in multiple remaps $intersect")
        }
        remapConfigs[config.toSet()] = action
    }

    fun afterEvaluate() {
        for ((config, action) in remapConfigs) {
            val remapSettings = ModRemapProvider(config, project, minecraft)
            for (c in config) {
                remapConfigsResolved[c] = remapSettings
            }
            remapSettings.action()
            remapSettings.doRemap()
        }
    }

    fun getClasspathAs(namespace: MappingNamespace, fallbackNamespace: MappingNamespace, classpath: Set<File>): Set<File> {
        val remapCp = classpath.associateWith { file ->
            remapConfigsResolved.values.firstNotNullOfOrNull { conf -> conf.getConfigForFile(file)?.let { conf to it } }
        }
        val nonRemap = remapCp.mapNotNull { if (it.value == null) it.key else null }
        project.logger.info("[Unimined/ModRemapper] getting classpath as $namespace/$fallbackNamespace")
        val remap = remapCp.values.filterNotNull()
        val map = defaultedMapOf<ModRemapProvider, MutableSet<Configuration>> { mutableSetOf() }
        for ((m, c) in remap) {
            map[m].add(c)
        }
        val remapOutputs = mutableSetOf<Configuration>()
        for ((m, c) in map) {
            val def = defaultedMapOf<Configuration, Configuration> { project.configurations.detachedConfiguration() }
            m.doRemap(namespace, fallbackNamespace, def)
            remapOutputs.addAll(def.values)
        }
        val files = remapOutputs.flatMap { it.resolve() }
        for (file in nonRemap) {
            project.logger.info("[Unimined/ModRemapper]  unremapped: $file")
        }
        for (file in files) {
            project.logger.info("[Unimined/ModRemapper]    remapped: $file")
        }
        return nonRemap.toSet() + files
    }

}
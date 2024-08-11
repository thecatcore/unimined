package xyz.wagyourtail.unimined.internal.mapping.aw

import kotlinx.coroutines.runBlocking
import net.fabricmc.accesswidener.*
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import okio.buffer
import okio.sink
import okio.use
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.gradle.api.logging.Logger
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import xyz.wagyourtail.unimined.api.mapping.MappingsConfig
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.internal.mapping.MappingsProvider
import xyz.wagyourtail.unimined.internal.mapping.at.AccessTransformerApplier.noAccessMappings
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.aw.AWReader
import xyz.wagyourtail.unimined.mapping.formats.aw.AWWriter
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.tree.MemoryMappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.FieldVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MethodVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NullDelegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator
import xyz.wagyourtail.unimined.util.forEachInZip
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.io.path.*

object AccessWidenerApplier {

    class AwRemapper(val source: String, val target: String, val catchNsError: Boolean, val logger: Logger?): OutputConsumerPath.ResourceRemapper {

        constructor(source: String, target: String): this(source, target, false, null)

        override fun canTransform(remapper: TinyRemapper, relativePath: Path): Boolean {
            // read the beginning of the file and see if it begins with "accessWidener"
            return relativePath.extension.equals("accesswidener", true) ||
                    relativePath.extension.equals("aw", true)
        }

        override fun transform(
            destinationDirectory: Path,
            relativePath: Path,
            input: InputStream,
            remapper: TinyRemapper
        ) {
            val awr = AccessWidenerWriter()
            val aw = input.readBytes()
            try {
                AccessWidenerReader(AccessWidenerRemapper(awr, remapper.environment.remapper, source, target)).read(BufferedReader(InputStreamReader(ByteArrayInputStream(aw), StandardCharsets.UTF_8)))
                val output = destinationDirectory.resolve(relativePath)
                output.parent.createDirectories()
                Files.write(output, awr.write(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            } catch (t: IllegalArgumentException) {
                if (t.message?.startsWith("Cannot remap access widener from namespace") != true) throw t
                if (!catchNsError) {
                    throw t
                } else {
                    logger!!.warn("[Unimined/AccessWidenerTransformer] Skipping access widener $relativePath due to namespace mismatch, writing original!!")
                    Files.write(destinationDirectory.resolve(relativePath), aw, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                }
            }
        }
    }

    fun nsName(config: MappingsConfig<*>, namespace: Namespace) =
        if (config.isOfficial(namespace.name)) {
            "official"
        } else if (config.isIntermediary(namespace.name)) {
            "intermediary"
        } else {
            "named"
        }

    fun transform(
        accessWidener: Path,
        namespace: String,
        baseMinecraft: Path,
        output: Path,
        throwIfNSWrong: Boolean,
        logger: Logger
    ): Boolean {
        val aw = AccessWidener()
        AccessWidenerReader(aw).read(BufferedReader(accessWidener.reader()))
        if (aw.namespace == namespace) {
            Files.copy(baseMinecraft, output, StandardCopyOption.REPLACE_EXISTING)
            try {
                val targets = aw.targets.toMutableSet()
                ZipArchiveOutputStream(output.outputStream()).use { zipOutput ->
                    logger.debug("Transforming $output with access widener $accessWidener and namespace $namespace")
                    baseMinecraft.forEachInZip { path, stream ->
                        if (path.endsWith(".class")) {
                            val target = path.removeSuffix(".class").replace("/", ".")
                            if (target in targets) {
                                try {
                                    logger.debug("Transforming $path")
                                    val reader = ClassReader(stream)
                                    val writer = ClassWriter(0)
                                    val visitor = AccessWidenerClassVisitor.createClassVisitor(Opcodes.ASM9, writer, aw)
                                    reader.accept(visitor, 0)
                                    zipOutput.putArchiveEntry(ZipArchiveEntry(path))
                                    zipOutput.write(writer.toByteArray())
                                    zipOutput.closeArchiveEntry()
                                } catch (e: Exception) {
                                    logger.warn(
                                        "An error occurred while transforming $target with access widener $accessWidener for namespace $namespace in $output",
                                        e
                                    )
                                }
                                targets.remove(target)
                            } else {
                                zipOutput.putArchiveEntry(ZipArchiveEntry(path))
                                stream.copyTo(zipOutput)
                                zipOutput.closeArchiveEntry()
                            }
                        } else {
                            zipOutput.putArchiveEntry(ZipArchiveEntry(path))
                            stream.copyTo(zipOutput)
                            zipOutput.closeArchiveEntry()
                        }
                    }
                }
                if (targets.isNotEmpty()) {
                    logger.warn("AccessWidener $accessWidener did not find the following classes: $targets")
                }
            } catch (e: Exception) {
                output.deleteIfExists()
                throw e
            }
            return true
        }
        if (throwIfNSWrong) {
            throw IllegalStateException("AccessWidener namespace (${aw.namespace}) does not match minecraft namespace ($namespace)")
        } else {
            logger.info("AccessWidener ($accessWidener) namespace (${aw.namespace}) does not match minecraft namespace ($namespace), it will not be applied!")
        }
        return false
    }

    suspend fun mergeAws(
        inputs: List<Path>,
        output: Path,
        targetNamespace: Namespace,
        mappingsProvider: MappingsProvider
    ): Path {
        val mappings = noAccessMappings(mappingsProvider.resolve())

        inputs.forEach {
            runBlocking {
                var temp = ""
                val data = AWReader.readData(CharReader(it.readText()))

                if (data.namespace != targetNamespace) {
                    val remappedData = AWWriter.remapMappings(data, mappingsProvider.resolve(), targetNamespace)
                    AWWriter.writeData(remappedData) {
                        temp += it
                    }
                } else {
                    AWWriter.writeData(data) {
                        temp += it
                    }
                }

                if (temp.isNotEmpty()) AWReader.read(temp, mappings)
            }
        }

        output.sink().buffer().use {
            mappings.accept(AWWriter.write(it), listOf(targetNamespace))
        }

        return output
    }
}
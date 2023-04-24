package xyz.wagyourtail.unimined.api.output

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import xyz.wagyourtail.unimined.api.output.jar.JarOutput
import xyz.wagyourtail.unimined.api.output.remap.RemapJarOutput


val Project.outputProvider
    get() = extensions.getByType(OutputProvider::class.java)


/**
 * @since 0.5.0
 */
abstract class OutputProvider {

    abstract val jar: JarOutput

    abstract val remapJar: RemapJarOutput

    abstract fun <T: Jar> addStep(name: String, type: Class<T>): Output<T>
    abstract fun getOutputStep(name: String): Output<*>?
    abstract fun <T: Jar> addStepBefore(before: String, name: String, type: Class<T>): Output<T>
    abstract fun <T: Jar> addStep(
        name: String,
        type: Class<T>,
        action: Output<T>.() -> Unit
    )

    fun <T: Jar> addStep(
        name: String,
        Type: Class<T>,
        @DelegatesTo(
            strategy = Closure.DELEGATE_FIRST,
            value = Output::class
        )
        action: Closure<*>
    ) {
        addStep(name, Type) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }

    abstract fun <T: Jar> addStepBefore(
        before: String,
        name: String,
        type: Class<T>,
        action: Output<T>.() -> Unit
    )

    fun <T: Jar> addStepBefore(
        before: String,
        name: String,
        Type: Class<T>,
        @DelegatesTo(
            strategy = Closure.DELEGATE_FIRST,
            value = Output::class
        )
        action: Closure<*>
    ) {
        addStepBefore(before, name, Type) {
            action.delegate = this
            action.resolveStrategy = Closure.DELEGATE_FIRST
            action.call()
        }
    }
}
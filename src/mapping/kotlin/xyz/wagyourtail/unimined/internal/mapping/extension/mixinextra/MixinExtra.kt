package xyz.wagyourtail.unimined.internal.mapping.extension.mixinextra

import xyz.wagyourtail.unimined.internal.mapping.extension.mixin.refmap.RefmapBuilderClassVisitor
import xyz.wagyourtail.unimined.internal.mapping.extension.mixinextra.annotations.method.*

object MixinExtra {

    object Annotation {

        const val MODIFY_EXPRESSION_VALUE = "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;"
        const val MODIFY_RECIEVER = "Lcom/llamalad7/mixinextras/injector/ModifyReciever;"
        const val MODIFY_RETURN_VALUE = "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;"
        const val WRAP_WITH_CONDITION = "Lcom/llamalad7/mixinextras/injector/WrapWithCondition;"
        const val WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;"


    }

    fun refmapBuilder(refmapBuilder: RefmapBuilderClassVisitor) {

        refmapBuilder.methodAnnotationVisitors.addAll(listOf(
            ModifyExpressionValueAnnotationVisitor.Companion::shouldVisit to ::ModifyExpressionValueAnnotationVisitor,
            ModifyRecieverAnnotationVisitor.Companion::shouldVisit to ::ModifyRecieverAnnotationVisitor,
            ModifyReturnValueAnnotationVisitor.Companion::shouldVisit to ::ModifyReturnValueAnnotationVisitor,
            WrapWithConditionAnnotationVisitor.Companion::shouldVisit to ::WrapWithConditionAnnotationVisitor,
            WrapOperationAnnotationVisitor.Companion::shouldVisit to ::WrapOperationAnnotationVisitor,
        ))

    }

}
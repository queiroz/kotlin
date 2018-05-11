/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.descriptors.impl.AnonymousFunctionDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.moveInsideParentheses
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.BranchedFoldingUtils
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.bindingContextUtil.getTargetFunctionDescriptor
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.typeUtil.isUnit

class LambdaToAnonymousFunctionIntention : SelfTargetingIntention<KtLambdaExpression>(
    KtLambdaExpression::class.java,
    "Convert to anonymous function",
    "Convert lambda expression to anonymous function"
), LowPriorityAction {

    override fun isApplicableTo(element: KtLambdaExpression, caretOffset: Int): Boolean {
        if (element.getStrictParentOfType<KtValueArgument>() == null) return false
        val descriptor = element.functionLiteral.descriptor as? AnonymousFunctionDescriptor ?: return false
        return descriptor.valueParameters.none { it.name.isSpecial }
    }

    override fun applyTo(element: KtLambdaExpression, editor: Editor?) {
        val functionLiteral = element.functionLiteral
        val bodyExpression = functionLiteral.bodyExpression ?: return
        val descriptor = functionLiteral.descriptor as? AnonymousFunctionDescriptor ?: return
        val psiFactory = KtPsiFactory(element)

        val context = element.analyze(BodyResolveMode.PARTIAL)
        bodyExpression.collectDescendantsOfType<KtReturnExpression>().forEach {
            if (it.getTargetFunctionDescriptor(context) == descriptor) it.labeledExpression?.delete()
        }

        // TODO: check type rendering (!!!)
        val anonymousFunction = psiFactory.createFunction(
            KtPsiFactory.CallableBuilder(KtPsiFactory.CallableBuilder.Target.FUNCTION).apply {
                typeParams()
                descriptor.extensionReceiverParameter?.type?.let {
                    receiver(it.toString())
                }
                name("")
                for (parameter in descriptor.valueParameters) {
                    param(parameter.name.asString(), parameter.type.toString())
                }
                descriptor.returnType?.takeIf { !it.isUnit() }?.let {
                    val lastStatement = bodyExpression.statements.lastOrNull()
                    if (lastStatement != null && lastStatement !is KtReturnExpression) {
                        val foldableReturns = BranchedFoldingUtils.getFoldableReturns(lastStatement)
                        if (foldableReturns == null || foldableReturns.isEmpty()) {
                            lastStatement.replace(psiFactory.createExpressionByPattern("return $0", lastStatement))
                        }
                    }
                    returnType(it.toString())
                } ?: noReturnType()
                blockBody(" " + bodyExpression.text)
            }.asString()
        )

        val resultingFunction = element.replaced(anonymousFunction)
        (resultingFunction.parent as? KtLambdaArgument)?.also { it.moveInsideParentheses(it.analyze(BodyResolveMode.PARTIAL)) }
    }
}
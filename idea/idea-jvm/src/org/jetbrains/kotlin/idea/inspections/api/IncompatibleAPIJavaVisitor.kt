/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.inspections.api

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocComment
import org.jetbrains.kotlin.psi.psiUtil.parents

class IncompatibleAPIJavaVisitor internal constructor(
    private val myHolder: ProblemsHolder,
    private val problemsCache: ProblemsCache
) : JavaElementVisitor() {
    override fun visitDocComment(comment: PsiDocComment) {
        // No references inside doc comment are of interest.
    }

    override fun visitImportList(list: PsiImportList?) {
        // Do not report anything in imports
    }

    override fun visitClass(aClass: PsiClass) {}

    override fun visitReferenceExpression(expression: PsiReferenceExpression) {
        visitReferenceElement(expression)
    }

    override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) {
        super.visitReferenceElement(reference)

        val isInsideImport = reference.element.parents
            .takeWhile { it is PsiJavaCodeReferenceElement || it is PsiImportStatement }
            .any { it is PsiImportStatement }
        if (isInsideImport) {
            return
        }

        ModuleUtilCore.findModuleForPsiElement(reference.element) ?: return

        val psiMember = reference.resolve() as? PsiMember ?: return

        val problem = findProblem(psiMember, problemsCache) ?: return

        registerProblemForReference(reference, myHolder, problem)
    }

    override fun visitNewExpression(expression: PsiNewExpression) {
        super.visitNewExpression(expression)
        val constructor = expression.resolveConstructor()
        ModuleUtilCore.findModuleForPsiElement(expression) ?: return

        if (constructor is PsiCompiledElement) {
            val problem = findProblem(constructor, problemsCache)
            if (problem != null) {
                registerProblemForReference(expression.classReference, myHolder, problem)
            }
        }
    }

    override fun visitMethod(method: PsiMethod) {
        super.visitMethod(method)
        val annotation =
            (if (!method.isConstructor) AnnotationUtil.findAnnotation(method, CommonClassNames.JAVA_LANG_OVERRIDE) else null) ?: return

        ModuleUtilCore.findModuleForPsiElement(annotation) ?: return

        val methods = method.findSuperMethods()
        for (superMethod in methods) {
            if (superMethod is PsiCompiledElement) {
                val problem = findProblem(superMethod, problemsCache)
                if (problem != null) {
                    registerProblemForReference(annotation.nameReferenceElement, myHolder, problem)
                    return
                }
            } else {
                return
            }
        }
    }
}
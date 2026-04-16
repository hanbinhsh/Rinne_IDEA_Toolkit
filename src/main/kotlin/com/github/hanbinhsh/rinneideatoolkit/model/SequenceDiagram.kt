package com.github.hanbinhsh.rinneideatoolkit.model

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPsiElementPointer

enum class SequenceMessageKind {
    CALL,
    RETURN,
    CREATE,
}

data class SequenceDiagram(
    val targetMethodDisplayName: String,
    val targetClassQualifiedName: String,
    val targetMethodSignature: String,
    val scenarios: List<SequenceScenario>,
)

data class SequenceScenario(
    val id: Int,
    val entryMethodDisplayName: String,
    val entryPointer: SmartPsiElementPointer<PsiMethod>,
    val participants: List<SequenceParticipant>,
    val messages: List<SequenceMessage>,
)

data class SequenceParticipant(
    val className: String,
    val classQualifiedName: String,
)

data class SequenceMessage(
    val scenarioId: Int,
    val order: Int,
    val depth: Int,
    val fromClassName: String,
    val fromClassQualifiedName: String,
    val toClassName: String,
    val toClassQualifiedName: String,
    val methodDisplaySignature: String,
    val kind: SequenceMessageKind,
    val isCreateReturn: Boolean = false,
    val isSelfCall: Boolean,
    val pointer: SmartPsiElementPointer<PsiElement>,
)

data class SequenceViewState(
    val targetMethodDisplayName: String? = null,
    val diagram: SequenceDiagram? = null,
    val loading: Boolean = false,
    val errorMessage: String? = null,
)

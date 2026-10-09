package ph.merd.akma.domain

import ph.merd.akma.safety.ModelOutputSafety

/** Every copied field reaches the model through this helper; action and tone remain user-selected. */
internal fun neutralizedForPrompt(request: AnalyzeRequest): AnalyzeRequest = request.copy(
    message = ModelOutputSafety.neutralizePromptInput(request.message),
    history = ModelOutputSafety.neutralizePromptInput(request.history),
    relationship = request.relationship?.let(ModelOutputSafety::neutralizePromptInput),
)

internal fun neutralizedForPrompt(request: DraftRequest): DraftRequest = request.copy(
    original = neutralizedForPrompt(request.original),
    userInstruction = ModelOutputSafety.neutralizePromptInput(request.userInstruction),
)

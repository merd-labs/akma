# Shared Kotlin contract — coordinated domain action safety

Owner: Miguel; reviewer: Elijah. The owner authorizes the three-action catalog and second-confirmation reconciliation on `fix/domain-action-safety`. Review this PR before dependent UI/runtime branches integrate. No historical source drafts change.

## Engine types

```kotlin
data class AnalyzeRequest(
    val message: String,
    val history: String = "",
    val relationship: String? = null,
)

data class SuggestedAction(val id: String, val label: String)

/** Classification provenance, supplied by the adapter, not extracted from model-generated JSON. */
enum class AnalysisSource { UNSPECIFIED, LOCAL_MODEL, DETERMINISTIC }

data class AnalysisResult(
    val category: String,
    val summary: String,
    val requiresUserDecision: Boolean,
    val actions: List<SuggestedAction>,
    val source: AnalysisSource = AnalysisSource.UNSPECIFIED,
)

enum class ReplyTone { PROFESSIONAL, FRIENDLY, CONCISE }

data class DraftRequest(
    val original: AnalyzeRequest,
    val selectedActionId: String,
    val tone: ReplyTone,
    val userInstruction: String = "",
)

interface LocalReplyEngine {
    suspend fun initialize(): Result<Unit>
    /** Return untrusted analysis with adapter-owned source metadata; callers normalize before display. */
    suspend fun analyze(request: AnalyzeRequest): Result<AnalysisResult>
    /** Interpret selectedActionId using ActionCatalog, never a model-supplied label. */
    suspend fun draft(request: DraftRequest): Result<String>
}
```

`AnalysisResult` is untrusted engine output until `ReplyValidation.normalize` succeeds. The adapter must assign `source` itself; never take provenance from model JSON. `UNSPECIFIED` preserves existing constructor compilation but fails normalization. Synthetic engine fixtures belong only in tests and do not establish real inference. No runtime adapter or JSON parser is added here.

## Catalog and display boundary

`ActionCatalog` owns every supported ID, visible label, and category membership. `actionIds`, `categoryIds`, `action(id)`, and `actionsFor(category)` expose reviewed local definitions. Candidate pools for interview/meeting contain four actions; displayed results contain **1–3**. Four supplied actions fail rather than being silently truncated. IDs are exact and case-sensitive. Caller allowlists may narrow the catalog but cannot extend it.

| Category ID | Action IDs and authoritative labels |
| --- | --- |
| `interview_invitation` | `accept`: Accept; `reschedule`: Reschedule; `clarify`: Ask for details; `decline`: Decline politely |
| `meeting` | `confirm`: Confirm; `suggest_time`: Suggest another time; `ask_agenda`: Ask for agenda; `decline`: Decline politely |
| `reschedule_request` | `agree_new_time`: Agree to new time; `suggest_alternative`: Suggest alternative; `ask_reason`: Ask why |
| `follow_up` | `give_update`: Give update; `request_more_time`: Ask for more time; `thank_confirm`: Thank and confirm |
| `complaint` | `apologize`: Apologize; `explain`: Explain; `offer_fix`: Offer a fix |
| `casual` | `reply_warmly`: Reply warmly; `catch_up`: Catch up; `keep_short`: Keep it short |
| `other` | `acknowledge`: Acknowledge; `ask_to_clarify`: Ask to clarify; `respond_briefly`: Respond briefly |

Exact legacy category `invitation` normalizes to `interview_invitation`. No other aliases or unknown-category fallback are implicit. Unknown IDs, duplicates, category-incompatible IDs, blank/malformed categories, empty actions, and unsupported provenance fail visibly. Model labels are discarded, including blank or misleading labels: `{id:"accept", label:"Decline"}` becomes `{id:"accept", label:"Accept"}`. The selected ID remains `accept` and requires human confirmation.

`ActionCatalog.otherAnalysis()` explicitly supplies Other and its three safe local actions with `DETERMINISTIC` provenance. This is a catalog fallback, not a classifier or model response. The coordinator never calls it to conceal invalid analysis, missing models, or engine failures. An adapter may explicitly choose it when genuine classification is uncertain, while disclosing that the fallback is deterministic.

No user text is logged. Raw message/history/relationship/instruction and draft limits remain 1,500 UTF-16 characters. Raw analysis category/summary also fail above 1,500 characters before sanitization. Category normalization removes Unicode control/format characters, trims whitespace, and requires an exact catalog category. Summary normalization removes format characters (including bidi overrides/isolates and direction marks), converts controls/Unicode whitespace to spaces, collapses whitespace, and rejects empty results and unpaired surrogates. Summary content is bounded to 200 Unicode code points without splitting surrogate pairs. A trusted source prefix is added separately: model summaries are marked untrusted; deterministic summaries are labeled deterministic; both disclose local catalog actions. This reduces display spoofing but does not prove the summary is true.

`validate(analysis, allowedActionIds)` and `normalize(analysis, allowedActionIds)` share checks. Validation alone does not sanitize its input object. Only the returned normalized analysis may enter display state. Published `requiresUserDecision` is always true; a model cannot remove the human gate.

## Second confirmation before every draft

```kotlin
data class DraftConfirmation(
    val id: Long,
    val action: SuggestedAction,
    val request: DraftRequest,
)

// Added to ReplyState:
val pendingConfirmation: DraftConfirmation? = null

// ReplyCoordinator public methods:
fun draft(actionId: String, tone: ReplyTone) // Stages confirmation only.
fun confirmDraft(confirmationId: Long)      // Starts drafting after a separate human Confirm.
fun cancel()                              // Cancels pending confirmation or active work.
```

An action click calls `draft`; it never invokes the engine. It publishes a pending confirmation containing canonical action/label and the immutable original message/action/tone request. Show that exact request to the user. A separate Confirm control calls `confirmDraft` with the ID captured by the displayed confirmation. Never confirm automatically, never immediately call both APIs from an action click, and never substitute a newer pending ID when an old dialog confirms.

Every action requires confirmation, even when the raw model sets `requiresUserDecision=false`. Confirmation validates the current pending ID and request, consumes it before scheduling, and prevents duplicate engine calls. A repeated identical selection retains its ID. Replacement action/tone selection, cancellation, accepted message changes, reanalysis, and initialization invalidate earlier confirmation. Stale confirmation cannot invoke inference. Copying is disabled while confirmation is pending. Processing failures and cancellation require a fresh selection/confirmation before retrying a draft.

Phases remain `ModelUnavailable`, `ModelLoading`, `Ready`, `Analyzing`, `ChoosingAction`, `Drafting`, `Editing`, `Copied`, and `Error`. Pending confirmation stays in `ChoosingAction`; no new phase requires exhaustive UI changes. Existing mutex serialization, timeout, cancellation, duplicate-tap handling, error redaction, and manual copy/send behavior remain required.

## Integration and limitations

Current Activity and overlay have no second-confirmation controls. Their existing action clicks now stop safely at pending confirmation. Elijah/Danielle must wire explicit Confirm/Cancel controls in separate reviewed UI changes before release; do not ship this as a complete drafting journey. Gradle, manifest, layout/design, and inference runtime remain outside this branch.

Catalog meanings constrain requests, not model prose. An Accept/Confirm action expresses only the user's confirmed choice; it does not supply new dates, availability, payments, refunds, or names. Runtime adapters must use canonical IDs and original user context, ask for missing details, and treat prompts as untrusted input. Structural tests do not prove semantic faithfulness or guarantee that a generated draft contains no invented commitments. The user must review/edit and manually copy/paste/send. Filipino/Taglish behavior and actual offline device inference remain unverified.

package ph.merd.akma.ai.protocol

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import ph.merd.akma.domain.ActionCatalog
import ph.merd.akma.domain.AnalysisResult
import ph.merd.akma.domain.AnalysisSource
import ph.merd.akma.domain.AnalyzeRequest
import ph.merd.akma.domain.DraftRequest

object AkmaProtocol {

    val SYSTEM_PROMPT = """You are Akma, a helpful, intelligent assistant. 
You extract intentions from messages and write replies from the user's perspective. 
Never invent facts, dates, or commitments. Keep output concise and respect the requested language (English, Tagalog, or Taglish)."""

    fun compileAnalysisPrompt(request: AnalyzeRequest): String {
        val prompt = StringBuilder()
        prompt.append("Analyze the following message. Identify its category and a short summary of its purpose. ")
        prompt.append("The category MUST be exactly one of: interview_invitation, meeting, reschedule_request, follow_up, complaint, casual, other.\n\n")
        
        if (request.history.isNotBlank()) {
            prompt.append("Conversation History:\n").append(request.history).append("\n\n")
        }
        prompt.append("Message:\n").append(request.message).append("\n\n")
        prompt.append("Output strictly valid JSON with no markdown formatting. Schema:\n")
        prompt.append("{\"category\": \"<category>\", \"summary\": \"<short purpose>\", \"language\": \"<english|tagalog|taglish>\"}")
        return prompt.toString()
    }

    fun decodeAnalysis(rawModelText: String): Result<AnalysisResult> {
        return try {
            val jsonText = rawModelText.substringAfter("{").substringBeforeLast("}")
            if (jsonText.isEmpty() && !rawModelText.contains("{")) {
                return Result.success(ActionCatalog.otherAnalysis())
            }
            
            val jsonElement = JsonParser.parseString("{$jsonText}")
            val json = if (jsonElement.isJsonObject) jsonElement.asJsonObject else JsonObject()
            
            val rawCategory = if (json.has("category") && !json.get("category").isJsonNull) {
                json.get("category").asString
            } else {
                "other"
            }
            var category = rawCategory.lowercase()
            
            val canonical = ActionCatalog.canonicalCategory(category)
            if (canonical == null) {
                category = "other"
            } else {
                category = canonical
            }

            val rawSummary = if (json.has("summary") && !json.get("summary").isJsonNull) {
                json.get("summary").asString
            } else {
                "Choose how to respond."
            }
            
            var summary = rawSummary
            if (summary.length > 200) {
                summary = summary.substring(0, 200) + "..."
            }
            
            if (category == "other") {
                return Result.success(ActionCatalog.otherAnalysis())
            }

            val actions = ActionCatalog.actionsFor(category).take(3)
            
            Result.success(
                AnalysisResult(
                    category = category,
                    summary = summary,
                    requiresUserDecision = true,
                    actions = actions,
                    source = AnalysisSource.LOCAL_MODEL
                )
            )
        } catch (e: JsonSyntaxException) {
            Result.success(ActionCatalog.otherAnalysis())
        } catch (e: Exception) {
            Result.success(ActionCatalog.otherAnalysis())
        }
    }

    fun compileDraftPrompt(request: DraftRequest): String {
        val prompt = StringBuilder()
        prompt.append("Write a reply to the following message. ")
        prompt.append("Write from the recipient's perspective. ")
        
        val action = ActionCatalog.action(request.selectedActionId)
        if (action != null) {
            prompt.append("The reply should: ${action.label}. ")
        }
        
        when (request.selectedActionId) {
            "reschedule" -> prompt.append("Ask the sender for a different interview time. Do not say you are available Friday at 10 or accept that time. ")
            "clarify", "ask_agenda", "ask_to_clarify" -> prompt.append("Ask for details before agreeing to anything. ")
            "decline" -> prompt.append("Politely decline without an invented excuse. ")
        }
        
        prompt.append("Tone: ${request.tone.name.lowercase()}. ")
        
        if (request.userInstruction.isNotBlank()) {
            prompt.append("Additional instruction: ${request.userInstruction}. ")
        }
        
        prompt.append("Do NOT include any preamble, introduction, or quotes. Output ONLY the exact text of the reply.\n\n")
        
        if (request.original.history.isNotBlank()) {
            prompt.append("Conversation History:\n").append(request.original.history).append("\n\n")
        }
        prompt.append("Message:\n").append(request.original.message).append("\n")
        return prompt.toString()
    }

    fun validateParsedReply(rawModelText: String): Result<String> {
        var cleanText = rawModelText.substringBefore("<|im_end|>").trim()
        cleanText = cleanText.removePrefix("\"").removeSuffix("\"").trim()
        
        if (cleanText.isEmpty()) {
            return Result.failure(IllegalStateException("Generated reply is empty."))
        }
        
        if (cleanText.length > 1500) {
            return Result.failure(IllegalStateException("Generated reply is too long."))
        }
        
        return Result.success(cleanText)
    }
}

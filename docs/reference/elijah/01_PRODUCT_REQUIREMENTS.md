# ContextAI — Product Requirements Document (PRD)

**Version:** 1.0 · **Date:** 2026-10-09 · **Status:** Hackathon MVP / scope freeze  
**Team:** 4 developers · **Build window:** 24 hours · **Platform:** Android (Kotlin)  
**Owner:** Product / Demo lead (Member 4) · **Working title:** ContextAI

> **Pitch:** A privacy-first Android communication assistant that understands the intent behind a received message and helps people write an appropriate reply with an on-device language model—without opening a separate AI chat service.

## 1. Executive summary

People who communicate with recruiters, managers, clients, and colleagues often interrupt their workflow to copy a message into an external AI assistant, explain the situation, generate a suitable response, and return to the original app. ContextAI reduces this friction by placing a user-invoked reply assistant over their existing Android communication workflow. The assistant analyzes **user-provided** message text locally, presents relevant response intentions, and generates a draft in a chosen tone. The user reviews and copies the draft before sending it.

**The innovation claim is not** “AI replies have never existed.” The specific product thesis is **local/private inference + intent-aware response choices + reduced switching across apps**.

## 2. Users, jobs to be done, and scenarios

**Primary users:** students/job seekers communicating with HR; professionals handling client and workplace conversations; people who want context-appropriate formal replies.  
**User job:** “When I receive an important message, help me understand what response options make sense and draft one quickly, without exposing the conversation to an external AI provider.”

| Scenario | Received message | User's intent | Good outcome |
|---|---|---|---|
| Internship interview | “Can you interview Friday at 10?” | Ask for another schedule | Polite reschedule request, no invented availability |
| Client complaint | “The delivery was late and I am unhappy.” | Acknowledge, request details | Respectful reply, no unauthorized refund promise |
| Workplace request | “Can you finish the report by tonight?” | Clarify deadline/scope | Clear question, no false agreement |

## 3. Problem and product outcome

**Current workflow:** Message → Copy → Open ChatGPT/Gemini → Explain context/instructions → Generate → Copy → Return → Paste/edit.  
**Target workflow:** Message → Copy → Tap ContextAI bubble → Paste/confirm context → Select intended action → Draft locally → Copy → Return to input field and paste.

The MVP reduces **app-to-app AI prompting**; it does **not** promise zero taps, automated message interception, or universal in-field insertion.

## 4. Product principles

1. **User intent over AI assumption.** Never infer the user's availability, agreement, promised delivery, or willingness to pay without confirmation.
2. **Local by default.** Message content and model inference stay on the Android phone. No cloud AI endpoint.
3. **Explicit context access.** Only process text the user intentionally submits; do not scrape conversations silently.
4. **User-controlled sending.** The application does not send messages automatically.
5. **Demo reliability beats breadth.** One successful Viber/Android messaging-app flow outweighs six broken integrations.

## 5. MVP scope (P0 — required)

| ID | Requirement | Acceptance criterion |
|---|---|---|
| PRD-01 | Start assistant from user-invoked floating control | Bubble is visible over the chosen demo messaging app after permission grant; user can open/close it |
| PRD-02 | Enter received message/context | User can explicitly paste or type message text into a focused field; empty input is rejected |
| PRD-03 | On-device intent analysis | Local model returns an intent category and 2–4 appropriate selectable response actions for supplied text |
| PRD-04 | Choose response action | User selects one action (e.g., accept, reschedule, clarify); no response assumes a choice before selection |
| PRD-05 | Choose reply tone | Professional required; Friendly/Concise are P1 if time is short; selected tone influences draft |
| PRD-06 | Generate a reply locally | A selected action and message produce a relevant editable draft using the local model; no internet required |
| PRD-07 | Review and copy draft | User can edit text, copy it, and manually paste in source app; no auto-send |
| PRD-08 | Privacy/status information | UI clearly indicates “On-device AI”, permission use, and that nothing is sent until user pastes/sends |
| PRD-09 | Usable error states | Missing model, permission denied, model busy, invalid response, and timeout show actionable feedback |
| PRD-10 | Demonstrable offline | After model is installed, a complete generation succeeds with Wi-Fi and mobile data disabled |

### Acceptance demonstration for the *single core feature*

1. Start with ContextAI installed, model available, and permission granted.
2. Open Viber or another **tested** messaging app displaying a prepared HR message.
3. Copy the message using the messaging app's own UI; tap the assistant bubble.
4. Paste the copied message **explicitly** into ContextAI's input field (or use explicit user-triggered clipboard import if tested).
5. Local model detects `interview_scheduling` and proposes actions such as `confirm`, `reschedule`, and `ask_details`.
6. Select `reschedule` + `professional`; locally generate a respectful response that does **not** claim availability or invent a new date.
7. Review, copy, return to the chat composer, and paste. **Do not auto-send.**
8. Repeat with connectivity disabled and display proof that the model inference is on the phone.

## 6. Stretch scope (P1 only after P0 passes)

- Collapsible bubble + polished animation and repositioning.
- Friendly and concise tone variants if not part of first implementation.
- Android `ACTION_SEND` share-text intake on supported applications.
- Explicit “Paste copied message” action with correctly focused foreground surface.
- Copy-to-clipboard and minimization in a single tap.
- Small performance/interaction benchmark comparing conventional and ContextAI workflows.

## 7. Out of scope (P2 / future)

- Automatic reading of Viber, Messenger, Gmail, Teams, or Slack threads.
- Accessibility-based screen scraping; notification reading; OCR screenshots.
- Full custom Android keyboard / `InputMethodService`.
- Cross-app auto-insertion or autonomous message sending.
- Email/chat integrations, backend accounts, remote sync, RAG, agents, analytics SDKs.
- iOS support, Play Store publishing, production-grade device matrix.

## 8. Detailed UX behavior

**Onboarding:** short privacy explanation → check model installed → request draw-over-apps permission with Android system settings → user taps “Enable assistant.” If overlay fails, show the same assistant in a normal Activity as a fallback, clearly identified as reduced integration.

**Assistant panel:**
- Context textarea (required; multiline, editable, scrollable).
- “Analyze” button; loading state with cancel/close option.
- Detected purpose and 2–4 human-readable action cards.
- Action selection; tone selection; “Generate reply” button.
- Editable reply; Copy; Close/minimize.
- Clear failure/retry messaging; no fabricated results.

**Unknown or ambiguous messages:** show `general_reply` or `needs_clarification`; present neutral actions including “Ask for clarification.” Do not force a specific business scenario.

**Safety boundary:** incoming text is untrusted and may contain commands (“ignore your rules”); prompts must treat it as content, not developer/system instructions. Generated text is always a draft subject to user review.

## 9. Nonfunctional product targets

| ID | Target | How evaluated |
|---|---|---|
| NFR-P1 | 100% cloud-free inference in MVP | Airplane-mode demo after model setup; no AI HTTP client |
| NFR-P2 | Reply relevant to user-selected action | 3 scenario test cases; no contradictory promises |
| NFR-P3 | Usable response time | Target ≤30 s for a short reply after warm-up **on selected demo hardware**; record actual latency, do not falsely claim guarantee |
| NFR-P4 | No message auto-sending | No send API or send action; manual copy and paste only |
| NFR-P5 | User-entered content not persisted by default | No history DB/logging of message bodies; clear when panel closes |
| NFR-P6 | Repeatable demo | 3 successive inference runs without crash on demo device |

## 10. Metrics and judge-facing proof

**Measure, don't invent:** baseline task time (copy → external AI → paste), ContextAI task time, app switches, number of taps, local inference latency, and result relevance over the same 3 scripted cases. Collect at least 3 runs per path if time allows. Report mean/median with actual observations; do not prefill savings percentages.

**Hackathon criterion alignment** (based on event details shared by the team):
- Problem & Usefulness (25%): relatable workflow pain, measured interruption reduction.
- Local AI Implementation (25%): genuine on-phone offline inference, private context.
- Technical Execution (20%): robust overlay, model lifecycle, structured outputs, error handling.
- Innovation (15%): user-intent choice + local cross-app flow (not a claim of AI reply novelty).
- Product & Demo (15%): polished 60–90-second live end-to-end demonstration.

## 11. Dependencies and assumptions (unresolved until verified)

- **Demo phone model / RAM / chipset / Android version:** unknown; must be recorded at kickoff.
- **Team Kotlin/Android experience:** unknown; may change implementation choice.
- **Hackathon local-AI rule:** presumed to require inference on local hardware; confirm exact permitted devices/edge-server language. This PRD targets stricter *on-phone* inference.
- **Source app:** Viber is a target, **not a guaranteed integration**. Use a tested messaging app if Viber's copy behavior blocks the flow.
- **LLM runtime/model:** provisional pending actual device compatibility and offline smoke test.

## 12. Definition of done

P0 requirements PRD-01 through PRD-10 demonstrated on physical Android hardware; model runs offline; no fabricated AI output; generated response follows user action; error states are handled; Android build installs from team-owned artifact; teammate can reproduce the demo using the setup guide.

## 13. Product risks and responses

| Risk | Mitigation |
|---|---|
| Phone too weak for chosen model | Device/model gate at hour 2; reduce model/token budget, pick tested compatible runtime, or escalate rule-compliant compute decision |
| Overlay crashes/disappears | Simplify UI to Android Views; manual activation from visible Activity; fallback Activity for recovery only |
| Clipboard inaccessible in overlay | Explicit paste into focused field; avoid passive clipboard reads |
| Intent model gives invalid actions | Validate JSON, restrict action set, one retry; otherwise require user-entered intent |
| User assumes AI sends messages | Clear “Copy draft” CTA and manual-send disclaimer |

## 14. Open decisions before development

- Physical phone specs and spare phone availability.
- Choice of confirmed LiteRT-LM artifact or alternate runtime based on device smoke test.
- Selected source app for the official demo.
- Which team member owns merge authority and the demo handset.

**Scope lock:** Do not add automatic cross-app access, a system keyboard, or a cloud fallback without explicit whole-team approval and an updated hackathon-rules check.

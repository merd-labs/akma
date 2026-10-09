# 60-Second Screen Record Plan

**Requirement:** All recorded fixtures must be **synthetic**. Do not use real private data or chat history.

## Setup (0:00 - 0:05)
- Device: Tecno Pova 2 (Android 11) or equivalent test device.
- Screen recording active.
- **[CONDITIONAL IF VERIFIED LATER] Action:** Pull down quick settings and explicitly turn on **Airplane Mode** to prove offline capability.
- **Action:** Open a messaging app (e.g., standard SMS app or notes app acting as receiver).

## The Trigger (0:05 - 0:15)
- **Action:** Long-press and copy a pre-prepared synthetic message (e.g., *"Hi, can we reschedule our meeting to tomorrow at 3 PM?"*).
- **Visual:** "Copied to clipboard" toast appears.

## Akma Overlay (0:15 - 0:25)
- **Action:** Launch the Akma overlay (via shortcut/tile/app icon).
- **Visual:** Akma UI appears.
- **Action:** User explicitly pastes the copied text into Akma's input field.

## Action Selection & Inference (0:25 - 0:45)
- **Visual:** Akma displays the intended category (e.g., "Reschedule request") and 3 action chips.
- **Action:** User taps a tone/action chip (e.g., "Professional decline").
- **[CONDITIONAL IF VERIFIED LATER] Visual:** Loading indicator while the local LLM runs inference. *(Currently NOT TESTED on Android)*
- **[CONDITIONAL IF VERIFIED LATER] Visual:** The generated draft appears (e.g., *"I am unavailable tomorrow at 3 PM. Can we find another time?"*). *(Currently NOT TESTED on Android)*

## Refine & Send (0:45 - 0:60)
- **Action:** User edits the text slightly (optional).
- **Action:** User taps "Copy Reply".
- **Action:** User pastes the reply back into the messaging app and hits send.
- **End recording.**

# Functional acceptance tests (synthetic data)

## HR reschedule
Input: “Hello! We'd like to invite you for an interview this Friday at 2 PM. Are you available?” Select RESCHEDULE/Professional. **Pass:** asks to move interview politely; does not claim Friday 2PM works or fabricate an alternate date.

## Client complaint
Input: “The item arrived 3 days late and I am very disappointed.” Select ACKNOWLEDGE/Professional. **Pass:** apologizes/acknowledges, seeks details; does not promise refund or an implemented resolution.

## Manager deadline
Input: “Can you finish the report tonight?” Select CLARIFY/Concise. **Pass:** asks necessary details and does not make an unapproved promise.

## Negative/technical cases
- Overlay denied: app explains and offers normal Activity fallback.
- Empty/huge input: visible validation; never feed unbounded text to model.
- No model or incompatible artifact: clear error, not synthetic “AI” output.
- Device offline: after model import, actual analyze/draft produces variable outputs without Wi-Fi/cellular.
- Rapid double tap: one serialized inference at a time.
- Hide and reopen: preserve required UI state without reloading model repeatedly.
- Android 14: overlay permission and service behavior checked.
- Repeated 3 runs: record exact times and crash/ANR count.
- Check Logcat excludes input, output and PII.

Mark actual results as PASS/FAIL/NOT RUN, with device, timestamp and artifact SHA. Do not assume acceptance has passed.

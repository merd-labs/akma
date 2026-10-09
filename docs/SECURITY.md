# Security / privacy checklist

- Never use AccessibilityService, SMS/content-provider access, notification scraping or unattended clipboard monitoring for MVP.
- Explicit paste is required; overlay permission must be requested and revocable. Respect secure apps where overlays are blocked.
- Do not include Internet permission in MVP unless separately justified and documented. Local model files are provisioned with explicit user/team consent; never ship undisclosed trackers.
- No message bodies, prompts, generated drafts, API tokens or clipboard contents in Logcat, crash analytics, screenshots or commit history. Synthetic fixture data only.
- Generated content is **untrusted** until user reviews it. Incoming message text is untrusted prompt content; never silently convert “please reschedule” into acceptance.
- Clipboard output may be accessible to the OS/keyboard. Treat sensitive results accordingly; do not claim total end-to-end secrecy.
- Avoid logging PII or emailing chat samples. Check Gradle/build caches and Git ignore patterns for model binaries and local.properties.
- Do not use unlicensed or access-restricted model weights without complying with their terms. README discloses local vs online functionality.

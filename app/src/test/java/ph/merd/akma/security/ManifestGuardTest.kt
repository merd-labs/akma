package ph.merd.akma.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs scripts/security/ManifestGuard.java (the same program CI/developers run) against the real merged
 * manifest and against deliberately hostile variants. Needs only the JDK that runs these tests.
 */
class ManifestGuardTest {
    private data class Run(val exit: Int, val stdout: String, val stderr: String)

    private val repoRoot: File = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { it.parentFile }
        .take(4).firstOrNull { File(it, "scripts/security/ManifestGuard.java").isFile }
        ?: error("scripts/security/ManifestGuard.java not found above ${System.getProperty("user.dir")}")
    private val policy = File(repoRoot, "scripts/security/manifest-policy.txt")
    private val cleanText: String = javaClass.getResourceAsStream("/manifests/merged-clean-debug.xml")!!
        .readBytes().toString(Charsets.UTF_8)

    private fun javaBinary(): String {
        val exe = if (requireNotNull(System.getProperty("os.name")).startsWith("Windows", ignoreCase = true)) "java.exe" else "java"
        return File(requireNotNull(System.getProperty("java.home")), "bin/$exe").path
    }

    private fun guard(vararg args: String): Run {
        val command = listOf(javaBinary(), File(repoRoot, "scripts/security/ManifestGuard.java").path, "--policy", policy.path) + args
        val process = ProcessBuilder(command).directory(repoRoot).start()
        val out = process.inputStream.bufferedReader().readText()
        val err = process.errorStream.bufferedReader().readText()
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            fail("ManifestGuard timed out")
        }
        return Run(process.exitValue(), out, err)
    }

    private fun temp(name: String, text: String): File =
        File.createTempFile("guard-$name-", ".xml").apply { deleteOnExit(); writeText(text) }

    private fun withComponent(xml: String) = cleanText.replace("</application>", "$xml\n    </application>")
    private fun withPermission(name: String) =
        cleanText.replace("<application", "<uses-permission android:name=\"$name\" />\n    <application")

    /** Output of one batch run, split into per-manifest FAIL lines. */
    private fun failuresByManifest(output: String): Map<String, List<String>> {
        val result = linkedMapOf<String, MutableList<String>>()
        var current = ""
        output.lines().forEach { line ->
            when {
                line.startsWith("== manifest: ") -> { current = line.removePrefix("== manifest: ").trim(); result[current] = mutableListOf() }
                line.startsWith("FAIL ") -> result.getValue(current) += line
            }
        }
        return result
    }

    @Test fun cleanFixtureAndTheRealMergedManifestPass() {
        val clean = guard(File(repoRoot, "app/src/test/resources/manifests/merged-clean-debug.xml").path)
        assertEquals(clean.stdout + clean.stderr, 0, clean.exit)
        assertTrue(clean.stdout, "RESULT PASS" in clean.stdout)
        assertTrue("debug build must warn, not fail: ${clean.stdout}", "WARN [debuggable]" in clean.stdout)

        // testDebugUnitTest depends on processDebugManifest, so the real merged manifest exists when this runs.
        val merged = File(repoRoot, "app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml")
        assertTrue("merged manifest not found at ${merged.path}; if AGP moved it, update this path (do not skip)", merged.isFile)
        val real = guard(merged.path)
        assertEquals(real.stdout + real.stderr, 0, real.exit)
    }

    @Test fun forbiddenPermissionInjectedIntoRealMergedManifestIsDetected() {
        val merged = File(repoRoot, "app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml")
        assertTrue(merged.isFile)
        val tampered = temp("real-internet", merged.readText().replace("<application", "<uses-permission android:name=\"android.permission.INTERNET\" />\n<application"))
        val run = guard(tampered.path)
        assertEquals(run.stdout, 1, run.exit)
        assertTrue(run.stdout, "FAIL [permission] unapproved permission android.permission.INTERNET" in run.stdout)
        assertTrue("message must tell the developer how to fix it: ${run.stdout}", "tools:node=\"remove\"" in run.stdout)
    }

    @Test fun eachHostileVariantIsDetectedWithAnActionableMessage() {
        val cases = linkedMapOf(
            "internet" to (withPermission("android.permission.INTERNET") to "unapproved permission android.permission.INTERNET"),
            "network-state" to (withPermission("android.permission.ACCESS_NETWORK_STATE") to "ACCESS_NETWORK_STATE"),
            "sms" to (withPermission("android.permission.READ_SMS") to "SMS access is forbidden"),
            "a11y-permission" to (withPermission("android.permission.BIND_ACCESSIBILITY_SERVICE") to "AccessibilityService is forbidden"),
            "a11y-service" to (withComponent(
                """<service android:name="com.evil.Spy" android:exported="true" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
                   <intent-filter><action android:name="android.accessibilityservice.AccessibilityService"/></intent-filter></service>""",
            ) to "[platform-service]"),
            "notification-listener" to (withComponent(
                """<service android:name="com.evil.Listener" android:exported="true" android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
                   <intent-filter><action android:name="android.service.notification.NotificationListenerService"/></intent-filter></service>""",
            ) to "[platform-service]"),
            "exported-service" to (withComponent("""<service android:name=".overlay.Leak" android:exported="true"/>""") to "[exported] service ph.merd.akma.overlay.Leak is exported"),
            "implicit-export" to (withComponent("""<receiver android:name=".Implicit"><intent-filter><action android:name="x.Y"/></intent-filter></receiver>""")
                to "implicitly exported"),
            "open-provider" to (withComponent("""<provider android:name=".OpenProvider" android:authorities="x.p" android:exported="true"/>""") to "[exported] provider"),
            "unguarded-profileinstaller" to (cleanText.replace("android:permission=\"android.permission.DUMP\"", "") to "approved only when guarded by android:permission"),
            "cleartext" to (cleanText.replace("android:allowBackup=\"false\"", "android:allowBackup=\"false\" android:usesCleartextTraffic=\"true\"") to "[cleartext]"),
            "backup-enabled" to (cleanText.replace("android:allowBackup=\"false\"", "android:allowBackup=\"true\"") to "[backup]"),
            "shared-uid" to (cleanText.replace("<manifest ", "<manifest android:sharedUserId=\"x.shared\" ") to "[shared-uid]"),
            "weak-custom-permission" to (cleanText.replace("<application", "<permission android:name=\"ph.merd.akma.OPEN\" android:protectionLevel=\"normal\" />\n<application") to "[custom-permission]"),
        )
        val files = cases.mapValues { (name, case) -> temp(name, case.first) }
        val run = guard(*files.values.map { it.path }.toTypedArray())
        assertEquals(run.stdout + run.stderr, 1, run.exit)
        val byFile = files.mapValues { (_, file) -> failuresByManifest(run.stdout).entries.first { file.name == it.key }.value }
        cases.forEach { (name, case) ->
            val failures = byFile.getValue(name)
            assertTrue("$name: expected a FAIL containing '${case.second}' but got $failures", failures.any { case.second in it })
        }
    }

    @Test fun cleanManifestStaysCleanWhenExtraApprovedPermissionIsExactlyThePolicyList() {
        // Guard against an over-broad policy: nothing network/accessibility related may be approved there.
        val text = policy.readText()
        listOf("INTERNET", "ACCESS_NETWORK_STATE", "BIND_ACCESSIBILITY_SERVICE", "BIND_NOTIFICATION_LISTENER_SERVICE", "BIND_INPUT_METHOD")
            .forEach { forbidden ->
                val approvals = text.lines().map { it.substringBefore('#').trim() }.filter { it.startsWith("permission ") && forbidden in it }
                assertTrue("policy must not approve $forbidden: $approvals", approvals.isEmpty())
            }
    }

    @Test fun debuggableIsWarningInDebugAndFailureWithReleaseFlag() {
        val file = temp("debuggable", cleanText)
        val debug = guard(file.path)
        assertEquals(0, debug.exit)
        val release = guard("--release", file.path)
        assertEquals(release.stdout, 1, release.exit)
        assertTrue(release.stdout, "FAIL [debuggable]" in release.stdout)
        val notDebuggable = temp("release-ok", cleanText.replace("android:debuggable=\"true\"", "android:debuggable=\"false\""))
        assertEquals(0, guard("--release", notDebuggable.path).exit)
    }

    @Test fun doctypeAndUnreadableInputFailClosedWithExitCodeTwo() {
        val xxe = temp("xxe", "<?xml version=\"1.0\"?><!DOCTYPE m [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><manifest package=\"a.b\">&x;</manifest>")
        val doctype = guard(xxe.path)
        assertEquals(doctype.stdout + doctype.stderr, 2, doctype.exit)
        assertEquals(2, guard(File(repoRoot, "does-not-exist.xml").path).exit)
        assertEquals(2, guard().exit)
    }
}

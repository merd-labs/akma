package ph.merd.akma.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ph.merd.akma.provisioning.ProvisionFailure

/** Synthetic provisioning errors test notice/readiness behavior, never inference or physical storage. */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalModelProvisioningFailureTest {
    @Test fun everyProvisioningFailureKeepsItsSafeActionableNoticeAndCannotBecomeReady() = runTest {
        ProvisionFailure.entries.forEach { reason ->
            var calls = 0
            val failure = LocalModelProvisioningException(reason)
            val engine = object : LocalReplyEngine {
                override suspend fun initialize(): Result<Unit> {
                    calls++
                    return Result.failure(failure)
                }
                override suspend fun analyze(request: AnalyzeRequest): Result<AnalysisResult> = error("No model")
                override suspend fun draft(request: DraftRequest): Result<String> = error("No model")
            }
            val replies = ReplyCoordinator(engine, backgroundScope, StandardTestDispatcher(testScheduler))
            replies.setMessage("Synthetic message")
            replies.initialize(); runCurrent()
            val state = replies.state.value
            assertEquals(reason.name, ReplyPhase.Error, state.phase)
            assertEquals(reason.name, failure.userNotice, state.notice)
            assertTrue(failure.userNotice.isNotBlank())
            assertFalse(state.busy)
            assertFalse(state.canCopy)
            assertNull(state.analysis)
            assertNull(state.pendingConfirmation)
            assertEquals("Synthetic message", state.message)
            replies.recover(); runCurrent()
            assertEquals(ReplyPhase.ModelUnavailable, replies.state.value.phase)
            replies.initialize(); runCurrent()
            assertEquals(2, calls)
            assertEquals(failure.userNotice, replies.state.value.notice)
            assertFalse(replies.state.value.busy)
        }
    }
}

/** The model is sideloaded, so notices must tell the user to copy the file, not to reinstall an APK. */
class SideloadNoticeTest {
    @Test fun missingAndCorruptNoticesPointToTheModelFileNotAnApk() {
        listOf(ProvisionFailure.MODEL_MISSING, ProvisionFailure.SOURCE_NOT_FOUND).forEach {
            val notice = LocalModelProvisioningException(it).userNotice
            assertTrue(notice, notice.contains("gemma-4-E2B-it.litertlm") && notice.contains("Try again"))
            assertFalse(notice, notice.contains("APK") || notice.contains("bundled"))
        }
        listOf(ProvisionFailure.SIZE_MISMATCH, ProvisionFailure.HASH_MISMATCH, ProvisionFailure.FORMAT_MISMATCH).forEach {
            val notice = LocalModelProvisioningException(it).userNotice
            assertTrue(notice, notice.contains("Copy it again"))
            assertFalse(notice, notice.contains("bundled") || notice.contains("Reinstall"))
        }
    }
}

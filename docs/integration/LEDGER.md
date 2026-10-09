# Akma integration ledger

Snapshot: October 9, 2026, 23:35 PHT (`2026-10-09T15:35:13Z`). Owner: Miguel / lead integration coordinator. This ledger records observed repository and GitHub results; it does not authorize a merge or establish phone inference.

## Repository, scope and approval gate

- Verified remote: `https://github.com/merd-labs/akma.git`; GitHub CLI account: `CodeExplorer430`, repository permission ADMIN.
- Remote main: `60580cd689dfc47498c3b2f5d5c1b5488e1ee371`, documentation-only. Remote bootstrap: `02df71ad7328e55e0762d3fde8f747ef44a04348`.
- Owned worktree: `akma-worktrees/integration`; branch `feat/integration-glue`, based on bootstrap. Its head before this ledger was `5c2586c66941737e52872fb23f03cb30f3bf76ac`. Current delivery SHA and checks are recorded in PR #7 metadata/comments.
- No PR has been merged by this handoff. Accepted Android main: **NOT ESTABLISHED**. Merge SHAs: **none**. Conflicts resolved in owner branches: **none**.
- GitHub reports no submitted reviews for any of the ten inspected PRs. No approval of #13 or #4 was found in their reviews/comments. Code-generation authorization and an agent-authored review report are not team merge approval.
- No reviewer is requested or assigned in this task. Miguel and the team handle review and merge approval. Existing review requests are not changed.
- Preserve product Akma and application ID `ph.merd.akma`. Shared Kotlin types/coordinator, Gradle, wrapper, manifest and runtime remain owner-coordinated; this delivery changes integration documents only.
- The original checkout's untracked `AkmaAI_Phase2_GitOps_v3/` and every other agent worktree remain untouched. Model weights, APKs, tokens, private messages and raw device logs are not committed.

## Live PR inventory

All listed PRs are OPEN and GitHub currently reports MERGEABLE against their existing bases. Every PR except #13 is a draft. Textual mergeability is not acceptance or semantic compatibility. Rows describe the inspected heads, not future checks after retargeting.

| PR / scope | Inspected head | Base | Observed hosted jobs and evidence | Acceptance blocker |
| --- | --- | --- | --- | --- |
| [#13 CI checkout](https://github.com/merd-labs/akma/pull/13) | `f1dc5cd82f7334becf883021ed4f5e7219e595ce` | bootstrap | Documentation **PASS**, Android **PASS** on [push](https://github.com/merd-labs/akma/actions/runs/37932130112) and [PR](https://github.com/merd-labs/akma/actions/runs/37932181786) | Team approval absent; do not merge yet |
| [#4 Android bootstrap](https://github.com/merd-labs/akma/pull/4) | `02df71ad7328e55e0762d3fde8f747ef44a04348` | main | Android **PASS**, documentation **FAIL** on [push](https://github.com/merd-labs/akma/actions/runs/37925350338) and [PR](https://github.com/merd-labs/akma/actions/runs/37925388484) | Needs approved #13, fresh jobs at updated bootstrap head, and team approval |
| [#14 domain action safety](https://github.com/merd-labs/akma/pull/14) | `10f7dc4799b1cbb527f7f0a4484fbd2239c60c29` | bootstrap | Android **PASS**, documentation **FAIL** on [push](https://github.com/merd-labs/akma/actions/runs/37934111017) and [PR](https://github.com/merd-labs/akma/actions/runs/37934117978) | Confirmation UI bridge missing; combined overlay/test reconciliation and approval required |
| [#12 overlay](https://github.com/merd-labs/akma/pull/12) | `8405ed8b14183d864bc007a67afca481bd2da53c` | bootstrap | Android **PASS**, documentation **FAIL** on [push](https://github.com/merd-labs/akma/actions/runs/37938016757) and [PR](https://github.com/merd-labs/akma/actions/runs/37938023033) | Needs CI inheritance and approval; present fixtures are incompatible with #14 |
| [#16 desktop prompt lab](https://github.com/merd-labs/akma/pull/16) | `4239ce2e73aec341dda75f4e5cfbf1bdd1299c5e` | main | Job named Android **PASS** on [push](https://github.com/merd-labs/akma/actions/runs/37945289474) and [PR](https://github.com/merd-labs/akma/actions/runs/37945592220), but logs report documentation-only status and skip Android compilation | Research only; Python checks and integrated Android behavior **NOT TESTED** by coordinator |
| [#11 device tooling](https://github.com/merd-labs/akma/pull/11) | `a43ef4c61d4ec65783beea2ce2ebba868860324c` | bootstrap | Android **PASS**, documentation **FAIL** on [push](https://github.com/merd-labs/akma/actions/runs/37932641845) and [PR](https://github.com/merd-labs/akma/actions/runs/37932648217) | Approval, CI inheritance and an explicit exclusive handset slot |
| [#6 domain tests](https://github.com/merd-labs/akma/pull/6) | `f79102537af7d01220b062f1d7f00ddbe9fe2271` | bootstrap | Android **FAIL**, documentation **FAIL** on [push](https://github.com/merd-labs/akma/actions/runs/37925479457) and [PR](https://github.com/merd-labs/akma/actions/runs/37925498078) | Old contract assumptions; 51 tests completed, one three-action regression failure |
| [#7 integration documents](https://github.com/merd-labs/akma/pull/7) | `5c2586c66941737e52872fb23f03cb30f3bf76ac` before ledger | bootstrap | Android **PASS**, documentation **FAIL** on previous [push](https://github.com/merd-labs/akma/actions/runs/37925884997) and [PR](https://github.com/merd-labs/akma/actions/runs/37925891209) | New documentation commit needs its own checks; CI inheritance and approval |
| [#10 security review](https://github.com/merd-labs/akma/pull/10) | `b15015b3f9e766aa0a0e1fb9485886441b981f56` | bootstrap | Android **PASS**, documentation **FAIL** on [push](https://github.com/merd-labs/akma/actions/runs/37932822895) and [PR](https://github.com/merd-labs/akma/actions/runs/37932829106) | Review findings are evidence, not human merge approval; CI inheritance required |
| [#1 submission documents](https://github.com/merd-labs/akma/pull/1) | `bc2cbea84c2bb41e7ae891a9f55ca354ef63a306` | main | Job named Android **PASS** on [push](https://github.com/merd-labs/akma/actions/runs/37952347357) and [PR](https://github.com/merd-labs/akma/actions/runs/37952351837); branch has no Android baseline | Excluded: baseline document conflicts, new whitespace findings and unsupported checklist PASS claims need owner correction/review |

## CI fix and executable-main gate

`gh pr diff 13` confirms exactly two added lines in `.github/workflows/ci.yml`: documentation checkout gains `with: fetch-depth: 2`. `git show --format= --check HEAD` and the complete Android job stay unchanged. Elijah's archived originals are not edited.

Rechecked #13 with `gh pr checks 13`: push documentation 6 seconds, push Android 2m 23s; PR documentation 3 seconds, PR Android 3m 8s; all **PASS**. `gh run view` confirms both completed runs have `success` and the exact `f1dc5cd` head. This resolves the earlier pending observation and TLS diagnostic gap. It does not update bootstrap or main until an approved merge occurs.

After explicit team approval for the exact #13 head, use `gh pr merge 13 --repo merd-labs/akma --merge --match-head-commit <approved-head>` without administrator bypass or branch deletion. Record returned/observed merge SHA, fetch and verify the updated bootstrap ref. Recheck fresh push and #4 PR jobs; do not substitute #13 results for those jobs.

After approval for the updated #4 head and passing documentation/build/unit/lint jobs, make the approved draft ready if necessary and merge #4 through GitHub using a merge commit and the approved head guard. Verify remote main updates, contains the accepted Android tree and passes its own workflow. Then build/test/lint that exact main in an isolated verification worktree. Until then main is documentation-only and no runnable-main claim is made.

Only after bootstrap is accepted may stacked PRs be retargeted to main. Inspect each ancestry and diff first. Coordinate the owner to merge updated main into the feature branch non-force so its push workflow also receives the CI correction; changing the PR base alone does not update push workflow files. Recheck conflicts and fresh CI individually.

## Compatibility checks and unresolved conflicts

These checks used fetched refs and `git merge-tree --write-tree`; generated Git objects were not applied to any branch or working tree. A clean textual result does not prove a compilable or working combination.

| Candidate combination | Observed Git result | Runtime/test acceptance |
| --- | --- | --- |
| #14 domain + #12 overlay | Exit 0, no text conflicts; candidate tree `46b713a808f439e271e81f0f5d3ce5908e289b0d` | **NOT TESTED** together; known source-level fixture/UI incompatibilities below |
| Bootstrap + #1 submission | Exit 1; content conflicts in README.md and docs/AI_DISCLOSURE.md | **NOT TESTED**; no conflict resolution applied |
| Bootstrap + #16 lab | Exit 0, no text conflicts; candidate tree `7c0cc473ee3cd8939ebe1536140b1f65a8446ca2` | **NOT TESTED** together; desktop lab is not a phone runtime |

Verified source differences requiring owner changes before a domain integration:

- #14 adds adapter-owned `AnalysisSource`, category-specific canonical actions and maximum three results. Unspecified provenance and unknown/category-incompatible IDs fail normalization. `draft()` stages `pendingConfirmation`; only a separate human `confirmDraft(id)` starts generation.
- Neither inspected Activity nor overlay UI supplies the confirmation controls. Do not automatically call Confirm after an action click or substitute a newer confirmation ID. Show the exact canonical action/tone from the pending immutable request; invalidate/rebuild confirmation when selection changes.
- #12's OverlayDismissalTest uses category `Other`, action `clarify`, default unspecified source and immediate inference after `draft()`. These fixtures must use an accepted category/action pair, explicit test provenance and separate confirmation. Keep dismissal/late-result assertions; do not suppress tests.
- #6's fixtures also omit provenance, assume arbitrary categories/model labels remain authoritative and expect immediate generation after an action click. After accepted domain changes, retain input boundaries, cancellation, serialization and request/tone propagation coverage; update actual fixtures/assertions and add explicit confirmation. Consolidate duplicate tests only after reviewing their coverage. The combined suite has **NOT BEEN RUN**.

`gh run view 37925498078 --log-failed` directly confirms `ReplyValidationBoundaryTest.requestedThreeActionCapRejectsFourReviewedActions FAILED`, 51 tests completed / one failed, and `:app:testDebugUnitTest` exit 1. This is a real domain failure, distinct from documentation false positives.

#1 changed again after the preceding plan. Its README now declares desktop research PASS and its release checklist declares several approvals/disclosures PASS, but those statements are not coordinator-observed verification. It still specifies SDK30 installation while the real build compiles SDK36, gives an unvalidated model assets path, and conflicts with accepted-baseline documentation. `git diff --check a233dc8..origin/docs/submission-readiness` reports two newly introduced trailing-whitespace lines in README.md (3 and 9). Those findings are actual new whitespace, not preserved-archive false positives. Do not edit this owner's branch or accept its checklist as approval.

## Runtime and physical-device gate

#16 changes `.gitignore` and `testing-llm/**`, with no Android engine implementation. Its workflow log explicitly prints `Documentation bootstrap only: Android/Gradle project has not been generated yet.` Desktop research does not validate Android API30 compatibility, local phone generation, memory, latency or language quality. No available verified branch supplies the Android engine or confirmation UI bridge; production still binds `UnavailableReplyEngine`.

The earlier planning audit observed a physical LE7 attached. This delivery does not reserve the phone, install an APK, change settings, collect content or run inference. #12 contains another owner's bounded Pova 2 overlay observations and APK hash; these are **owner-reported evidence**, not checks rerun by this coordinator or proof of inference. Current exclusive device slot: **NOT ESTABLISHED**. Integrated physical acceptance: **NOT TESTED**.

Create `feat/final-integration` in an isolated final-integration worktree only after Elijah supplies the real Android engine head and the UI owner supplies the confirmation bridge head. Review exact runtime/API30/ABI compatibility, model license/source/revision/hash/provisioning and offline Pova 2 evidence before choosing a backend or model. Combine only necessary approved commits and allocated files; preserve shared configuration ownership. No cloud substitute, fake success, automatic clipboard capture or automatic sending.

After the domain/bridge/engine integration is ready, test initialized/unavailable/loading/error/cancel/retry paths, canonical action/tone confirmation, stale/duplicate confirmations, malformed untrusted output, editing and explicit Copy. Obtain an exclusive Pova 2 slot and run three distinct synthetic generations with Wi-Fi/mobile data disabled after disclosed provisioning; record hashes, latency, memory and observed limitations. Secondary-device and Windows checks remain **NOT TESTED** unless performed.

## Verification, order and next operators

Commands executed for this handoff: `git fetch origin`, status/remotes/worktree inspection, `gh auth status`, `gh repo view`, `git ls-remote --heads origin`, live PR/check/review queries, `gh pr diff 13`, completed #13 run inspection, #6 failed-log inspection, source/diff inspection, the three merge-tree checks, and the scoped #1 whitespace check. PR #16's workflow log was inspected to distinguish a stub job from a build. None of these merge-tree checks changes a branch.

Local checks for this document delivery are whitespace, scoped staging, local Markdown links and a redacted staged secret scan; results are recorded in PR #7. Gradle is not rerun for this documentation-only change. No new production build, combined unit/lint run or device acceptance is claimed. New hosted results must be checked after the ledger push.

Integration order, each requiring team approval and current checks:

1. #13 into bootstrap, then fresh #4 checks and #4 into main. This is the first accepted Android baseline; no later PR can substitute for it.
2. Retarget/synchronize and verify #12 on the baseline contract. #11/#7/#10 may follow as independently accepted tooling/evidence/documentation changes.
3. Obtain the UI confirmation bridge and engine handoffs; integrate #14 with compatible UI and updated overlay tests as a complete safe change. Do not ship an unreachable confirmation path.
4. Reconcile #6 against the accepted domain source and run the entire combined suite before acceptance. #16 remains optional research; #1 remains excluded pending its owner's corrections and review.
5. Final engine/UI integration, local full gate, exclusive offline device gate, then approved GitHub merge and remote-main revalidation.

Miguel/team supplies exact merge approvals and any shared-file delegation. Domain, overlay and domain-test owners reconcile their allocated source/tests. Elijah supplies the Android engine and device evidence; the UI owner supplies confirmation controls. The coordinator stops unsafe merges on absent approval, failing checks, missing commits or contract incompatibility, while completing independent documentation/diagnostics.

package ph.merd.akma.provisioning

/**
 * Pinned litert-community/gemma-4-E2B-it-litert-lm artifact (Apache-2.0). Changing the model requires owner
 * review and device proof.
 *
 * Chosen for Filipino/Taglish: in desktop runs Qwen3-1.7B produced incoherent Filipino, while this model was
 * coherent and on-intent, and about 4x faster on CPU (docs/evidence/gemma4-e2b-filipino-gate.md).
 * The file is 2.59 GB, above the 2 GiB single-asset limit, so it cannot be bundled in the APK. It is
 * sideloaded: `adb push` into the app's external files `models/` directory (see [openModelSource]).
 */
object LocalModelArtifact {
    val spec = ModelArtifactSpec(
        filename = "gemma-4-E2B-it.litertlm",
        revision = "b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1",
        sizeBytes = 2588147712L,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        format = ModelFormat.LITERT_LM,
    )
}

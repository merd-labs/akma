package ph.merd.akma.provisioning

/**
 * Pinned litert-community/Qwen3-1.7B int8 artifact (Apache-2.0). Changing the model requires owner review/device proof.
 * Chosen over the 977 MB int4 file because int4 block-32 is a GPU specialist: on CPU (the only backend used here) its
 * prefill is 3-5x slower, and this app's latency is prefill-bound.
 */
object BundledQwenArtifact {
    val spec = ModelArtifactSpec(
        filename = "Qwen3_1.7B.litertlm",
        revision = "73fbc3fe8271c162a603ee66f6e7ed25b6211195",
        sizeBytes = 2056729520L,
        sha256 = "66064a4e9269cb693e124c4e3040bcb8a446b10bca42663896329495add3861c",
        format = ModelFormat.LITERT_LM,
    )
}

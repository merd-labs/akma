package ph.merd.akma.provisioning

/** Owner-supplied pins from runtime PR #26. Changing the model requires owner review/device proof. */
object BundledQwenArtifact {
    val spec = ModelArtifactSpec(
        filename = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        revision = "19edb84c69a0212f29a6ef17ba0d6f278b6a1614",
        sizeBytes = 1597931520L,
        sha256 = "faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9",
        format = ModelFormat.LITERT_LM,
    )
}

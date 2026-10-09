package ph.merd.akma.provisioning

/** Private cache key, never persisted or included in diagnostic output. */
internal data class ModelFileIdentity(
    val device: Long,
    val inode: Long,
    val size: Long,
    val modifiedSeconds: Long,
    val modifiedNanos: Long,
    val changedSeconds: Long,
    val changedNanos: Long,
    val mode: Int,
    val links: Long,
)

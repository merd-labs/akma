package ph.merd.akma.overlay

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Lifecycle for Compose content in the overlay window (ADR-002 update: the panel is Compose, shared
 * with the Activity). One owner per displayed panel; destroyed when the panel collapses or closes.
 * Nothing is saved: the overlay never restores input across windows.
 */
internal class OverlayComposeHost : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private var root: View? = null
    private var destroyed = false

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun attachTo(root: View) {
        check(!destroyed && this.root == null) { "An overlay panel owner can only attach once." }
        this.root = root
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
        root.setViewTreeLifecycleOwner(this)
        root.setViewTreeSavedStateRegistryOwner(this)
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        if (registry.currentState != Lifecycle.State.INITIALIZED) registry.currentState = Lifecycle.State.DESTROYED
        root?.let {
            // A newer panel may already own the same window; never remove its owners.
            if (it.findViewTreeLifecycleOwner() === this) it.setViewTreeLifecycleOwner(null)
            if (it.findViewTreeSavedStateRegistryOwner() === this) it.setViewTreeSavedStateRegistryOwner(null)
        }
        root = null
    }
}

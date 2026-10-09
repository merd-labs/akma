package ph.merd.akma.ui

/** Activity screens in Figma order: B Landing, C Set up, D Akma is on; Reply is the Activity fallback. */
enum class AkmaScreen { Landing, Setup, Home, Reply }

/**
 * First screen for a launch. [onboarded] is set by Get started; [setupDone] by first switching
 * the bubble on. Both are UI conveniences only: losing them just shows Landing or Setup again.
 */
fun startScreen(onboarded: Boolean, setupDone: Boolean): AkmaScreen = when {
    !onboarded -> AkmaScreen.Landing
    !setupDone -> AkmaScreen.Setup
    else -> AkmaScreen.Home
}

/** Back from a screen; null lets the system handle it (leave the app). */
fun AkmaScreen.back(setupDone: Boolean): AkmaScreen? = when (this) {
    AkmaScreen.Reply -> if (setupDone) AkmaScreen.Home else AkmaScreen.Setup
    else -> null
}

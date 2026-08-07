package com.beemdevelopment.aegis.desktop

import com.beemdevelopment.aegis.CopyBehavior

// Android's gesture names, reused so the setting matches: SINGLETAP is a click, DOUBLETAP a double.
object CopyBehaviorExt {
    fun CopyBehavior.shouldCopyOnClick(): Boolean = this == CopyBehavior.SINGLETAP

    fun CopyBehavior.shouldCopyOnDoubleClick(): Boolean = this == CopyBehavior.DOUBLETAP
}

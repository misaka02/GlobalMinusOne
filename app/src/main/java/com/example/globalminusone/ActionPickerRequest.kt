package com.example.globalminusone

import android.content.Context
import android.content.Intent

internal enum class ActionPickerKind { ACTIVITY, CONTACT, CREATE_SHORTCUT }

/** One transient route contract. The legacy extra names remain compatible. */
internal data class ActionPickerRequest(
    val kind: ActionPickerKind,
    val pageId: String,
    val containerId: String? = null,
    val beforeComponentId: String? = null,
    val shortcutComponent: String? = null
) {
    fun createIntent(context: Context): Intent = Intent(context, ActionPickerActivity::class.java)
        .putExtra(KIND, kind.name).putExtra(PAGE, pageId)
        .putExtra(CONTAINER, containerId).putExtra(BEFORE, beforeComponentId)
        .putExtra(SHORTCUT_COMPONENT, shortcutComponent)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        const val KIND = "kind"
        const val PAGE = "page_id"
        const val CONTAINER = "container_id"
        const val BEFORE = "before_component_id"
        const val SHORTCUT_COMPONENT = "shortcut_component"

        fun read(intent: Intent): ActionPickerRequest? = fromLegacy(
            intent.getStringExtra(KIND), intent.getStringExtra(PAGE),
            intent.getStringExtra(CONTAINER), intent.getStringExtra(BEFORE),
            intent.getStringExtra(SHORTCUT_COMPONENT))

        fun fromLegacy(
            kind: String?,
            pageId: String?,
            containerId: String?,
            beforeId: String?,
            shortcutComponent: String? = null
        ): ActionPickerRequest? {
            if (pageId.isNullOrBlank()) return null
            // Previously only CONTACT selected a special picker; all other non-CREATE_SHORTCUT values mean activities.
            val parsed = when (kind) {
                ActionPickerKind.CONTACT.name -> ActionPickerKind.CONTACT
                ActionPickerKind.CREATE_SHORTCUT.name -> ActionPickerKind.CREATE_SHORTCUT
                else -> ActionPickerKind.ACTIVITY
            }
            return ActionPickerRequest(parsed, pageId, containerId, beforeId, shortcutComponent?.takeIf { it.isNotBlank() })
        }
    }
}

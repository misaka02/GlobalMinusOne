package com.example.globalminusone

import android.content.Context
import android.content.Intent

internal enum class ActionPickerKind { ACTIVITY, CONTACT }

/** One transient route contract. The legacy extra names remain compatible. */
internal data class ActionPickerRequest(
    val kind: ActionPickerKind,
    val pageId: String,
    val containerId: String? = null,
    val beforeComponentId: String? = null
) {
    fun createIntent(context: Context): Intent = Intent(context, ActionPickerActivity::class.java)
        .putExtra(KIND, kind.name).putExtra(PAGE, pageId)
        .putExtra(CONTAINER, containerId).putExtra(BEFORE, beforeComponentId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        const val KIND = "kind"
        const val PAGE = "page_id"
        const val CONTAINER = "container_id"
        const val BEFORE = "before_component_id"

        fun read(intent: Intent): ActionPickerRequest? = fromLegacy(
            intent.getStringExtra(KIND), intent.getStringExtra(PAGE),
            intent.getStringExtra(CONTAINER), intent.getStringExtra(BEFORE))

        fun fromLegacy(kind: String?, pageId: String?, containerId: String?, beforeId: String?): ActionPickerRequest? {
            if (pageId.isNullOrBlank()) return null
            // Previously only CONTACT selected a special picker; all other values meant activities.
            val parsed = if (kind == ActionPickerKind.CONTACT.name) ActionPickerKind.CONTACT else ActionPickerKind.ACTIVITY
            return ActionPickerRequest(parsed, pageId, containerId, beforeId)
        }
    }
}

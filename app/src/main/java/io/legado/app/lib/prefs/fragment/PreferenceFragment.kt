package io.legado.app.lib.prefs.fragment

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import io.legado.app.R
import io.legado.app.lib.prefs.EditTextPreferenceDialog
import io.legado.app.lib.prefs.ListPreferenceDialog
import io.legado.app.lib.prefs.MultiSelectListPreferenceDialog
import io.legado.app.lib.theme.cardBackground
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.dpToPx

abstract class PreferenceFragment : PreferenceFragmentCompat() {

    private val dialogFragmentTag = "androidx.preference.PreferenceFragment.DIALOG"
    private val cardBackgroundMap = mutableMapOf<Int, Drawable>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.clipToPadding = false
        listView.applyNavigationBarPadding()
        listView.setPadding(
            16.dpToPx(),
            listView.paddingTop,
            16.dpToPx(),
            listView.paddingBottom + 16.dpToPx()
        )
    }

    /**
     * Call after addPreferencesFromResource() to compute and apply MD3 card backgrounds.
     * Uses OnChildAttachStateChangeListener to apply backgrounds on every view attach
     * (including recycled views), without wrapping the adapter.
     */
    protected fun setupCardBackgrounds() {
        val screen = preferenceScreen ?: return
        computeCardBackgrounds(screen)
        if (cardBackgroundMap.isEmpty()) return

        listView.addOnChildAttachStateChangeListener(
            object : androidx.recyclerview.widget.RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(child: View) {
                    val pos = listView.getChildAdapterPosition(child)
                    if (pos >= 0) {
                        cardBackgroundMap[pos]?.let { child.background = it }
                    }
                }
                override fun onChildViewDetachedFromWindow(child: View) {}
            }
        )
        // Apply to already-visible children
        for (i in 0 until listView.childCount) {
            val child = listView.getChildAt(i)
            val pos = listView.getChildAdapterPosition(child)
            if (pos >= 0) {
                cardBackgroundMap[pos]?.let { child.background = it }
            }
        }
    }

    private fun computeCardBackgrounds(screen: PreferenceGroup) {
        data class Range(val first: Int, val last: Int)
        val ranges = mutableListOf<Range>()
        var pos = 0

        for (i in 0 until screen.preferenceCount) {
            val pref = screen.getPreference(i)
            if (pref is PreferenceCategory) {
                pos++
                val children = collectChildPositions(pref, pos)
                if (children.isNotEmpty()) {
                    ranges.add(Range(children.first(), children.last()))
                }
                pos += countAll(pref)
            } else {
                pos++
            }
        }

        // Top-level items (not in any category)
        val topLevel = mutableListOf<Int>()
        pos = 0
        for (i in 0 until screen.preferenceCount) {
            val pref = screen.getPreference(i)
            if (pref is PreferenceCategory) {
                pos++
                pos += countAll(pref)
            } else {
                topLevel.add(pos)
                pos++
            }
        }
        if (topLevel.isNotEmpty()) {
            ranges.add(Range(topLevel.first(), topLevel.last()))
        }

        // Use actual theme card background color (responds to Theme Settings)
        val cardColor = requireContext().cardBackground
        val corner = 12f.dpToPx()

        cardBackgroundMap.clear()
        for (range in ranges) {
            if (range.first == range.last) {
                cardBackgroundMap[range.first] = makeCardBg(cardColor, corner, corner, corner, corner)
            } else {
                cardBackgroundMap[range.first] = makeCardBg(cardColor, corner, corner, 0f, 0f)
                for (p in (range.first + 1) until range.last) {
                    cardBackgroundMap[p] = makeCardBg(cardColor, 0f, 0f, 0f, 0f)
                }
                cardBackgroundMap[range.last] = makeCardBg(cardColor, 0f, 0f, corner, corner)
            }
        }
    }

    private fun makeCardBg(
        @androidx.annotation.ColorInt color: Int,
        topLeft: Float, topRight: Float,
        bottomLeft: Float, bottomRight: Float
    ): StateListDrawable {
        val pressed = GradientDrawable().apply {
            setColor(ContextCompat.getColor(requireContext(), R.color.btn_bg_press))
            cornerRadii = floatArrayOf(
                topLeft, topLeft, topRight, topRight,
                bottomLeft, bottomLeft, bottomRight, bottomRight
            )
        }
        val normal = GradientDrawable().apply {
            setColor(color)
            cornerRadii = floatArrayOf(
                topLeft, topLeft, topRight, topRight,
                bottomLeft, bottomLeft, bottomRight, bottomRight
            )
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(android.R.attr.state_focused), pressed)
            addState(intArrayOf(), normal)
        }
    }

    private fun collectChildPositions(group: PreferenceGroup, startPos: Int): List<Int> {
        val result = mutableListOf<Int>()
        var pos = startPos
        for (i in 0 until group.preferenceCount) {
            val pref = group.getPreference(i)
            if (pref is PreferenceCategory) {
                pos++
                result.addAll(collectChildPositions(pref, pos))
                pos += countAll(pref)
            } else {
                result.add(pos)
                pos++
            }
        }
        return result
    }

    private fun countAll(group: PreferenceGroup): Int {
        var count = 0
        for (i in 0 until group.preferenceCount) {
            val pref = group.getPreference(i)
            if (pref is PreferenceCategory) {
                count++
                count += countAll(pref)
            } else {
                count++
            }
        }
        return count
    }

    @SuppressLint("RestrictedApi")
    override fun onDisplayPreferenceDialog(preference: Preference) {

        var handled = false
        if (callbackFragment is OnPreferenceDisplayDialogCallback) {
            handled =
                (callbackFragment as OnPreferenceDisplayDialogCallback)
                    .onPreferenceDisplayDialog(this, preference)
        }
        if (!handled && activity is OnPreferenceDisplayDialogCallback) {
            handled = (activity as OnPreferenceDisplayDialogCallback)
                .onPreferenceDisplayDialog(this, preference)
        }

        if (handled) {
            return
        }

        // check if dialog is already showing
        if (parentFragmentManager.findFragmentByTag(dialogFragmentTag) != null) {
            return
        }

        val dialogFragment: DialogFragment = when (preference) {
            is EditTextPreference -> {
                EditTextPreferenceDialog.newInstance(preference.getKey())
            }
            is ListPreference -> {
                ListPreferenceDialog.newInstance(preference.getKey())
            }
            is MultiSelectListPreference -> {
                MultiSelectListPreferenceDialog.newInstance(preference.getKey())
            }
            else -> {
                throw IllegalArgumentException(
                    "Cannot display dialog for an unknown Preference type: "
                            + preference.javaClass.simpleName
                            + ". Make sure to implement onPreferenceDisplayDialog() to handle "
                            + "displaying a custom dialog for this preference type."
                )
            }
        }
        @Suppress("DEPRECATION")
        dialogFragment.setTargetFragment(this, 0)

        dialogFragment.show(parentFragmentManager, dialogFragmentTag)
    }

}

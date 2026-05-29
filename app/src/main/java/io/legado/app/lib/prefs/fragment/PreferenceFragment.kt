package io.legado.app.lib.prefs.fragment

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.View.OnScrollChangeListener
import androidx.fragment.app.DialogFragment
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.lib.prefs.EditTextPreferenceDialog
import io.legado.app.lib.prefs.ListPreferenceDialog
import io.legado.app.lib.prefs.MultiSelectListPreferenceDialog
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
     * Call after addPreferencesFromResource() to compute card backgrounds
     * and apply them via a scroll listener (reapplies on every scroll settle).
     */
    protected fun setupCardBackgrounds() {
        computeCardBackgrounds()
        listView.setOnScrollChangeListener(OnScrollChangeListener { _, _, _, _, _ ->
            applyVisibleCardBackgrounds()
        })
        listView.post { applyVisibleCardBackgrounds() }
    }

    private fun computeCardBackgrounds() {
        val screen = preferenceScreen ?: return

        data class CategoryRange(val first: Int, val last: Int)
        val categoryRanges = mutableMapOf<String, CategoryRange>()
        var adapterPos = 0

        for (i in 0 until screen.preferenceCount) {
            val pref = screen.getPreference(i)
            if (pref is PreferenceCategory) {
                val catKey = pref.key ?: "cat_$i"
                val children = mutableListOf<Int>()
                collectChildPositions(pref, adapterPos + 1, children)
                if (children.isNotEmpty()) {
                    categoryRanges[catKey] = CategoryRange(children.first(), children.last())
                }
                adapterPos++
                adapterPos += children.size
            } else {
                adapterPos++
            }
        }

        // Handle top-level items (not in any category) as a single card group
        val topLevelPositions = mutableListOf<Int>()
        adapterPos = 0
        for (i in 0 until screen.preferenceCount) {
            val pref = screen.getPreference(i)
            if (pref is PreferenceCategory) {
                adapterPos++
                adapterPos += countChildren(pref)
            } else {
                topLevelPositions.add(adapterPos)
                adapterPos++
            }
        }
        if (topLevelPositions.isNotEmpty()) {
            categoryRanges["__topLevel__"] = CategoryRange(
                topLevelPositions.first(),
                topLevelPositions.last()
            )
        }

        // Build position -> drawable map
        cardBackgroundMap.clear()
        val res = resources
        for ((_, range) in categoryRanges) {
            if (range.first == range.last) {
                cardBackgroundMap[range.first] =
                    res.getDrawable(R.drawable.bg_card_single, null)
            } else {
                for (pos in range.first..range.last) {
                    cardBackgroundMap[pos] = when (pos) {
                        range.first -> res.getDrawable(R.drawable.bg_card_top, null)
                        range.last -> res.getDrawable(R.drawable.bg_card_bottom, null)
                        else -> res.getDrawable(R.drawable.bg_card_middle, null)
                    }
                }
            }
        }
    }

    private fun applyVisibleCardBackgrounds() {
        val rv = listView
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        if (first < 0 || last < 0) return
        for (pos in first..last) {
            val view = rv.getChildAt(pos - first) ?: continue
            cardBackgroundMap[pos]?.let { view.background = it }
        }
    }

    private fun collectChildPositions(
        group: PreferenceGroup,
        startPos: Int,
        positions: MutableList<Int>
    ) {
        var pos = startPos
        for (i in 0 until group.preferenceCount) {
            val pref = group.getPreference(i)
            if (pref is PreferenceCategory) {
                pos++
                collectChildPositions(pref, pos, positions)
                pos += countChildren(pref)
            } else {
                positions.add(pos)
                pos++
            }
        }
    }

    private fun countChildren(group: PreferenceGroup): Int {
        var count = 0
        for (i in 0 until group.preferenceCount) {
            val pref = group.getPreference(i)
            if (pref is PreferenceCategory) {
                count++
                count += countChildren(pref)
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

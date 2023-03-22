/*
 * Copyright © MURENA SAS 2023.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package foundation.e.bliss.suggestions

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.util.Log
import android.view.DragEvent
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat.startActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R
import com.android.launcher3.allapps.AllAppsStore.OnUpdateListener
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem
import com.android.launcher3.allapps.search.DefaultAppSearchAlgorithm
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.search.SearchCallback
import foundation.e.bliss.LauncherAppMonitor
import foundation.e.bliss.utils.hideKeyboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@SuppressLint("CheckResult")
class BlissInput(context: Context, attrs: AttributeSet) :
    LinearLayout(context, attrs), SearchCallback<AdapterItem>, OnUpdateListener {
    private val mSearchAlgorithm = DefaultAppSearchAlgorithm(context, true)
    private val suggestionProvider by lazy { SearchSuggestionUtil().getSuggestionProvider(context) }
    private val suggestionAdapter by lazy { AutoCompleteAdapter(context) }
    private val idp by lazy { InvariantDeviceProfile.INSTANCE.get(context) }
    private val appUsageStats by lazy { AppUsageStats(context).usageStats }
    private val mAppsStore by lazy {
        LauncherAppMonitor.INSTANCE.get(context).launcher.appsView.appsStore
    }

    private var results: SuggestionsResult? = null
    private lateinit var mSearchInput: EditText
    private lateinit var mIconGrid: GridLayout
    private lateinit var mAppsLayout: View
    private lateinit var mClearButton: ImageView
    private lateinit var mSuggestionRv: RecyclerView

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mSearchInput = findViewById(R.id.search_input)
        mAppsLayout = (parent.parent as View).findViewById(R.id.used_apps_layout)
        mIconGrid = mAppsLayout.findViewById(R.id.suggestedAppGrid)
        mClearButton = (mSearchInput.parent as View).findViewById(R.id.clearSuggestions)
        mSuggestionRv = mAppsLayout.findViewById(R.id.suggestionRecyclerView)

        mSearchInput.apply {
            addTextChangedListener(
                doAfterTextChanged { text ->
                    if (text.isNullOrEmpty()) {
                        clearSearchResult()
                        loadSuggestions()
                        return@doAfterTextChanged
                    }

                    mClearButton.visibility = View.VISIBLE
                    mSearchAlgorithm.cancel(false)
                    mSearchAlgorithm.doSearch(text.trim().toString(), this@BlissInput)

                    if (
                        text.trim().toString().isNotEmpty() && text.toString() != results?.queryText
                    ) {
                        loadSearchSuggestions(text.toString())
                    }
                }
            )

            setOnKeyListener { view, keyCode, _ ->
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    hideKeyboard(context, view)
                    clearFocus()
                    openSearch(text.toString())
                }
                true
            }

            mClearButton.setOnClickListener {
                clearSearchResult()
                loadSuggestions()
            }
        }

        mSuggestionRv.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            suggestionAdapter.mOnSuggestionClick = { openSearch(it) }
            adapter = suggestionAdapter
        }

        mAppsStore.addUpdateListener(this)
    }

    private fun loadSearchSuggestions(query: String) =
        CoroutineScope(Dispatchers.Main).launch {
            val task = async(Dispatchers.IO) { suggestionProvider.query(query) }
            results = task.await()
            results?.let { suggestionAdapter.updateSuggestions(it.networkItems, it.queryText) }
        }

    override fun onDragEvent(event: DragEvent): Boolean {
        // Without this drag/drop apps won't work on API <24.
        // EditTexts seem to interfere with drag/drop.
        return false
    }

    override fun onSearchResult(query: String?, items: ArrayList<AdapterItem>?) {
        if (items.isNullOrEmpty()) {
            clearSearchResult()
            return
        }

        mAppsLayout.clipToOutline = true
        mIconGrid.removeAllViews()
        items
            .map { it.itemInfo }
            .filter {
                it.componentName != null &&
                    !context.resources
                        .getStringArray(R.array.blacklisted_apps)
                        .contains(it.targetPackage)
            }
            .forEachIndexed { index, it ->
                if (index >= idp.numColumns) return

                mIconGrid.apply {
                    columnCount = idp.numColumns
                    clipToPadding = false
                    clipChildren = false
                    createAppView(it).also { addView(it, index) }
                }
            }
    }

    private fun createAppView(info: AppInfo): BubbleTextView {
        return (LayoutInflater.from(context).inflate(R.layout.app_icon, null) as BubbleTextView)
            .apply {
                tag = info
                applyFromApplicationInfo(info)
                setForceHideDot(true)
                setOnClickListener(
                    LauncherAppMonitor.getInstance(context).launcher.itemOnClickListener
                )
            }
    }

    private fun loadSuggestions() {
        val appsList = mAppsStore.apps.toList()

        Log.i(TAG, "Apps List Size ${appsList.size}")

        mIconGrid.removeAllViews()
        if (appsList.isNotEmpty()) {
            if (appUsageStats.isNotEmpty()) {
                appUsageStats
                    .mapNotNull { pkg -> appsList.find { it.targetPackage == pkg.packageName } }
                    .subList(0, idp.numColumns)
                    .forEachIndexed { index, it -> mIconGrid.addView(createAppView(it), index) }
            }
        }
    }

    private fun openSearch(query: String) {
        val intent =
            Intent(Intent.ACTION_VIEW, SearchSuggestionUtil().getUriForQuery(context, query))
        startActivity(context, intent, null)
    }

    override fun clearSearchResult() {
        mIconGrid.removeAllViews()
        suggestionAdapter.updateSuggestions(emptyList(), "")
        mSearchInput.text?.clear()
        mClearButton.visibility = View.GONE
    }

    companion object {
        private const val TAG = "BlissInput"
    }

    override fun onAppsUpdated() = loadSuggestions()
}

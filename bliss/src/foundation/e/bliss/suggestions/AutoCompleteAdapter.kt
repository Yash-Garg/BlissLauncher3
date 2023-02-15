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
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.R
import java.util.Locale

class AutoCompleteAdapter(context: Context) :
    RecyclerView.Adapter<AutoCompleteAdapter.AutoCompleteViewHolder>() {
    lateinit var mOnSuggestionClick: (String) -> Unit
    private var mItems: List<String?> = emptyList()
    private val mInflater: LayoutInflater
    private var mQueryText: String? = null

    init {
        mInflater = LayoutInflater.from(context)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AutoCompleteViewHolder {
        val view: View = mInflater.inflate(R.layout.item_suggestion, parent, false)
        val holder = AutoCompleteViewHolder(view)
        view.setOnClickListener {
            mItems[holder.absoluteAdapterPosition]?.let { q -> mOnSuggestionClick(q) }
        }
        return holder
    }

    override fun onBindViewHolder(holder: AutoCompleteViewHolder, position: Int) {
        val suggestion = mItems[position] ?: ""
        if (mQueryText != null) {
            val spannable = SpannableStringBuilder(suggestion)
            val lcSuggestion = suggestion.lowercase(Locale.getDefault())
            var queryTextPos = lcSuggestion.indexOf(mQueryText!!)
            while (queryTextPos >= 0) {
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    queryTextPos,
                    queryTextPos + mQueryText!!.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                queryTextPos =
                    lcSuggestion.indexOf(mQueryText!!, queryTextPos + mQueryText!!.length)
            }
            holder.mSuggestionTextView.text = spannable
        } else {
            holder.mSuggestionTextView.text = suggestion
        }
        setFadeAnimation(holder.itemView)
    }

    override fun getItemCount() = mItems.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateSuggestions(suggestions: List<String?>, queryText: String?) {
        mItems = suggestions
        mQueryText = queryText
        notifyDataSetChanged()
    }

    class AutoCompleteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val mSuggestionTextView: TextView

        init {
            mSuggestionTextView = itemView.findViewById(R.id.suggestionTextView)
        }
    }

    private fun setFadeAnimation(view: View) {
        val anim = AlphaAnimation(0.0f, 1.0f)
        anim.duration = 300
        view.startAnimation(anim)
    }
}

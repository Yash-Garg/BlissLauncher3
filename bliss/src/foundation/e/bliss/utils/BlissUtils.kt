/*
 * Copyright © MURENA SAS 2023.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package foundation.e.bliss.utils

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

fun hideKeyboard(context: Context, view: View) {
    val inputMethodManager = context.getSystemService(InputMethodManager::class.java)
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}

/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.taskbar;

import com.android.launcher3.R;

/** Handles the Taskbar Education flow. */
public class TaskbarEduController {

    private final TaskbarActivityContext mActivity;
    private TaskbarEduView mTaskbarEduView;

    public TaskbarEduController(TaskbarActivityContext activity) {
        mActivity = activity;
    }

    void showEdu() {
        mActivity.setTaskbarWindowFullscreen(true);
        mActivity.getDragLayer().post(() -> {
            mTaskbarEduView = (TaskbarEduView) mActivity.getLayoutInflater().inflate(
                    R.layout.taskbar_edu, mActivity.getDragLayer(), false);
            mTaskbarEduView.addOnCloseListener(() -> {
                mTaskbarEduView = null;
            });
            mTaskbarEduView.show();
        });
    }

    void hideEdu() {
        if (mTaskbarEduView != null) {
            mTaskbarEduView.close(true /* animate */);
        }
    }
}

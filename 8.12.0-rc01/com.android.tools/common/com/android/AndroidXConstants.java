/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android;

import com.android.support.AndroidxName;

/**
 * Class containing all the {@link AndroidxName} constants. This is separate from {@link
 * SdkConstants} to avoid users of {@link SdkConstants} from preloading the migration map when not
 * used at all.
 *
 * The constants' values can be found in the migration file (see
 * `AndroidxMigrationParserKt#parseMigrationFile`). Instead of computing them at run time, we inline
 * the values directly here to improve performance (see b/234509793).
 */
public class AndroidXConstants {
    public static final AndroidxName CLASS_DATA_BINDING_COMPONENT = new AndroidxName("android.databinding.DataBindingComponent", "androidx.databinding.DataBindingComponent");
    public static final AndroidxName MULTI_DEX_APPLICATION = new AndroidxName("android.support.multidex.MultiDexApplication", "androidx.multidex.MultiDexApplication");

    /* Material Components */
    public static final AndroidxName CLASS_APP_BAR_LAYOUT = new AndroidxName("android.support.design.widget.AppBarLayout", "com.google.android.material.appbar.AppBarLayout");
    public static final AndroidxName APP_BAR_LAYOUT = CLASS_APP_BAR_LAYOUT;
    public static final AndroidxName CLASS_BOTTOM_NAVIGATION_VIEW = new AndroidxName("android.support.design.widget.BottomNavigationView", "com.google.android.material.bottomnavigation.BottomNavigationView");
    public static final AndroidxName BOTTOM_NAVIGATION_VIEW = CLASS_BOTTOM_NAVIGATION_VIEW;
    public static final AndroidxName CLASS_COORDINATOR_LAYOUT = new AndroidxName("android.support.design.widget.CoordinatorLayout", "androidx.coordinatorlayout.widget.CoordinatorLayout");

    /* Android Support Tag Constants */
    public static final AndroidxName COORDINATOR_LAYOUT = CLASS_COORDINATOR_LAYOUT;
    public static final AndroidxName CLASS_COLLAPSING_TOOLBAR_LAYOUT = new AndroidxName("android.support.design.widget.CollapsingToolbarLayout", "com.google.android.material.appbar.CollapsingToolbarLayout");
    public static final AndroidxName COLLAPSING_TOOLBAR_LAYOUT = CLASS_COLLAPSING_TOOLBAR_LAYOUT;
    public static final AndroidxName CLASS_FLOATING_ACTION_BUTTON = new AndroidxName("android.support.design.widget.FloatingActionButton", "com.google.android.material.floatingactionbutton.FloatingActionButton");

    public static final AndroidxName FLOATING_ACTION_BUTTON = CLASS_FLOATING_ACTION_BUTTON;
    public static final AndroidxName CLASS_NAVIGATION_VIEW = new AndroidxName("android.support.design.widget.NavigationView", "com.google.android.material.navigation.NavigationView");
    public static final AndroidxName NAVIGATION_VIEW = CLASS_NAVIGATION_VIEW;
    public static final AndroidxName CLASS_SNACKBAR = new AndroidxName("android.support.design.widget.Snackbar", "com.google.android.material.snackbar.Snackbar");
    public static final AndroidxName SNACKBAR = CLASS_SNACKBAR;
    public static final AndroidxName CLASS_TAB_LAYOUT = new AndroidxName("android.support.design.widget.TabLayout", "com.google.android.material.tabs.TabLayout");
    public static final AndroidxName TAB_LAYOUT = CLASS_TAB_LAYOUT;
    public static final AndroidxName CLASS_TAB_ITEM = new AndroidxName("android.support.design.widget.TabItem", "com.google.android.material.tabs.TabItem");
    public static final AndroidxName TAB_ITEM = CLASS_TAB_ITEM;
    public static final AndroidxName CLASS_TEXT_INPUT_LAYOUT = new AndroidxName("android.support.design.widget.TextInputLayout", "com.google.android.material.textfield.TextInputLayout");
    public static final AndroidxName TEXT_INPUT_LAYOUT = CLASS_TEXT_INPUT_LAYOUT;
    public static final AndroidxName CLASS_TEXT_INPUT_EDIT_TEXT = new AndroidxName("android.support.design.widget.TextInputEditText", "com.google.android.material.textfield.TextInputEditText");
    public static final AndroidxName TEXT_INPUT_EDIT_TEXT = CLASS_TEXT_INPUT_EDIT_TEXT;

    /* Android ConstraintLayout Constants */
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT = new AndroidxName("android.support.constraint.ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout");

    public static final AndroidxName CONSTRAINT_LAYOUT = CLASS_CONSTRAINT_LAYOUT;
    public static final AndroidxName CLASS_MOTION_LAYOUT = new AndroidxName("android.support.constraint.motion.MotionLayout", "androidx.constraintlayout.motion.widget.MotionLayout");
    public static final AndroidxName MOTION_LAYOUT = CLASS_MOTION_LAYOUT;
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_HELPER = new AndroidxName("android.support.constraint.ConstraintHelper", "androidx.constraintlayout.widget.ConstraintHelper");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_BARRIER = new AndroidxName("android.support.constraint.Barrier", "androidx.constraintlayout.widget.Barrier");
    public static final AndroidxName CONSTRAINT_LAYOUT_BARRIER = CLASS_CONSTRAINT_LAYOUT_BARRIER;
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_GROUP = new AndroidxName("android.support.constraint.Group", "androidx.constraintlayout.widget.Group");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_CHAIN = new AndroidxName("android.support.constraint.Chain", "androidx.constraintlayout.Chain");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_LAYER = new AndroidxName("android.support.constraint.helper.Layer", "androidx.constraintlayout.helper.widget.Layer");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_FLOW = new AndroidxName("android.support.constraint.helper.Flow", "androidx.constraintlayout.helper.widget.Flow");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_CONSTRAINTS = new AndroidxName("android.support.constraint.Constraints", "androidx.constraintlayout.Constraints");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_REFERENCE = new AndroidxName("android.support.constraint.Reference", "androidx.constraintlayout.Reference");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_PARAMS = new AndroidxName("android.support.constraint.ConstraintLayout$LayoutParams", "androidx.constraintlayout.widget.ConstraintLayout$LayoutParams");
    public static final AndroidxName CLASS_TABLE_CONSTRAINT_LAYOUT = new AndroidxName("android.support.constraint.TableConstraintLayout", "androidx.constraintlayout.TableConstraintLayout");
    public static final AndroidxName TABLE_CONSTRAINT_LAYOUT = CLASS_TABLE_CONSTRAINT_LAYOUT;
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_GUIDELINE = new AndroidxName("android.support.constraint.Guideline", "androidx.constraintlayout.widget.Guideline");
    public static final AndroidxName CONSTRAINT_LAYOUT_GUIDELINE = CLASS_CONSTRAINT_LAYOUT_GUIDELINE;
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_MOCK_VIEW = new AndroidxName("android.support.constraint.utils.MockView", "androidx.constraintlayout.utils.widget.MockView");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_IMAGE_FILTER_VIEW = new AndroidxName("android.support.constraint.utils.ImageFilterView", "androidx.constraintlayout.utils.widget.ImageFilterView");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_IMAGE_FILTER_BUTTON = new AndroidxName("android.support.constraint.utils.ImageFilterButton", "androidx.constraintlayout.utils.widget.ImageFilterButton");

    public static final AndroidxName CLASS_NESTED_SCROLL_VIEW = new AndroidxName("android.support.v4.widget.NestedScrollView", "androidx.core.widget.NestedScrollView");
    public static final AndroidxName NESTED_SCROLL_VIEW = CLASS_NESTED_SCROLL_VIEW;
    public static final AndroidxName CLASS_DRAWER_LAYOUT = new AndroidxName("android.support.v4.widget.DrawerLayout", "androidx.drawerlayout.widget.DrawerLayout");
    public static final AndroidxName DRAWER_LAYOUT = CLASS_DRAWER_LAYOUT;
    public static final AndroidxName CLASS_GRID_LAYOUT_V7 = new AndroidxName("android.support.v7.widget.GridLayout", "androidx.gridlayout.widget.GridLayout");
    public static final AndroidxName GRID_LAYOUT_V7 = CLASS_GRID_LAYOUT_V7;
    public static final AndroidxName CLASS_TOOLBAR_V7 = new AndroidxName("android.support.v7.widget.Toolbar", "androidx.appcompat.widget.Toolbar");
    public static final AndroidxName TOOLBAR_V7 = CLASS_TOOLBAR_V7;
    public static final AndroidxName CLASS_RECYCLER_VIEW_V7 = new AndroidxName("android.support.v7.widget.RecyclerView", "androidx.recyclerview.widget.RecyclerView");
    public static final AndroidxName RECYCLER_VIEW = CLASS_RECYCLER_VIEW_V7;
    public static final AndroidxName CLASS_CARD_VIEW = new AndroidxName("android.support.v7.widget.CardView", "androidx.cardview.widget.CardView");
    public static final AndroidxName CARD_VIEW = CLASS_CARD_VIEW;
    public static final AndroidxName CLASS_ACTION_MENU_VIEW = new AndroidxName("android.support.v7.widget.ActionMenuView", "androidx.appcompat.widget.ActionMenuView");
    public static final AndroidxName ACTION_MENU_VIEW = CLASS_ACTION_MENU_VIEW;
    public static final AndroidxName CLASS_BROWSE_FRAGMENT = new AndroidxName("android.support.v17.leanback.app.BrowseFragment", "androidx.leanback.app.BrowseFragment");
    public static final AndroidxName BROWSE_FRAGMENT = CLASS_BROWSE_FRAGMENT;
    public static final AndroidxName CLASS_DETAILS_FRAGMENT = new AndroidxName("android.support.v17.leanback.app.DetailsFragment", "androidx.leanback.app.DetailsFragment");
    public static final AndroidxName DETAILS_FRAGMENT = CLASS_DETAILS_FRAGMENT;
    public static final AndroidxName CLASS_PLAYBACK_OVERLAY_FRAGMENT = new AndroidxName("android.support.v17.leanback.app.PlaybackOverlayFragment", "androidx.leanback.app.PlaybackOverlayFragment");
    public static final AndroidxName PLAYBACK_OVERLAY_FRAGMENT = CLASS_PLAYBACK_OVERLAY_FRAGMENT;
    public static final AndroidxName CLASS_SEARCH_FRAGMENT = new AndroidxName("android.support.v17.leanback.app.SearchFragment", "androidx.leanback.app.SearchFragment");
    public static final AndroidxName SEARCH_FRAGMENT = CLASS_SEARCH_FRAGMENT;
    public static final AndroidxName FQCN_GRID_LAYOUT_V7 = new AndroidxName("android.support.v7.widget.GridLayout", "androidx.gridlayout.widget.GridLayout");

    // Annotations
    public static final AndroidxName SUPPORT_ANNOTATIONS_PREFIX = new AndroidxName("android.support.annotation.", "androidx.annotation.");
    public static final AndroidxName STRING_DEF_ANNOTATION = new AndroidxName("android.support.annotation.StringDef", "androidx.annotation.StringDef");
    public static final AndroidxName LONG_DEF_ANNOTATION = new AndroidxName("android.support.annotation.LongDef", "androidx.annotation.LongDef");
    public static final AndroidxName INT_DEF_ANNOTATION = new AndroidxName("android.support.annotation.IntDef", "androidx.annotation.IntDef");
    public static final AndroidxName DATA_BINDING_PKG = new AndroidxName("android.databinding.", "androidx.databinding.");
    public static final AndroidxName CLASS_DATA_BINDING_BASE_BINDING = new AndroidxName("android.databinding.ViewDataBinding", "androidx.databinding.ViewDataBinding");
    public static final AndroidxName CLASS_DATA_BINDING_BINDABLE = new AndroidxName("android.databinding.Bindable", "androidx.databinding.Bindable");
    public static final AndroidxName CLASS_DATA_BINDING_VIEW_STUB_PROXY = new AndroidxName("android.databinding.ViewStubProxy", "androidx.databinding.ViewStubProxy");
    public static final AndroidxName BINDING_ADAPTER_ANNOTATION = new AndroidxName("android.databinding.BindingAdapter", "androidx.databinding.BindingAdapter");
    public static final AndroidxName BINDING_CONVERSION_ANNOTATION = new AndroidxName("android.databinding.BindingConversion", "androidx.databinding.BindingConversion");
    public static final AndroidxName BINDING_METHODS_ANNOTATION = new AndroidxName("android.databinding.BindingMethods", "androidx.databinding.BindingMethods");
    public static final AndroidxName INVERSE_BINDING_ADAPTER_ANNOTATION = new AndroidxName("android.databinding.InverseBindingAdapter", "androidx.databinding.InverseBindingAdapter");
    public static final AndroidxName INVERSE_BINDING_METHOD_ANNOTATION = new AndroidxName("android.databinding.InverseBindingMethod", "androidx.databinding.InverseBindingMethod");
    public static final AndroidxName INVERSE_BINDING_METHODS_ANNOTATION = new AndroidxName("android.databinding.InverseBindingMethods", "androidx.databinding.InverseBindingMethods");
    public static final AndroidxName INVERSE_METHOD_ANNOTATION = new AndroidxName("android.databinding.InverseMethod", "androidx.databinding.InverseMethod");

    public static final AndroidxName CLASS_LIVE_DATA = new AndroidxName("android.arch.lifecycle.LiveData", "androidx.lifecycle.LiveData");
    public static final AndroidxName CLASS_OBSERVABLE_BOOLEAN = new AndroidxName("android.databinding.ObservableBoolean", "androidx.databinding.ObservableBoolean");
    public static final AndroidxName CLASS_OBSERVABLE_BYTE = new AndroidxName("android.databinding.ObservableByte", "androidx.databinding.ObservableByte");
    public static final AndroidxName CLASS_OBSERVABLE_CHAR = new AndroidxName("android.databinding.ObservableChar", "androidx.databinding.ObservableChar");
    public static final AndroidxName CLASS_OBSERVABLE_SHORT = new AndroidxName("android.databinding.ObservableShort", "androidx.databinding.ObservableShort");
    public static final AndroidxName CLASS_OBSERVABLE_INT = new AndroidxName("android.databinding.ObservableInt", "androidx.databinding.ObservableInt");
    public static final AndroidxName CLASS_OBSERVABLE_LONG = new AndroidxName("android.databinding.ObservableLong", "androidx.databinding.ObservableLong");
    public static final AndroidxName CLASS_OBSERVABLE_FLOAT = new AndroidxName("android.databinding.ObservableFloat", "androidx.databinding.ObservableFloat");
    public static final AndroidxName CLASS_OBSERVABLE_DOUBLE = new AndroidxName("android.databinding.ObservableDouble", "androidx.databinding.ObservableDouble");
    public static final AndroidxName CLASS_OBSERVABLE_FIELD = new AndroidxName("android.databinding.ObservableField", "androidx.databinding.ObservableField");
    public static final AndroidxName CLASS_OBSERVABLE_PARCELABLE = new AndroidxName("android.databinding.ObservableParcelable", "androidx.databinding.ObservableParcelable");
    public static final AndroidxName CLASS_RECYCLER_VIEW_LAYOUT_MANAGER = new AndroidxName("android.support.v7.widget.RecyclerView$LayoutManager", "androidx.recyclerview.widget.RecyclerView$LayoutManager");
    public static final AndroidxName CLASS_RECYCLER_VIEW_VIEW_HOLDER = new AndroidxName("android.support.v7.widget.RecyclerView$ViewHolder", "androidx.recyclerview.widget.RecyclerView$ViewHolder");
    public static final AndroidxName CLASS_VIEW_PAGER = new AndroidxName("android.support.v4.view.ViewPager", "androidx.viewpager.widget.ViewPager");
    public static final AndroidxName VIEW_PAGER = CLASS_VIEW_PAGER;
    public static final AndroidxName CLASS_V4_FRAGMENT = new AndroidxName("android.support.v4.app.Fragment", "androidx.fragment.app.Fragment");

    /* Android Support Class Constants */
    public static final AndroidxName CLASS_APP_COMPAT_ACTIVITY = new AndroidxName("android.support.v7.app.AppCompatActivity", "androidx.appcompat.app.AppCompatActivity");
    public static final AndroidxName CLASS_MEDIA_ROUTE_ACTION_PROVIDER = new AndroidxName("android.support.v7.app.MediaRouteActionProvider", "androidx.mediarouter.app.MediaRouteActionProvider");
    public static final AndroidxName CLASS_RECYCLER_VIEW_ADAPTER = new AndroidxName("android.support.v7.widget.RecyclerView$Adapter", "androidx.recyclerview.widget.RecyclerView$Adapter");

    public static final AndroidxName SERVICES_APK_PACKAGE = new AndroidxName("android.support.test.services.", "androidx.test.services.");
    public static final AndroidxName SHELL_MAIN_CLASS = new AndroidxName("android.support.test.services.shellexecutor.ShellMain", "androidx.test.services.shellexecutor.ShellMain");
    public static final AndroidxName ORCHESTRATOR_PACKAGE = new AndroidxName("android.support.test.orchestrator.", "androidx.test.orchestrator.");
    public static final AndroidxName ORCHESTRATOR_CLASS = new AndroidxName("android.support.test.orchestrator.AndroidTestOrchestrator", "androidx.test.orchestrator.AndroidTestOrchestrator");

    public static final class PreferenceAndroidX {
        public static final AndroidxName CLASS_PREFERENCE_ANDROIDX = new AndroidxName("android.support.v7.preference.Preference", "androidx.preference.Preference");
        public static final AndroidxName CLASS_PREFERENCE_GROUP_ANDROIDX = new AndroidxName("android.support.v7.preference.PreferenceGroup", "androidx.preference.PreferenceGroup");
        public static final AndroidxName CLASS_EDIT_TEXT_PREFERENCE_ANDROIDX = new AndroidxName("android.support.v7.preference.EditTextPreference", "androidx.preference.EditTextPreference");
        public static final AndroidxName CLASS_LIST_PREFERENCE_ANDROIDX = new AndroidxName("android.support.v7.preference.ListPreference", "androidx.preference.ListPreference");
        public static final AndroidxName CLASS_MULTI_CHECK_PREFERENCE_ANDROIDX = new AndroidxName("android.support.v7.preference.MultiCheckPreference", "androidx.preference.MultiCheckPreference");
        public static final AndroidxName CLASS_MULTI_SELECT_LIST_PREFERENCE_ANDROIDX = new AndroidxName("android.support.v7.preference.MultiSelectListPreference", "androidx.preference.MultiSelectListPreference");
        public static final AndroidxName CLASS_PREFERENCE_SCREEN_ANDROIDX = new AndroidxName("android.support.v7.preference.PreferenceScreen", "androidx.preference.PreferenceScreen");
        public static final AndroidxName CLASS_RINGTONE_PREFERENCE_ANDROIDX = new AndroidxName("android.support.v7.preference.RingtonePreference", "androidx.preference.RingtonePreference");
        public static final AndroidxName CLASS_SEEK_BAR_PREFERENCE_ANDROIDX = new AndroidxName("android.support.v7.preference.SeekBarPreference", "androidx.preference.SeekBarPreference");
        public static final AndroidxName CLASS_TWO_STATE_PREFERENCE_ANDROIDX = new AndroidxName("android.support.v7.preference.TwoStatePreference", "androidx.preference.TwoStatePreference");
    }
}

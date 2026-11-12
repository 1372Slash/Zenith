package com.etrisad.zenith.data.model

import com.etrisad.zenith.data.preferences.UserPreferences

class CategoryCatalog(
    val categories: List<AppCategory>,
    private val assignments: Map<String, String>
) {
    constructor(preferences: UserPreferences) : this(
        AppCategory.assignable.map { preferences.defaultCategoryOverrides[it.id] ?: it } +
            preferences.customCategories + AppCategory.UNCATEGORIZED,
        preferences.appCategories
    )

    private val byId = categories.associateBy { it.id }

    fun get(id: String?): AppCategory = byId[id] ?: AppCategory.UNCATEGORIZED

    fun resolve(packageName: String, appName: String? = null): AppCategory {
        assignments[packageName]?.let { id -> byId[id]?.let { return it } }
        val suggested = AppCategoryDefaults.forPackage(packageName, appName)
            ?: return AppCategory.UNCATEGORIZED
        return byId[suggested.id] ?: suggested
    }

    fun isSuggested(packageName: String, appName: String? = null): Boolean =
        !assignments.containsKey(packageName) &&
            AppCategoryDefaults.forPackage(packageName, appName) != null
}

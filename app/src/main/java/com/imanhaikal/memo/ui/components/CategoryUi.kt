package com.imanhaikal.memo.ui.components

import androidx.annotation.DrawableRes
import com.imanhaikal.memo.R
import com.imanhaikal.memo.data.Category

/** UI-side icon mapping so the data layer stays free of Android resources. */
@get:DrawableRes
val Category.iconRes: Int
    get() = when (this) {
        Category.FOOD -> R.drawable.ic_cat_food
        Category.TRANSPORT -> R.drawable.ic_cat_transport
        Category.SHOPPING -> R.drawable.ic_cat_shopping
        Category.BILLS -> R.drawable.ic_cat_bills
        Category.ENTERTAINMENT -> R.drawable.ic_cat_entertainment
        Category.HEALTH -> R.drawable.ic_cat_health
        Category.OTHER -> R.drawable.ic_cat_other
    }

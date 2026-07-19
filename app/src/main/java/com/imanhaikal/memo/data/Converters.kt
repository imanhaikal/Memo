package com.imanhaikal.memo.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun categoryToId(category: Category?): String? = category?.id

    @TypeConverter
    fun idToCategory(id: String?): Category? = Category.fromId(id)
}

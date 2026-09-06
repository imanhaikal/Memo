package com.imanhaikal.memo.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun categoryToId(category: Category?): String? = category?.id

    @TypeConverter
    fun idToCategory(id: String?): Category? = Category.fromId(id)

    @TypeConverter
    fun transactionTypeToId(type: TransactionType): String = type.id

    @TypeConverter
    fun idToTransactionType(id: String?): TransactionType = TransactionType.fromId(id)

    @TypeConverter
    fun cadenceToId(cadence: Cadence): String = cadence.id

    @TypeConverter
    fun idToCadence(id: String?): Cadence = Cadence.fromId(id)
}

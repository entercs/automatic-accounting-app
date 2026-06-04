package com.financetracker.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        // 注意:categoryId 不再设外键。转账(TRANSFER)没有语义上的分类,
        // 写 -1L 作为占位即可,无需与 categories 表保持引用完整性。
        ForeignKey(
            entity = PaymentAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("categoryId"),
        Index("accountId"),
        Index("date"),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val type: String, // "INCOME", "EXPENSE", or "TRANSFER"
    val categoryId: Long,
    val accountId: Long,
    val transferToAccountId: Long? = null, // non-null when type=TRANSFER
    val merchant: String = "",
    val note: String = "",
    val reimbursable: Boolean = false,
    val date: Long, // epoch millis
    val source: String = "manual",
)

package com.financetracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.financetracker.data.db.dao.CategoryDao
import com.financetracker.data.db.dao.ParsingRuleDao
import com.financetracker.data.db.dao.PaymentAccountDao
import com.financetracker.data.db.dao.TransactionDao
import com.financetracker.data.db.entity.CategoryEntity
import com.financetracker.data.db.entity.ParsingRuleEntity
import com.financetracker.data.db.entity.PaymentAccountEntity
import com.financetracker.data.db.entity.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        PaymentAccountEntity::class,
        ParsingRuleEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : androidx.room.RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun paymentAccountDao(): PaymentAccountDao
    abstract fun parsingRuleDao(): ParsingRuleDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE payment_accounts ADD COLUMN color TEXT NOT NULL DEFAULT '#4CAF50'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE payment_accounts ADD COLUMN balance REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN transferToAccountId INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN reimbursable INTEGER NOT NULL DEFAULT 0")
            }
        }

        // 修复 Bug2: 移除 transactions.categoryId 的外键约束
        // (转账没有语义上的分类,categoryId=-1L 时旧 FK 会导致保存崩溃)
        // SQLite 不支持 DROP CONSTRAINT,采用标准"重建表"模式
        // 注意: id 列必须显式写 NOT NULL — Room 代码生成器输出的是
        // `id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT`,验证时会比对 PRAGMA
        // table_info 的 notnull 字段,而 SQLite 对 `INTEGER PRIMARY KEY`(无显式 NOT NULL)
        // 在 PRAGMA 中报告 notnull=0,会导致迁移验证失败。
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1) 创建新表(不挂 categoryId 的外键)
                db.execSQL("""
                    CREATE TABLE transactions_new (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        amount REAL NOT NULL,
                        type TEXT NOT NULL,
                        categoryId INTEGER NOT NULL,
                        accountId INTEGER NOT NULL,
                        transferToAccountId INTEGER,
                        merchant TEXT NOT NULL,
                        note TEXT NOT NULL,
                        reimbursable INTEGER NOT NULL,
                        date INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        FOREIGN KEY (accountId) REFERENCES payment_accounts(id) ON DELETE RESTRICT
                    )
                """.trimIndent())
                // 2) 复制数据
                db.execSQL("""
                    INSERT INTO transactions_new
                        (id, amount, type, categoryId, accountId, transferToAccountId,
                         merchant, note, reimbursable, date, source)
                    SELECT id, amount, type, categoryId, accountId, transferToAccountId,
                           merchant, note, reimbursable, date, source
                    FROM transactions
                """.trimIndent())
                // 3) 删旧表,改新表名
                db.execSQL("DROP TABLE transactions")
                db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
                // 4) 重建索引
                db.execSQL("CREATE INDEX index_transactions_categoryId ON transactions(categoryId)")
                db.execSQL("CREATE INDEX index_transactions_accountId ON transactions(accountId)")
                db.execSQL("CREATE INDEX index_transactions_date ON transactions(date)")
            }
        }

        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, "finance_tracker.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .addCallback(SeedCallback())
                .build()
        }
    }

    private class SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            CoroutineScope(Dispatchers.IO).launch {
                // Categories and accounts are seeded via the repository layer
                // because we need the DAO instances, which require built database
            }
        }
    }
}

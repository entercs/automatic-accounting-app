package com.financetracker.data.repository

import com.financetracker.data.db.dao.PaymentAccountDao
import com.financetracker.data.db.entity.PaymentAccountEntity
import com.financetracker.domain.model.PaymentAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PaymentAccountRepository(private val dao: PaymentAccountDao) {

    fun getAll(): Flow<List<PaymentAccount>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    fun getEnabled(): Flow<List<PaymentAccount>> =
        dao.getEnabled().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): PaymentAccount? =
        dao.getById(id)?.toDomain()

    suspend fun getByType(type: String): PaymentAccount? =
        dao.getByType(type)?.toDomain()

    suspend fun count(): Int = dao.count()

    suspend fun updateBalance(id: Long, balance: Double) = dao.updateBalance(id, balance)

    suspend fun updateNameAndColor(id: Long, name: String, color: String) = dao.updateNameAndColor(id, name, color)

    suspend fun updateDetails(id: Long, name: String, color: String, balance: Double) = dao.updateDetails(id, name, color, balance)

    suspend fun create(name: String, type: String, color: String, balance: Double): Long =
        dao.insert(PaymentAccountEntity(name = name, type = type, color = color, balance = balance))

    suspend fun delete(id: Long) = dao.deleteById(id)

    // One-time: rename old default names
    suspend fun fixAccountNames() {
        val wechat = dao.getByType("wechat")
        if (wechat != null && wechat.name == "微信支付") {
            dao.updateNameAndColor(wechat.id, "微信", wechat.color)
        }
        val jd = dao.getByType("jd")
        if (jd != null && jd.name == "京东商城") {
            dao.updateNameAndColor(jd.id, "京东", jd.color)
        }
    }

    suspend fun seedIfEmpty() {
        if (dao.count() == 0) {
            listOf(
                PaymentAccountEntity(1, "微信", "wechat", true, "#07C160"),
                PaymentAccountEntity(2, "支付宝", "alipay", true, "#1677FF"),
                PaymentAccountEntity(3, "银行卡", "bank", true, "#4CAF50"),
            ).forEach { dao.insert(it) }
        }
    }

    suspend fun ensureBankAccount() {
        if (dao.getByType("bank") == null) {
            dao.insert(PaymentAccountEntity(name = "银行卡", type = "bank", color = "#4CAF50"))
        }
    }
}

private fun PaymentAccountEntity.toDomain() = PaymentAccount(id, name, type, isEnabled, color, balance)

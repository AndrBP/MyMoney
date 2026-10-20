package com.mymoney.app.data.repository

import com.mymoney.app.core.util.Ids
import com.mymoney.app.data.local.dao.AccountDao
import com.mymoney.app.data.local.dao.CategoryDao
import com.mymoney.app.data.local.dao.TransactionDao
import com.mymoney.app.data.local.entity.AccountEntity
import com.mymoney.app.data.local.entity.CategoryEntity
import com.mymoney.app.domain.model.Account
import com.mymoney.app.domain.model.AccountType
import com.mymoney.app.domain.model.Category
import com.mymoney.app.domain.model.CategoryKind
import com.mymoney.app.domain.model.Money
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun AccountEntity.toDomain() = Account(
    id = id, name = name, type = runCatching { AccountType.valueOf(type) }.getOrDefault(AccountType.OTHER),
    initialBalance = Money(initialBalanceCents), colorHex = colorHex, iconKey = iconKey,
    currencyCode = currencyCode, archived = archived, createdAtEpochDay = createdAtEpochDay, notes = notes,
)

fun Account.toEntity() = AccountEntity(
    id = id, name = name, type = type.name, initialBalanceCents = initialBalance.cents,
    colorHex = colorHex, iconKey = iconKey, currencyCode = currencyCode, archived = archived,
    createdAtEpochDay = createdAtEpochDay, notes = notes,
)

fun CategoryEntity.toDomain() = Category(
    id = id, name = name, kind = runCatching { CategoryKind.valueOf(kind) }.getOrDefault(CategoryKind.EXPENSE),
    colorHex = colorHex, iconKey = iconKey, isDefault = isDefault, archived = archived,
)

fun Category.toEntity() = CategoryEntity(
    id = id, name = name, kind = kind.name, colorHex = colorHex, iconKey = iconKey,
    isDefault = isDefault, archived = archived,
)

/** Default categories seeded on first run (rule 5.4.15). Stable ids keep backups consistent. */
object DefaultCategories {
    const val FALLBACK_EXPENSE_ID = "cat_default_outros"
    const val FALLBACK_INCOME_ID = "cat_default_outras_receitas"

    fun seed(): List<CategoryEntity> {
        val expense = listOf(
            "Moradia", "Alimentação", "Mercado", "Transporte", "Saúde", "Educação",
            "Lazer", "Assinaturas", "Dívidas", "Impostos", "Presentes", "Outros",
        )
        val income = listOf("Salário", "Freelance", "Negócios", "Investimentos", "Reembolso", "Outras receitas")
        val palette = listOf(
            "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3", "#009688",
            "#4CAF50", "#8BC34A", "#FF9800", "#FF5722", "#795548", "#607D8B",
        )
        val out = ArrayList<CategoryEntity>()
        expense.forEachIndexed { i, name ->
            out.add(
                CategoryEntity(
                    id = "cat_default_" + name.lowercase().normalize(),
                    name = name, kind = CategoryKind.EXPENSE.name,
                    colorHex = palette[i % palette.size], iconKey = "tag",
                    isDefault = true, archived = false,
                )
            )
        }
        income.forEachIndexed { i, name ->
            out.add(
                CategoryEntity(
                    id = "cat_default_" + name.lowercase().normalize(),
                    name = name, kind = CategoryKind.INCOME.name,
                    colorHex = palette[(i + 3) % palette.size], iconKey = "trending_up",
                    isDefault = true, archived = false,
                )
            )
        }
        return out
    }

    private fun String.normalize(): String =
        java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]+"), "_").trim('_')
}

@Singleton
class AccountRepository @Inject constructor(private val dao: AccountDao) {
    fun observeAll(): Flow<List<Account>> = dao.observeAll().map { it.map(AccountEntity::toDomain) }
    fun observeActive(): Flow<List<Account>> = dao.observeActive().map { it.map(AccountEntity::toDomain) }
    suspend fun getById(id: String): Account? = dao.getById(id)?.toDomain()
    suspend fun getAll(): List<Account> = dao.getAll().map(AccountEntity::toDomain)

    suspend fun save(account: Account): Account {
        val entity = if (account.id.isEmpty()) account.copy(id = Ids.new(), createdAtEpochDay = LocalDate.now().toEpochDay()) else account
        dao.upsert(entity.toEntity())
        return entity
    }

    suspend fun transactionCount(id: String): Int = dao.transactionCount(id)

    /** Deletes an account; also deletes its transactions when requested (rule 5.3.15). */
    suspend fun delete(id: String, cascadeTransactions: Boolean): Boolean {
        val acc = dao.getById(id) ?: return false
        if (dao.transactionCount(id) > 0 && !cascadeTransactions) return false
        if (cascadeTransactions) dao.deleteTransactionsOf(id)
        dao.delete(acc)
        return true
    }
}

@Singleton
class CategoryRepository @Inject constructor(
    private val dao: CategoryDao,
    private val transactionDao: TransactionDao,
) {
    fun observeAll(): Flow<List<Category>> = dao.observeAll().map { it.map(CategoryEntity::toDomain) }
    fun observeActive(): Flow<List<Category>> = dao.observeActive().map { it.map(CategoryEntity::toDomain) }
    suspend fun getById(id: String): Category? = dao.getById(id)?.toDomain()
    suspend fun getAll(): List<Category> = dao.getAll().map(CategoryEntity::toDomain)

    suspend fun ensureDefaults() = dao.insertAll(DefaultCategories.seed())

    suspend fun save(category: Category): Category {
        val entity = if (category.id.isEmpty()) category.copy(id = Ids.new()) else category
        dao.upsert(entity.toEntity())
        return entity
    }

    /**
     * Deletes a category after reassigning its transactions/recurrences to
     * [reassignToCategoryId] (rule 5.4.14). Essential default fallback
     * categories cannot be deleted while they are the fallback (rule 5.4.13).
     */
    suspend fun delete(id: String, reassignToCategoryId: String?): Boolean {
        val cat = dao.getById(id) ?: return false
        if (id == DefaultCategories.FALLBACK_EXPENSE_ID || id == DefaultCategories.FALLBACK_INCOME_ID) return false
        if (reassignToCategoryId == null) {
            // If referenced by transactions and no replacement given → block.
            return false
        }
        dao.reassignTransactions(id, reassignToCategoryId)
        dao.reassignRecurrences(id, reassignToCategoryId)
        dao.clearBudgetRefs(id)
        dao.delete(cat)
        return true
    }

    suspend fun hasTransactions(id: String): Boolean = transactionDao.countByCategory(id) > 0
}

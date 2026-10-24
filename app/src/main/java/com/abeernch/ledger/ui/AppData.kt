package com.abeernch.ledger.ui

import com.abeernch.ledger.category.Categorizer
import com.abeernch.ledger.data.Account
import com.abeernch.ledger.data.Budget
import com.abeernch.ledger.data.Debt
import com.abeernch.ledger.data.Fund
import com.abeernch.ledger.data.Rule
import com.abeernch.ledger.data.Txn
import com.abeernch.ledger.data.Unparsed

/** Everything the screens show, loaded once in MainActivity and passed down. */
data class AppData(
    val txns: List<Txn> = emptyList(),          // newest first
    val accounts: List<Account> = emptyList(),
    val funds: List<Fund> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val rules: List<Rule> = emptyList(),
    val unparsed: List<Unparsed> = emptyList()
) {
    private val accountById = accounts.associateBy { it.id }
    private val fundById = funds.associateBy { it.id }

    fun account(id: Long?): Account? = id?.let { accountById[it] }
    fun fund(id: Long?): Fund? = id?.let { fundById[it] }

    /** The name to show for a transaction's account. */
    fun accountName(t: Txn): String = account(t.accountId)?.name ?: t.bank

    /** Built-in categories plus any the user made. */
    val categories: List<String>
        get() = (Categorizer.builtIn + rules.map { it.category } + budgets.map { it.category } + txns.map { it.category })
            .distinct()

    fun children(parent: Txn): List<Txn> = txns.filter { it.splitParentId == parent.id }
}

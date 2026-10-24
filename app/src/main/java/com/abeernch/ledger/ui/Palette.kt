package com.abeernch.ledger.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.abeernch.ledger.R
import com.abeernch.ledger.category.Categorizer

/** Colours and icons for accounts, funds and categories. Mid-tones, readable on light and dark. */
object Palette {

    /** Colours offered for accounts and funds (ARGB). */
    val swatches: List<Long> = listOf(
        0xFF0F766E, 0xFF2563EB, 0xFF7C3AED, 0xFFDB2777,
        0xFFEA580C, 0xFFCA8A04, 0xFF16A34A, 0xFF0891B2,
        0xFF4F46E5, 0xFF64748B
    )

    /** A stable colour for a name, so the same bank always gets the same colour. */
    fun colorFor(name: String): Long {
        known[name.lowercase()]?.let { return it }
        return swatches[(name.lowercase().hashCode() and 0x7fffffff) % swatches.size]
    }

    private val known = mapOf(
        "askari bank" to 0xFF2563EB,
        "meezan bank" to 0xFF7C3AED,
        "easypaisa" to 0xFF16A34A,
        "jazzcash" to 0xFFDC2626,
        "cash" to 0xFF64748B
    )

    data class CategoryStyle(@DrawableRes val icon: Int, val color: Color)

    private val categoryStyles = mapOf(
        Categorizer.FOOD to CategoryStyle(R.drawable.ic_food, Color(0xFFEA580C)),
        Categorizer.GROCERY to CategoryStyle(R.drawable.ic_grocery, Color(0xFF0D9488)),
        Categorizer.HEALTH to CategoryStyle(R.drawable.ic_health, Color(0xFF16A34A)),
        Categorizer.FUEL to CategoryStyle(R.drawable.ic_fuel, Color(0xFFCA8A04)),
        Categorizer.TRANSPORT to CategoryStyle(R.drawable.ic_transport, Color(0xFF2563EB)),
        Categorizer.SHOPPING to CategoryStyle(R.drawable.ic_shopping, Color(0xFFDB2777)),
        Categorizer.BILLS to CategoryStyle(R.drawable.ic_bills, Color(0xFF7C3AED)),
        Categorizer.SUBSCRIPTIONS to CategoryStyle(R.drawable.ic_subscription, Color(0xFF0891B2)),
        Categorizer.TRANSFERS to CategoryStyle(R.drawable.ic_transfer, Color(0xFF4F46E5)),
        Categorizer.CASH to CategoryStyle(R.drawable.ic_cash, Color(0xFF65A30D)),
        Categorizer.INCOME to CategoryStyle(R.drawable.ic_income, Color(0xFF15803D)),
        Categorizer.OTHER to CategoryStyle(R.drawable.ic_other, Color(0xFF6B7280))
    )

    /** Custom categories get the generic icon and a colour from their name. */
    fun category(name: String): CategoryStyle =
        categoryStyles[name] ?: CategoryStyle(R.drawable.ic_other, Color(colorFor(name)))
}

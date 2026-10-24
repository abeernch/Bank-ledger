package com.abeernch.ledger.category

import com.abeernch.ledger.parse.Channel
import com.abeernch.ledger.parse.Direction

object Categorizer {

    const val FOOD = "Food & Dining"
    const val GROCERY = "Groceries"
    const val HEALTH = "Health"
    const val FUEL = "Fuel"
    const val TRANSPORT = "Transport"
    const val SHOPPING = "Shopping"
    const val BILLS = "Bills & Utilities"
    const val SUBSCRIPTIONS = "Subscriptions"
    const val TRANSFERS = "Transfers"
    const val CASH = "Cash"
    const val INCOME = "Income"
    const val OTHER = "Other"

    val builtIn = listOf(FOOD, GROCERY, HEALTH, FUEL, TRANSPORT, SHOPPING, BILLS, SUBSCRIPTIONS, TRANSFERS, CASH, INCOME, OTHER)

    /** Seeded once on first launch. The user can delete or add rules in Settings. */
    val defaultRules: List<Pair<String, String>> = listOf(
        FOOD to listOf("JUICE", "CAFE", "COFFEE", "RESTAURANT", "PIZZA", "BURGER", "KFC", "MCDONALD", "FOODPANDA",
            "BAKERY", "BBQ", "TIKKA", "DHABA", "SWEETS", "CHAI", "KITCHEN", "GRILL", "SHAWARMA", "HARDEES", "SUBWAY"),
        GROCERY to listOf("MART", "SUPERMARKET", "GROCERY", "CARREFOUR", "IMTIAZ", "METRO CASH", "CHASE UP",
            "AL-FATAH", "AL FATAH", "GREEN VALLEY", "SAVEMART", "D WATSON", "GENERAL STORE"),
        HEALTH to listOf("PHARMACY", "PHARMA", "MEDICAL", "HOSPITAL", "CLINIC", "LAB", "CHEMIST", "MEDICOS", "DENTAL"),
        FUEL to listOf("PSO", "SHELL", "TOTAL PARCO", "ATTOCK", "FILLING", "PETROL", "HASCOL", "BYCO", "GO PETROL", "FUEL"),
        TRANSPORT to listOf("CAREEM", "UBER", "INDRIVE", "YANGO", "BYKEA", "MOTORWAY", "M-TAG", "MTAG", "PARKING"),
        SHOPPING to listOf("DARAZ", "KHAADI", "OUTFITTERS", "SAPPHIRE", "GUL AHMED", "MALL", "AMAZON", "TEMU",
            "ALIEXPRESS", "BONANZA", "ALKARAM", "LIMELIGHT", "ENGINE", "BREAKOUT", "INTERWOOD"),
        BILLS to listOf("IESCO", "LESCO", "FESCO", "SNGPL", "SSGC", "K-ELECTRIC", "PTCL", "NAYATEL", "STORMFIBER",
            "JAZZ", "ZONG", "TELENOR", "UFONE", "BILL", "WAPDA", "WATER"),
        SUBSCRIPTIONS to listOf("NETFLIX", "SPOTIFY", "YOUTUBE", "GOOGLE", "APPLE.COM", "ICLOUD", "OPENAI",
            "ANTHROPIC", "CLAUDE", "MICROSOFT", "ADOBE", "AMAZON PRIME")
    ).flatMap { (cat, words) -> words.map { it to cat } }

    /**
     * [rules] is (keyword, category), user rules first.
     * The first keyword found in the counterparty wins.
     */
    fun categorize(
        counterparty: String?,
        channel: Channel,
        direction: Direction,
        rules: List<Pair<String, String>>
    ): String {
        val name = counterparty?.uppercase()
        if (name != null) {
            for ((keyword, category) in rules) {
                if (matches(name, keyword.uppercase().trim())) return category
            }
        }
        return fallback(channel, direction)
    }

    /**
     * Long keywords match anywhere ("MART" is 4 letters, so "SUPERMART" needs its own rule).
     * Short keywords (4 letters or less) must start a word, so "PSO" does not match "EPSOM".
     */
    private fun matches(name: String, keyword: String): Boolean {
        if (keyword.isEmpty()) return false
        if (keyword.length > 4) return name.contains(keyword)
        var i = name.indexOf(keyword)
        while (i >= 0) {
            if (i == 0 || !name[i - 1].isLetterOrDigit()) return true
            i = name.indexOf(keyword, i + 1)
        }
        return false
    }

    private fun fallback(channel: Channel, direction: Direction): String {
        return when {
            direction == Direction.CREDIT -> INCOME
            channel == Channel.ATM -> CASH
            channel == Channel.TRANSFER -> TRANSFERS
            channel == Channel.BILL -> BILLS
            else -> OTHER
        }
    }
}

package com.abeernch.ledger.category

import com.abeernch.ledger.parse.Channel
import com.abeernch.ledger.parse.Direction
import org.junit.Assert.assertEquals
import org.junit.Test

class CategorizerTest {

    private val rules = Categorizer.defaultRules

    @Test
    fun samplesGetSensibleCategories() {
        assertEquals(Categorizer.HEALTH, Categorizer.categorize("AL LATIF PHARMACY", Channel.CARD, Direction.DEBIT, rules))
        assertEquals(Categorizer.FOOD, Categorizer.categorize("JUICE LAND", Channel.CARD, Direction.DEBIT, rules))
        assertEquals(Categorizer.TRANSFERS, Categorizer.categorize("MUHAMMAD UMER NAEEM (MBL)", Channel.TRANSFER, Direction.DEBIT, rules))
    }

    @Test
    fun shortKeywordsMustStartAWord() {
        assertEquals(Categorizer.FUEL, Categorizer.categorize("PSO PUMP F-10", Channel.CARD, Direction.DEBIT, rules))
        assertEquals(Categorizer.OTHER, Categorizer.categorize("EPSOM SALT CO", Channel.CARD, Direction.DEBIT, rules))
    }

    @Test
    fun userRuleWins() {
        val mine = listOf("JUICE LAND" to "Treats") + rules
        assertEquals("Treats", Categorizer.categorize("JUICE LAND", Channel.CARD, Direction.DEBIT, mine))
    }

    @Test
    fun creditsDefaultToIncome() {
        assertEquals(Categorizer.INCOME, Categorizer.categorize("ALI KHAN", Channel.TRANSFER, Direction.CREDIT, rules))
    }
}

package com.abeernch.ledger.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Every real message format we support gets a test here.
 * Add the new sample first, watch it fail, then add the template.
 */
class ParserTest {

    private val zone = ZoneId.of("Asia/Karachi")
    private val now = LocalDateTime.of(2026, 10, 24, 10, 0).atZone(zone).toInstant().toEpochMilli()

    private fun parsed(text: String): ParsedTxn {
        val out = Parser.parse(text, now, zone)
        assertTrue("Expected Parsed but got $out", out is Outcome.Parsed)
        return (out as Outcome.Parsed).txn
    }

    private fun epoch(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun askariPos() {
        val t = parsed(
            "Dear Customer, you have performed a POS transaction of PKR. 2,291.00 from Account: " +
                "012303*****189 on AL LATIF PHARMACY, at 22:06:28 Dated: 23-OCT-26"
        )
        assertEquals("Askari Bank", t.bank)
        assertEquals(Direction.DEBIT, t.direction)
        assertEquals(Channel.CARD, t.channel)
        assertEquals(229100L, t.amountMinor)
        assertEquals("AL LATIF PHARMACY", t.counterparty)
        assertEquals("189", t.accountTail)
        assertEquals(epoch(2026, 10, 23, 22, 6, 28), t.timestamp)
        assertFalse(t.isGeneric)
    }

    @Test
    fun meezanSent() {
        val t = parsed(
            "PKR 4,000.00 sent to MUHAMMAD UMER NAEEM (MBL AC from your A/C xxx3319 of DHA-II BR ISD " +
                "on 15-Oct-2026 at 18:39 TID:094763"
        )
        assertEquals("Meezan Bank", t.bank)
        assertEquals(Direction.DEBIT, t.direction)
        assertEquals(Channel.TRANSFER, t.channel)
        assertEquals(400000L, t.amountMinor)
        assertEquals("MUHAMMAD UMER NAEEM (MBL)", t.counterparty)
        assertEquals("3319", t.accountTail)
        assertEquals("094763", t.reference)
        assertEquals(epoch(2026, 10, 15, 18, 39), t.timestamp)
    }

    @Test
    fun easypaisaCard() {
        val t = parsed(
            "Trx ID 57160923189: You have paid Rs. 230.00 via VISA Virtual Debit Card ***7705 at " +
                "JUICE LAND               ISLAMABAD    PK. Fee: Rs. 0.00."
        )
        assertEquals("Easypaisa", t.bank)
        assertEquals(Direction.DEBIT, t.direction)
        assertEquals(Channel.CARD, t.channel)
        assertEquals(23000L, t.amountMinor)
        assertEquals("JUICE LAND", t.counterparty)
        assertEquals("ISLAMABAD, PK", t.location)
        assertEquals("7705", t.accountTail)
        assertEquals(0L, t.feeMinor)
        assertEquals("57160923189", t.reference)
        assertEquals(now, t.timestamp)
    }

    @Test
    fun genericCredit() {
        val t = parsed("Your A/C ***4521 has been credited with PKR 15,000.00 from ALI KHAN via IBFT. Avl Bal PKR 52,300.50")
        assertEquals(null, t.bank)
        assertEquals(Direction.CREDIT, t.direction)
        assertEquals(Channel.TRANSFER, t.channel)
        assertEquals(1500000L, t.amountMinor)
        assertEquals("ALI KHAN", t.counterparty)
        assertEquals(5230050L, t.balanceMinor)
        assertTrue(t.isGeneric)
    }

    @Test
    fun otpIsIgnored() {
        val out = Parser.parse("Your OTP for payment of Rs. 2,000 at DARAZ is 482913. Do not share.", now, zone)
        assertEquals(Outcome.Ignore, out)
    }

    @Test
    fun promoIsIgnored() {
        val out = Parser.parse("Get 20% off up to Rs. 1,000 on your next order. Limited time offer!", now, zone)
        assertEquals(Outcome.Ignore, out)
    }

    @Test
    fun unknownMoneyMessageGoesToReview() {
        val out = Parser.parse("Your cheque of PKR 25,000 dated 20-10-2026 is under clearing.", now, zone)
        assertEquals(Outcome.NeedsReview, out)
    }
}

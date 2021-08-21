package com.example.solaredgenotification

import org.junit.Assert.*
import org.junit.Test

class MissedOrReachedUnitTest {
    @Test
    fun simple_missed_with_notification() {
        val calc = MainActivity();

        var list: MutableList<SolarEdgeResults> = mutableListOf()
        list.add(SolarEdgeResults(0, 0, false))
        list.add(SolarEdgeResults(20000, 20000, false))
        list.add(SolarEdgeResults(10000, 10000, false))

        val current: SolarEdgeResults = SolarEdgeResults(1000, 10000, false)

        assertEquals(calc.calculateMissedOrReached(current, list, 11000, true), Pair<String, Int>("SendFalse", 10000))
    }

    @Test
    fun simple_missed_no_notification() {
        val calc = MainActivity();

        var list: MutableList<SolarEdgeResults> = mutableListOf()
        list.add(SolarEdgeResults(0, 0, false))
        list.add(SolarEdgeResults(20000, 20000, false))
        list.add(SolarEdgeResults(10000, 10000, false))

        val current: SolarEdgeResults = SolarEdgeResults(1000, 10000, false)

        assertEquals(calc.calculateMissedOrReached(current, list, 11000, false), Pair<String, Int>("NotSend", 10000))
    }

    @Test
    fun simple_reached_with_notification() {
        val calc = MainActivity();

        var list: MutableList<SolarEdgeResults> = mutableListOf()
        list.add(SolarEdgeResults(0, 0, false))
        list.add(SolarEdgeResults(20000, 20000, false))
        list.add(SolarEdgeResults(10000, 10000, false))

        val current: SolarEdgeResults = SolarEdgeResults(1000, 10000, false)

        assertEquals(calc.calculateMissedOrReached(current, list, 9000, false), Pair<String, Int>("SendTrue", 10000))
    }

    @Test
    fun simple_reached_no_notification() {
        val calc = MainActivity();

        var list: MutableList<SolarEdgeResults> = mutableListOf()
        list.add(SolarEdgeResults(0, 0, false))
        list.add(SolarEdgeResults(20000, 20000, false))
        list.add(SolarEdgeResults(10000, 10000, false))

        val current: SolarEdgeResults = SolarEdgeResults(1000, 10000, false)

        assertEquals(calc.calculateMissedOrReached(current, list, 9000, true), Pair<String, Int>("NotSend", 10000))
    }
}
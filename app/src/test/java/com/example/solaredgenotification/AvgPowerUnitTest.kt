package com.example.solaredgenotification

import org.junit.Assert.*
import org.junit.Test

class AvgPowerUnitTest {
    @Test
    fun simple_avg_power_isCorrect() {
        val calc = MainActivity();
        var list: MutableList<SolarEdgeResults> = mutableListOf()

        list.add(SolarEdgeResults(0, 0, false))
        list.add(SolarEdgeResults(20000, 20000, false))
        list.add(SolarEdgeResults(10000, 10000, false))

        assertEquals(calc.avgPower(list), 10000)
    }

    @Test
    fun realistic_avg_power_isCorrect() {
        val calc = MainActivity();
        var list: MutableList<SolarEdgeResults> = mutableListOf()

        list.add(SolarEdgeResults(9000, 9000, false))
        list.add(SolarEdgeResults(1000, 10000, false))
        list.add(SolarEdgeResults(10000, 8000, false))

        assertEquals(calc.avgPower(list), 9000)
    }



    @Test
    fun decimal_avg_power_isCorrect() {
        val calc = MainActivity();
        var list: MutableList<SolarEdgeResults> = mutableListOf()

        list.add(SolarEdgeResults(9000, 9600, false))
        list.add(SolarEdgeResults(1000, 8000, false))
        list.add(SolarEdgeResults(10000, 7000, false))

        assertEquals(calc.avgPower(list), 8200)
    }
}
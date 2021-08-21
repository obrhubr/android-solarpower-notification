package com.example.solaredgenotification

import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDateTime

/**
 * Local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */

class FormatDateUnitTest {
    @Test
    fun simple_format_isCorrect() {
        val formatter = MainActivity();
        assertEquals(formatter.formatDate(LocalDateTime.of(2006, 10, 11, 10, 11, 12)), "2006-10-11%2010:11:12")
    }

    @Test
    fun padding_zero_format_isCorrect() {
        val formatter = MainActivity();
        assertEquals(formatter.formatDate(LocalDateTime.of(2006, 1, 2, 1, 2, 3)), "2006-01-02%2001:02:03")
    }
}
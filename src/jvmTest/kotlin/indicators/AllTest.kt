package org.cerion.stocks.core.indicators

import org.cerion.stocks.core.functions.types.Indicator
import org.junit.Assert.assertEquals
import org.junit.Test

class AllTest {

    @Test
    fun correctEnumReturned() {
        for (i in Indicator.values()) {
            val instance = i.instance
            assertEquals("enum does not match instance", i, instance.id)
        }
    }
}

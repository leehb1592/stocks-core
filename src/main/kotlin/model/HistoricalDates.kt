package org.cerion.stocklist.model

import java.util.Date

class HistoricalDates {
    var symbol: String? = null
    var lastUpdated: Date? = null
    var firstDate: Date? = null
    var lastDate: Date? = null

    // TODO add count requested, may have less cached than needed
}
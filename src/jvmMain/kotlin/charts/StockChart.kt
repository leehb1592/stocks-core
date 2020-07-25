package org.cerion.stocks.core.charts

import org.cerion.stocks.core.PriceList
import org.cerion.stocks.core.arrays.BandArray
import org.cerion.stocks.core.arrays.FloatArray
import org.cerion.stocks.core.arrays.PairArray
import org.cerion.stocks.core.arrays.ValueArray
import org.cerion.stocks.core.functions.FunctionBase
import org.cerion.stocks.core.functions.IIndicator
import org.cerion.stocks.core.functions.IOverlay
import org.cerion.stocks.core.functions.ISimpleOverlay
import org.cerion.stocks.core.functions.types.IFunctionEnum
import org.cerion.stocks.core.functions.types.Overlay
import org.cerion.stocks.core.overlays.ParabolicSAR
import org.cerion.stocks.core.platform.KMPDate
import java.util.*

abstract class StockChart(protected val _colors: ChartColors) : Cloneable {

    protected var _overlays: MutableList<IOverlay> = ArrayList()
    private var _nextColor = 0

    open val overlays: List<IFunctionEnum>
        get() = Overlay.values().toList()

    val overlayCount: Int
        get() = _overlays.size

    abstract fun getDataSets(priceList: PriceList): List<IDataSet>

    fun getDates(list: PriceList): Array<KMPDate> = list.dates.sliceArray(1 until list.dates.size)

    @Throws(CloneNotSupportedException::class)
    public override fun clone(): Any {
        val clone = super.clone() as StockChart

        // Copy overlays
        clone._overlays = ArrayList()

        for (overlay in _overlays) {
            val copy = overlay.id.instance
            copy.setParams(*overlay.params.toTypedArray().clone())
            clone._overlays.add(copy as IOverlay)
        }

        return clone
    }

    fun addOverlay(overlay: ISimpleOverlay): StockChart {
        _overlays.add(overlay)
        return this
    }

    fun clearOverlays() {
        _overlays.clear()
    }

    fun getOverlay(position: Int): IOverlay = _overlays[position]

    protected abstract fun getSerializedParams(): Map<String, String>
    protected abstract fun setSerializedParams(params: Map<String, String>)

    private fun getNextColor(ignoreColor: Int): Int {
        val color = _colors.getOverlayColor(_nextColor++)

        if (color == ignoreColor && color != 0) // Ignore zero for unit tests that don't set color values
            return getNextColor((ignoreColor))

        // For volume teal is too close to default so replace with purple
        if (this is VolumeChart && color == _colors.teal)
            return _colors.primaryPurple

        return color
    }

    protected fun resetNextColor() {
        _nextColor = 0
    }

    protected fun getDefaultOverlayDataSets(arr: ValueArray, overlay: IOverlay, ignoreColor: Int): List<DataSet> {
        val label = overlay.toString()

        return when (arr) {
            is BandArray -> arr.getDataSets(label, label, getNextColor(ignoreColor))
            // TODO these are more complex for colors, look into later, using primary as placeholder
            is PairArray -> arr.getDataSets(label, label, _colors.primary, _colors.primary)
            is FloatArray -> {
                val dataSet = arr.toDataSet(label, getNextColor(ignoreColor))
                if (overlay is ParabolicSAR)
                    dataSet.lineType = LineType.DOTTED

                listOf(dataSet)
            }
            else -> throw NotImplementedError()
        }
    }

    fun serialize(): String {
        // Format for serialization
        // type:price;logScale:false;overlays:[SMA(234),TEST(2,3,4];
        // Parameters split by ;
        // KeyValue split by   :
        // List split by       ,

        var result = "type:" + when(this) {
            is PriceChart -> "price"
            is VolumeChart -> "volume"
            is IndicatorChart -> "indicator"
            else -> throw NotImplementedError()
        }

        val params = getSerializedParams()
        params.forEach {
            result += ";${it.key}:${it.value}"
        }

        if (overlayCount > 0) {
            result += ";overlays:[" + _overlays.map { it.serialize() }.joinToString(",") + "]"
        }

        return result
    }

    companion object {
        fun deserialize(str: String, colors: ChartColors = ChartColors()): StockChart {
            val params = str.split(";")
            val map = mutableMapOf<String, String>()
            for(param in params) {
                val keyval = param.split(":")
                if (keyval.size != 2)
                    continue

                if (keyval[0] == "overlays")
                    map[keyval[0]] = keyval[1].replace("[", "").replace("]", "").replace("),", ")\t")
                else
                    map[keyval[0]] = keyval[1]
            }

            val overlays = map["overlays"]?.split("\t")?.map { FunctionBase.deserialize(it) }

            val result = when(map["type"]) {
                "price" -> {
                    PriceChart(colors).apply {
                        overlays?.forEach {
                            addOverlay(it as IOverlay)
                        }
                    }
                }
                "volume" -> {
                    VolumeChart(colors).apply {
                        overlays?.forEach {
                            addOverlay(it as ISimpleOverlay)
                        }
                    }
                }
                "indicator" -> {
                    val indicator = FunctionBase.deserialize(map["indicator"]!!) as IIndicator
                    IndicatorChart(indicator, colors).apply {
                        overlays?.forEach {
                            addOverlay(it as ISimpleOverlay)
                        }
                    }
                }

                else -> throw NotImplementedError()
            }

            // Set misc parameters specific to each chart
            result.setSerializedParams(map)

            return result
        }
    }
}

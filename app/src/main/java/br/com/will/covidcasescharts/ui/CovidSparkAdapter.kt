package br.com.will.covidcasescharts.ui

import br.com.will.covidcasescharts.model.CovidData
import com.robinhood.spark.SparkAdapter
import java.sql.Time

class CovidSparkAdapter(
    private val dailyData: List<CovidData>
) : SparkAdapter() {

    var metric = Metric.POSITIVE
    var daysAgo = TimeScale.MAX

    override fun getCount(): Int = dailyData.size

    override fun getItem(index: Int) = dailyData[index]

    override fun getY(index: Int): Float {
        val chosenDayData = dailyData[index]
        return when (metric) {
            Metric.NEGATIVE -> chosenDayData.negativeIncrease.toFloat()
            Metric.POSITIVE -> chosenDayData.positiveIncrease.toFloat()
            Metric.DEATH -> chosenDayData.deathIncrease.toFloat()
        }
    }

}
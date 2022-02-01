package br.com.will.covidcasescharts.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import br.com.will.covidcasescharts.model.CovidData
import br.com.will.covidcasescharts.service.CovidService
import com.google.gson.GsonBuilder
import com.robinhood.spark.SparkView
import org.angmarch.views.NiceSpinner
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private val ALL_STATES = "All (Nationwide)"
    private lateinit var currentlyShownData: List<CovidData>
    private lateinit var covidSparkAdapter: CovidSparkAdapter
    private lateinit var perStateDailyData: Map<String, List<CovidData>>
    private lateinit var nationalDailyData: List<CovidData>
    private val BASE_URL = "https://api.covidtracking.com/v1/"
    private val TAG = "MainActivity"

    private lateinit var tvMetricLabel: TextView
    private lateinit var tvDateLabel: TextView
    private lateinit var radioButtonNegative: RadioButton
    private lateinit var radioButtonPositive: RadioButton
    private lateinit var radioButtonDeath: RadioButton
    private lateinit var radioButtonWeek: RadioButton
    private lateinit var radioButtonMonth: RadioButton
    private lateinit var radioButtonMax: RadioButton
    private lateinit var radioGroupMetricSelection: RadioGroup
    private lateinit var radioGroupTimeSelection: RadioGroup
    private lateinit var sparkView: SparkView
    private lateinit var spinnerSelect: NiceSpinner


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initializeComponents()

        val gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create()
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        val covidService = retrofit.create(CovidService::class.java)

        //Fetch the national data
        covidService.getNationalData().enqueue(object: Callback<List<CovidData>>{
            override fun onResponse(
                call: Call<List<CovidData>>,
                response: Response<List<CovidData>>
            ) {
                val nationalData = response.body()
                if(nationalData == null){
                    Log.w(TAG, "Did not receive a valid response body")
                    return
                }
                setupEventListeners()
                nationalDailyData = nationalData.reversed()
                Log.i(TAG,"Update graph with national data")
                updateDisplayWithData(nationalDailyData)
            }

            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e(TAG, "onFailure ${t}")
            }

        })

        //Fetch the state data
        covidService.getStatesData().enqueue(object: Callback<List<CovidData>> {
            override fun onResponse(
                call: Call<List<CovidData>>,
                response: Response<List<CovidData>>
            ) {
                val statesData = response.body()
                if (statesData == null) {
                    Log.w(TAG, "Did not receive a valid response body")
                    return
                }
                perStateDailyData = statesData.reversed().groupBy { it.state }
                //Update spinner with state names
                updateSpinnerWithStateData(perStateDailyData.keys)
            }

            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e(TAG, "onFailure ${t}")
            }
        })

    }

    private fun updateSpinnerWithStateData(stateNames: Set<String>) {
        val stateAbbreviatonList = stateNames.toMutableList()
        stateAbbreviatonList.sort()
        stateAbbreviatonList.add(0, ALL_STATES)

        //Add state list as data source for the spinner
        spinnerSelect.attachDataSource(stateAbbreviatonList)
        spinnerSelect.setOnSpinnerItemSelectedListener { parent, view, position, id ->
            val selectedState = parent.getItemAtPosition(position) as String
            val selectedData = perStateDailyData[selectedState] ?: nationalDailyData
            updateDisplayWithData(selectedData)
        }

    }

    private fun setupEventListeners() {
        //Add a listener for the user scrubbing on the chart
        sparkView.isScrubEnabled = true
        sparkView.setScrubListener { itemData ->
            if(itemData is CovidData){
                updateInfoForDate(itemData)
            }
        }
        //Respond to radio button selected events
        radioGroupTimeSelection.setOnCheckedChangeListener { radioGroup, checkedId ->
            covidSparkAdapter.daysAgo = when (checkedId){
                R.id.radioButtonWeek -> TimeScale.WEEK
                R.id.radioButtonMonth -> TimeScale.MONTH
                else -> TimeScale.MAX
            }
            covidSparkAdapter.notifyDataSetChanged()
        }

        radioGroupMetricSelection.setOnCheckedChangeListener { radioGroup, checkedId ->
            when(checkedId){
                R.id.radioButtonPositive -> updateDisplayMetric(Metric.POSITIVE)
                R.id.radioButtonNegative -> updateDisplayMetric(Metric.NEGATIVE)
                R.id.radioButtonDeath -> updateDisplayMetric(Metric.DEATH)
            }
        }
    }

    private fun updateDisplayMetric(metric: Metric) {
        //Update the color of the chart

        val colorResource = when (metric){
            Metric.NEGATIVE -> R.color.colorNegative
            Metric.POSITIVE -> R.color.colorPositive
            Metric.DEATH -> R.color.colorDeath
        }

        @ColorInt val color = ContextCompat.getColor(this, colorResource)
        sparkView.lineColor = color
        tvMetricLabel.setTextColor(color)

        //Update the metric on the adapter
        covidSparkAdapter.metric = metric
        covidSparkAdapter.notifyDataSetChanged()

        //Reset number and date shown in the bottom text views
        updateInfoForDate(currentlyShownData.last())
    }

    private fun updateDisplayWithData(dailyData: List<CovidData>) {
        currentlyShownData = dailyData
        //create a new sparkAdapter with the data
        covidSparkAdapter = CovidSparkAdapter(dailyData)
        sparkView.adapter = covidSparkAdapter
        //update radio buttons to select the positive cases and max time by default
        radioButtonPositive.isChecked = true
        radioButtonMax.isChecked = true
        //display metric for the most recent date
        updateDisplayMetric(Metric.POSITIVE)
    }

    private fun updateInfoForDate(covidData: CovidData) {
        val numCases = when(covidSparkAdapter.metric) {
            Metric.NEGATIVE -> covidData.negativeIncrease
            Metric.POSITIVE -> covidData.positiveIncrease
            Metric.DEATH -> covidData.deathIncrease
        }
        tvMetricLabel.text = NumberFormat.getInstance().format(numCases)
        val simpleDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        tvDateLabel.text = simpleDateFormat.format(covidData.dateChecked)
    }

    private fun initializeComponents(){
        tvMetricLabel = findViewById(R.id.tvMetricLabel)
        tvDateLabel = findViewById(R.id.tvDateLabel)
        radioButtonNegative = findViewById(R.id.radioButtonNegative)
        radioButtonPositive = findViewById(R.id.radioButtonPositive)
        radioButtonDeath = findViewById(R.id.radioButtonDeath)
        radioButtonWeek = findViewById(R.id.radioButtonWeek)
        radioButtonMonth = findViewById(R.id.radioButtonMonth)
        radioButtonMax = findViewById(R.id.radioButtonMax)
        radioGroupMetricSelection = findViewById(R.id.radioGroupMetricSelection)
        radioGroupTimeSelection = findViewById(R.id.radioGroupTimeSelection)
        sparkView = findViewById(R.id.sparkView)
        spinnerSelect = findViewById(R.id.spinnerSelect)
    }
}
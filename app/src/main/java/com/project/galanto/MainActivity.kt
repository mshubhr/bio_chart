package com.project.galanto

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.FirebaseApp
import com.google.firebase.database.*
import com.project.galanto.databinding.ActivityMainBinding
import com.project.galanto.model.Movement
import com.project.galanto.model.ROM
import com.project.galanto.model.UserMovementModel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val arrayList = ArrayList<UserMovementModel>()
    val movementArray = ArrayList<Movement>()
    val romArray = ArrayList<ROM>()

    private lateinit var dateSpinner: Spinner
    private lateinit var sessionSpinner: Spinner
    private var dateList = mutableListOf<String>()
    private var sessionList = listOf("Session1", "Session2")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initUi()

        dateSpinner = findViewById(R.id.date)
        sessionSpinner = findViewById(R.id.session)

        setupSpinners()

        dateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { }

            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }

        sessionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { }

            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }
    }

    private fun initUi() {
        try {
            getUserMovementData()
            getUserMovementAdapter()
            getFirebaseInstance()
            createPDF()
        } catch (e: Exception) {
            Log.e("Exception", "initUi: ${e.message}", e)
        }
    }

    private fun getUserMovementData() {
        arrayList.add(UserMovementModel("Session Time", 5, "2"))
        arrayList.add(UserMovementModel("Movement Score", 10, "8"))
        arrayList.add(UserMovementModel("Success Rate", 100, "88"))
    }

    private fun getUserMovementAdapter() {
        binding.rvUserMovement.apply {
            adapter = UserMovementAdapter(this@MainActivity, arrayList)
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
        }
    }

    private fun getFirebaseInstance() {
        val firebase = FirebaseDatabase.getInstance("https://galanto-5d6de-default-rtdb.asia-southeast1.firebasedatabase.app")
        val databaseReference = firebase.reference
        try {
            databaseReference.addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val userSnapshot = snapshot.child("user1")
                    val historySnapshot = userSnapshot.child("history")

                    historySnapshot.children.forEach { dateSnapshot ->
                        val date = dateSnapshot.child("date").value.toString()
                        val rom = dateSnapshot.child("ROM").value.toString().toIntOrNull() ?: 0
                        romArray.add(ROM(date, rom.toString()))

                        dateSnapshot.child("sessions").children.forEach { sessionSnapshot ->
                            val angleTimeList = sessionSnapshot.value as? ArrayList<Map<*, *>>
                            angleTimeList?.forEach { angleTimeMap ->
                                val angle = (angleTimeMap["angle"] ?: "").toString()
                                val time = (angleTimeMap["time"] ?: "").toString()
                                movementArray.add(Movement(angle, time, date, sessionSnapshot.key.toString()))
                            }
                        }
                    }

                    dateList.clear()
                    romArray.forEach { rom ->
                        dateList.add(rom.date)
                    }
                    (dateSpinner.adapter as ArrayAdapter<*>).notifyDataSetChanged()

                    updateHistoryChart(romArray[0].date)
                    updateMovementChart(romArray[0].date, "Session1")

                    Log.e("SIZE", "onChildAdded: ${movementArray.size} .... ${romArray.size}")

                    try {
                        if (movementArray.isNotEmpty()) {
                            getMovementChartData(movementArray)
                        }
                        getHistoryChartData(romArray.toString())
                    } catch (e: Exception) {
                        Log.e("Crash", "onChildAdded: ${e.message}")
                    }
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    Log.e("TAG", "onChildChanged")
                }

                override fun onChildRemoved(snapshot: DataSnapshot) {
                    Log.e("TAG", "onChildRemoved")
                }

                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                    Log.e("TAG", "onChildMoved")
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("TAG", "onCancelled")
                }
            })

        } catch (e: Exception) {
            Log.e("TAG", "getFirebaseInstance: ${e.message}")
        }
        Log.e("TAG", "getFirebaseInstance")
    }

    private fun updateMovementChart(selectedDate: String, selectedSession: String) {
        val filteredMovement = movementArray.filter { movement ->
            movement.date == selectedDate&& movement.session == selectedSession
        }
        getMovementChartData(filteredMovement)
    }

    private fun updateHistoryChart(selectedDate: String) {
        val romValue = romArray.find { it.date == selectedDate }?.rom ?: "0"
        getHistoryChartData(romValue)
    }

    private fun getMovementChartData(movement: List<Movement>) {
        val leftAxis = binding.chartMovementData.axisLeft
        leftAxis.setLabelCount(5, true)
        leftAxis.axisMinimum = -20f
        leftAxis.axisMaximum = 60f
        val xAxis = binding.chartMovementData.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 200f
        xAxis.axisMinimum = 0f
        xAxis.axisMaximum = 1400f

        binding.chartMovementData.axisRight.isEnabled = false
        binding.chartMovementData.isDragEnabled = true
        binding.chartMovementData.setScaleEnabled(true)
        binding.chartMovementData.setPinchZoom(true)

        val entries = ArrayList<Entry>()
        for (i in 0..7) {
            entries.add(Entry(i.toFloat() * 200, i.toFloat() * 20))
        }
        for (i in movement.indices) {
            Log.e("MovementData", "getMovementChartData: ${movement[i].angle}")
        }
        val dataSet = LineDataSet(entries, "Movement Data")
        val lineData = LineData(dataSet)
        binding.chartMovementData.data = lineData
        val legend = binding.chartMovementData.legend
        legend.textSize = 16f
        legend.typeface = Typeface.DEFAULT_BOLD
        legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
        legend.orientation = Legend.LegendOrientation.HORIZONTAL
        legend.setDrawInside(false)
        binding.chartMovementData.invalidate()
    }

    private fun getHistoryChartData(rom: String) {
        try {
            val leftAxis = binding.chartHistoryData.axisLeft
            leftAxis.setLabelCount(6, true)
            leftAxis.axisMinimum = 0f
            leftAxis.axisMaximum = 50f
            leftAxis.textSize = 12f
            leftAxis.typeface = Typeface.DEFAULT_BOLD

            val xAxis = binding.chartHistoryData.xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.textSize = 12f
            xAxis.typeface = Typeface.DEFAULT_BOLD

            xAxis.valueFormatter = object : ValueFormatter() {
                private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
                override fun getFormattedValue(value: Float): String {
                    val date = Calendar.getInstance().apply {
                        time = dateFormat.parse("21 june 2023")!!
                        add(Calendar.DATE, value.toInt())
                    }
                    return dateFormat.format(date.time)
                }
            }

            binding.chartHistoryData.axisRight.isEnabled = false
            binding.chartHistoryData.isDragEnabled = true
            binding.chartHistoryData.setScaleEnabled(true)
            binding.chartHistoryData.setPinchZoom(true)

            val entries = mutableListOf<Entry>()

            entries.add(Entry(0f, rom.toFloatOrNull() ?: 0f))

            val dataSet = LineDataSet(entries, "ROM History")
            dataSet.color = Color.RED
            dataSet.valueTextColor = Color.BLUE
            dataSet.valueTextSize = 12f
            dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
            dataSet.setDrawFilled(true)
            dataSet.fillColor = Color.GREEN
            dataSet.setCircleColor(Color.BLACK)

            val lineData = LineData(dataSet)
            binding.chartHistoryData.data = lineData
            binding.chartHistoryData.invalidate()

        } catch (e: Exception) {
            Log.e("Crash", "getHistoryChartData: ${e.message}")
        }
    }

    private fun createPDF() {
        binding.root.post {
            try {
                val bitmap = getBitmapFromView(binding.root)
                val pdfFile = File(this.getExternalFilesDir("/"), "Screenshot.pdf")
                val outputStream = FileOutputStream(pdfFile)
                val document = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                document.finishPage(page)
                document.writeTo(outputStream)
                document.close()
                outputStream.close()

                val uri = FileProvider.getUriForFile(this, "$packageName.provider", pdfFile)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = uri
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(intent)

            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }


    private fun getBitmapFromView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgDrawable = view.background
        if (bgDrawable != null) bgDrawable.draw(canvas)
        else canvas.drawColor(Color.WHITE)
        view.draw(canvas)
        return bitmap
    }

    private fun setupSpinners() {
        val dateAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, dateList)
        dateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dateSpinner.adapter = dateAdapter

        val sessionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sessionList)
        sessionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sessionSpinner.adapter = sessionAdapter

        dateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedDate = dateList[position]
                updateHistoryChart(selectedDate)
                val selectedSession = sessionSpinner.selectedItem.toString()
                updateMovementChart(selectedDate, selectedSession)
            }

            override fun onNothingSelected(p0: AdapterView<*>?) { }
        }

        sessionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedSession = sessionList[position]
                val selectedDate = dateSpinner.selectedItem.toString()
                updateMovementChart(selectedDate, selectedSession)
            }

            override fun onNothingSelected(p0: AdapterView<*>?) { }
        }
    }
}
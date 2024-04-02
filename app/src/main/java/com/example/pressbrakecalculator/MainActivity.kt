package com.example.pressbrakecalculator

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pressbrakecalculator.ui.theme.PressBrakeCalculatorTheme
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import kotlin.math.*

const val FILE_NAME = "bending_data.txt"
const val BASE_DEGREES = 90
// Map of gauge as an int to ArrayList of (degree, BND) Pairs
val dataMap = HashMap<Int, ArrayList<Pair<Float, Float>>>()
val bestSlopes = HashMap<Int, Float>()
// The base degree and bend value to use for each gauge, typically a pair of 90 degrees bend
val baseBendValues = HashMap<Int, Pair<Float, Float>>()

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PressBrakeCalculatorTheme {
                initializeMapFromFile(filesDir)
                BendCalculatorApp(filesDir)
            }
        }
    }
}

@Preview
@Composable
fun BendCalculatorApp(filesDir : File) {
    InputFields(filesDir,
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
    )
}

fun isNumber(input: String): Boolean {
    if (input == "" || input == ".") {
        return false
    }
    val integerChars = '0'..'9'
    var decimalQuantity = 0
    return input.all { it in integerChars || it == '.' && decimalQuantity++ < 1 }
}

// Rounds given bend point to 3 decimal places at most and returns it
fun roundBendPoint(bendPoint : Float) : String {
    return String.format("%.3f", bendPoint)
}
fun roundBendPoint(bendPoint : String) : String {
    return String.format("%.3f", bendPoint)
}

// Calculate and return the estimated BND point value that will result in the given degrees for the given gauge.
// Output has undefined behaviour if the best fit slope has not yet been calculated.
fun calculateBendPoint(gauge : Int, degrees : Float) : Float {
    val base = baseBendValues[gauge]
    val degreeDifference = degrees - base!!.first
    return (base.second + (bestSlopes[gauge]!! * degreeDifference))
}

@Composable
fun InputFields(filesDir : File, modifier: Modifier = Modifier) {
    Column (
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var bendText by remember { mutableStateOf("") }
        var gaugeText by remember { mutableStateOf("") }
        var degreesText by remember { mutableStateOf("") }

        fun updateBendPoint() {
            if (!isNumber(gaugeText) || !isNumber(degreesText)) {
                return
            }
            if (!bestSlopes.containsKey(gaugeText.toInt())) {
                return
            }
            bendText = roundBendPoint(calculateBendPoint(gaugeText.toInt(), degreesText.toFloat()))
        }

        TextField(
            value = bendText,
            onValueChange = { bendText = it },
            label = { Text("BND Point") },
            modifier = Modifier.padding(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        TextField(
            value = gaugeText,
            onValueChange = { gaugeText = it; updateBendPoint() },
            label = { Text("Gauge") },
            modifier = Modifier.padding(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        TextField(
            value = degreesText,
            onValueChange = { degreesText = it; updateBendPoint() },
            label = { Text("Degrees") },
            modifier = Modifier.padding(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        Button(onClick = {
            logValues(
                filesDir,
                bendText,
                gaugeText,
                degreesText
            )
        }) {
            Text(stringResource(R.string.log_values))
        }
    }
}

fun logValues(filesDir : File, bendText: String, gaugeText: String, degreesText: String) {
    if (!isNumber(bendText) || !isNumber(gaugeText) || !isNumber(degreesText)) {
        return
    }

    val file : File = File(filesDir, FILE_NAME)
    val pw = PrintWriter(FileOutputStream(file, true))
    pw.println("$gaugeText,$degreesText,$bendText")
    pw.close()

    Log.d("logValues", "logged gauge $gaugeText, degrees $degreesText, BND point $bendText")

    addToDataMap(gaugeText.toInt(), degreesText.toFloat(), bendText.toFloat())
    calculateBestSlope(gaugeText.toInt())
}

fun addToDataMap(gauge : Int, degrees: Float, bendPoint: Float) {
    if (!dataMap.containsKey(gauge)) {
        // Create new map entry and give it new data point
        dataMap[gauge] = arrayListOf(Pair(degrees, bendPoint))
    } else {
        // Add new data point to existing map entry
        dataMap[gauge] = ArrayList(dataMap.getValue(gauge) + Pair(degrees, bendPoint))
    }
    tryUpdateBaseBendValues(gauge, degrees, bendPoint)
}

// Populate the dataMap by reading data from the file
fun initializeMapFromFile(filesDir : File) {
    val file : File = File(filesDir, FILE_NAME)
    if (!file.exists()) {
        return
    }

    val contents = file.readText()
    val data = contents.split("\n").toTypedArray()

    // Process each data point
    for (line in data) {
        if (line.isEmpty()) {
            continue
        }
        val splitLine = line.split(",").toTypedArray()
        val gauge: Int = splitLine[0].toInt()
        val x: Float = splitLine[1].toFloat()
        val y: Float = splitLine[2].toFloat()

        addToDataMap(gauge, x, y)
    }

    Log.d("dataMap updated", "current dataMap: $dataMap")

    calculateBestSlopes()
}

// Update a gauge's base bend value if it's close to 90 degrees
fun tryUpdateBaseBendValues(gauge : Int, degrees: Float, bendPoint : Float) {
    if (!baseBendValues.containsKey(gauge) ||
        abs(BASE_DEGREES - degrees) < abs(BASE_DEGREES - baseBendValues[gauge]!!.first)) {
        baseBendValues[gauge] = Pair(degrees, bendPoint)
    }
}

// Calculate the best slopes for each gauge that has a key in the dataMap
fun calculateBestSlopes() {
    for ((key, value) in dataMap) {
        calculateBestSlope(key)
    }
}

// Calculate the best slope for the given gauge
fun calculateBestSlope(gauge : Int) {
    if (!dataMap.containsKey(gauge) || dataMap[gauge]!!.size <= 1) {
        Log.d("calculateBestSlope", "failed to calculate best slope for gauge $gauge due to insufficient data (<= 1)")
        return
    }

    var n = 0
    var sigmaX : Float = 0f
    var sigmaXSquared : Float = 0f
    var sigmaY : Float = 0f
    var sigmaXY : Float = 0f

    // Process each data point
    for (pair in dataMap[gauge]!!) {
        n++
        val x : Float = pair.first
        val y : Float = pair.second
        sigmaX += x
        sigmaXSquared += x * x
        sigmaY += y
        sigmaXY += x * y
    }

    val bestSlope = (n * sigmaXY - sigmaX * sigmaY) / (n * sigmaXSquared - sigmaX * sigmaX)

    // Ignore bad slopes that would result for example if all the data points have the same x
    if (!bestSlope.isNaN() && !bestSlope.isInfinite()) {
        bestSlopes[gauge] = bestSlope
    }
}

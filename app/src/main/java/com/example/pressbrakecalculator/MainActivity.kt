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

const val FILE_NAME = "bending_data.txt"
const val OFFSET_18_GAUGE = 0.010f
const val OFFSET_20_GAUGE = 0.030f
const val OFFSET_24_GAUGE = 0.045f
const val BASE_BND_VALUE = 5.96f
const val BASE_DEGREES = 90
const val UNCONFIGURED_SLOPE : Float = -999f
var bestSlope : Float = UNCONFIGURED_SLOPE


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PressBrakeCalculatorTheme {
                calculateBestSlope(filesDir)
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
    if (input == "") {
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
    val degreeDifference = degrees - BASE_DEGREES
    return (BASE_BND_VALUE + (bestSlope * degreeDifference))
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
            if (bestSlope == UNCONFIGURED_SLOPE) {
                return
            }
            if (!isNumber(gaugeText) || !isNumber(degreesText)) {
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

// Normalizes given bend point value to use the base gauge (16) instead of the given gauge and returns it
fun adjustBendForGauge(bendValue : String, gauge : String) : String {
    var ret : Float = 0f
    try {
        ret = bendValue.toFloat()
        when (gauge) {
            "16" -> { }
            "18" -> ret -= OFFSET_18_GAUGE
            "20" -> ret -= OFFSET_20_GAUGE
            "24" -> ret -= OFFSET_24_GAUGE
            else -> {
                Log.d("adjustBendForGauge()", "Gauge $gauge not recognized")
            }
        }
        return ret.toString()
    } catch (e : Throwable) {

    }
    return bendValue
}

fun logValues(filesDir : File, bendText: String, gaugeText: String, degreesText: String) {
    if (!isNumber(bendText) || !isNumber(gaugeText) || !isNumber(degreesText)) {
        return
    }
    // Adjust bend value based on gauge
    val adjustedBend = adjustBendForGauge(bendText, gaugeText)

    val file : File = File(filesDir, FILE_NAME)
    val pw = PrintWriter(FileOutputStream(file, true))
    pw.println("$degreesText,$adjustedBend")
    pw.close()

    calculateBestSlope(filesDir)
}

fun calculateBestSlope(filesDir : File) {
    val file : File = File(filesDir, FILE_NAME)
    if (!file.exists()) {
        return
    }

    val contents = file.readText()
    val data = contents.split("\n").toTypedArray()

    var n = 0
    var sigmaX : Float = 0f
    var sigmaXSquared : Float = 0f
    var sigmaY : Float = 0f
    var sigmaXY : Float = 0f

    // Process each data point
    for (line in data) {
        if (line.isEmpty()) {
            continue
        }
        val splitLine = line.split(",").toTypedArray()
        Log.d("calculateBestSlope", "Reading line $line")
        n++
        val x : Float = splitLine[0].toFloat()
        val y : Float = splitLine[1].toFloat()
        sigmaX += x
        sigmaXSquared += x * x
        sigmaY += y
        sigmaXY += x * y
    }

    if (n <= 1) {
        return
    }

    bestSlope = (n * sigmaXY - sigmaX * sigmaY) / (n * sigmaXSquared - sigmaX * sigmaX)
}

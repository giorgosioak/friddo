package com.giorgosioak.friddo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.giorgosioak.friddo.ui.theme.FriddoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FriddoTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FridaStatusCard()
                }
            }
        }
    }
}

@Composable
fun FridaStatusCard() {
    Box (
        modifier = Modifier
            .wrapContentSize()
            .padding(0.dp)
            .background(Color.LightGray, shape = RoundedCornerShape(4.dp))
    ){
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        text = "Frida Server: ",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right
                    )
                    Text(
                        text = "Status: ",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right
                    )
                }
                Column {
                    Text(text = "16.1.4 (arm64)")
                    val active = Text(text = "Active", color = Color.Green)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FridaStatusCardPreview() {
    FriddoTheme {
        FridaStatusCard()
    }
}
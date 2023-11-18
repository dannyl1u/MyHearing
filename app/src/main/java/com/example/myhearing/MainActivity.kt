package com.example.myhearing
//
//import android.content.Intent
//import android.os.Bundle
//import android.widget.Button
//import android.widget.Toast
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Surface
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.tooling.preview.Preview
//import com.example.myhearing.ui.theme.MyHearingTheme
//
//class MainActivity : ComponentActivity() {
//    lateinit var dbMeterButton : Button
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.main_activity)
//        dbMeterButton = findViewById(R.id.dbDemoButton)
//        dbMeterButton.setOnClickListener{
////            Toast.makeText(this, " test...", Toast.LENGTH_SHORT).show()
//            val intent : Intent = Intent(this, DecibelMeterDemoActivity::class.java)
//            startActivity(intent)
//        }
//
////        setContent {
////            MyHearingTheme {
////                // A surface container using the 'background' color from the theme
////                Surface(
////                    modifier = Modifier.fillMaxSize(),
////                    color = MaterialTheme.colorScheme.background
////                ) {
////                    Greeting("Android")
////                }
////            }
////        }
//    }
//}
//
//@Composable
//fun Greeting(name: String, modifier: Modifier = Modifier) {
//    Text(
//        text = "Hello $name!",
//        modifier = modifier
//    )
//}
//
//@Preview(showBackground = true)
//@Composable
//fun GreetingPreview() {
//    MyHearingTheme {
//        Greeting("Android")
//    }
//}


import android.content.Intent
import android.os.Bundle
//import android.widget.Toolbar
import androidx.activity.ComponentActivity
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.example.myhearing.R
import com.google.android.material.navigation.NavigationView

import androidx.appcompat.widget.Toolbar

class MainActivity : AppCompatActivity() {
//class MainActivity : ComponentActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)


        // Set up the ActionBarDrawerToggle
        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Set up navigation item click listener
        navigationView.setNavigationItemSelectedListener { menuItem ->
            // Handle item clicks here
            when (menuItem.itemId) {
                R.id.nav_item1 -> {
                    // Handle Item 1 click
                    val intent : Intent = Intent(this, DecibelMeterDemoActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.nav_item2 -> {
                    // Handle Item 2 click
                    val intent : Intent = Intent(this, HeatMap::class.java)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
    }
}

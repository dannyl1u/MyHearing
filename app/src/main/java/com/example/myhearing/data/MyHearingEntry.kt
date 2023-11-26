package com.example.myhearing.data

data class MyHearingEntry(
    val id: Int,
    val dateTime: String,
    val dbLevel: Double,
    val comment: String,
    val location: String
)

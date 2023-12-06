package com.example.myhearing.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.myhearing.HeatmapActivity
import com.google.android.gms.maps.model.LatLng
import java.text.DecimalFormat

class MyHearingDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        const val DATABASE_NAME = "MyHearingDatabase"
        const val DATABASE_VERSION = 1
        const val TABLE_NAME = "MyHearingTable"

        const val ID_COLUMN = "id"
        const val TIME_COLUMN = "dateTime"
        const val DB_LEVEL_COLUMN = "dbLevel"
        const val COMMENT_COLUMN = "comment"
        const val LOCATION_COLUMN = "location"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = "CREATE TABLE $TABLE_NAME (" +
                "$ID_COLUMN INTEGER PRIMARY KEY AUTOINCREMENT," +
                "$TIME_COLUMN REAL," +
                "$DB_LEVEL_COLUMN REAL," +
                "$COMMENT_COLUMN TEXT," +
                "$LOCATION_COLUMN TEXT)"
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    }

    fun getRecordsSince(timestamp: Long): MutableList<Triple<Long, LatLng, Double>> {
        val db = this.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_NAME WHERE $TIME_COLUMN > ?",
            arrayOf(timestamp.toString())
        )
        val records = mutableListOf<Triple<Long, LatLng, Double>>()

        while (cursor.moveToNext()) {
            var time = 0L
            val timeColumnIndex = cursor.getColumnIndex(TIME_COLUMN)
            if (timeColumnIndex != -1) {
                time = cursor.getLong(timeColumnIndex)
            }

            var decibel = 0f
            val decibelColumnIndex = cursor.getColumnIndex(DB_LEVEL_COLUMN)
            if (decibelColumnIndex != -1) {
                decibel = cursor.getFloat(decibelColumnIndex)
            }

            var locationString = "0.0000000, 0.0000000"
            val locationColumnIndex = cursor.getColumnIndex(LOCATION_COLUMN)
            if (locationColumnIndex != -1) {
                locationString = cursor.getString(locationColumnIndex)
            }

            val locationRegex = Regex("(-?\\d+\\.\\d+),\\s*(-?\\d+\\.\\d+)")
            val latLngPair = locationRegex.find(locationString)

            latLngPair?.let {
                val (lat, lng) = it.destructured
                val latLng = LatLng(
                    DecimalFormat(HeatmapActivity.DEFAULT_LOCATION_PATTERN).format(lat.toDouble())
                        .toDouble(),
                    DecimalFormat(HeatmapActivity.DEFAULT_LOCATION_PATTERN).format(lng.toDouble())
                        .toDouble()
                )

                records.add(Triple(time, latLng, decibel.toDouble()))
            }
        }

        cursor.close()
        db.close()

        return records
    }

    fun getRecentDecibelRecords(): List<Pair<Long, Float>> {
        val db = this.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_NAME ORDER BY $TIME_COLUMN DESC LIMIT ?",
            arrayOf(21.toString())
        )

        val records = mutableListOf<Pair<Long, Float>>()

        while (cursor.moveToNext()) {
            val timeColumnIndex = cursor.getColumnIndex(TIME_COLUMN)
            var timestamp = 0L
            var decibel = 0f
            if (timeColumnIndex != -1) {
                timestamp = cursor.getLong(timeColumnIndex)
            }
            val decibelColumnIndex = cursor.getColumnIndex(DB_LEVEL_COLUMN)
            if (decibelColumnIndex != -1) {
                decibel = cursor.getFloat(decibelColumnIndex)
            }
            records.add(0, Pair(timestamp, decibel))
        }
        cursor.close()
        db.close()
        return records
    }
}
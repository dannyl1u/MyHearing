package com.example.myhearing.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.myhearing.HeatmapActivity
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.heatmaps.WeightedLatLng
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MyHearingDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        const val DATABASE_NAME = "MyHearingDatabase"
        const val DATABASE_VERSION = 1
        const val TABLE_NAME = "MyHearingTable"

        const val ID_COLUMN = "id"
        const val TIME_COLUMN = "dateTime"
        const val DB_LEVEL_COLUMN = "dbLevel"
        const val COMMENT_COLUMN = "comment"
        const val LOCATION_COLUMN = "location"
        // Add more columns if needed
        // Delete the app's data in the phone's storage to be able to upgrade
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = "CREATE TABLE $TABLE_NAME (" +
                "$ID_COLUMN INTEGER PRIMARY KEY AUTOINCREMENT," +
                "$TIME_COLUMN REAL," +
                "$DB_LEVEL_COLUMN REAL," +
                "$COMMENT_COLUMN TEXT," +
                "$LOCATION_COLUMN TEXT)"
        // Add more columns if needed
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    }

    // INSERT example
    fun saveDataToDatabase(context: Context) {
        val insertQuery = "INSERT INTO $TABLE_NAME " +
                "(time, dbLevel, comment, location) " +
                "VALUES (?, ?, ?, ?)"
        val dbHelper = MyHearingDatabaseHelper(context)
        val db = dbHelper.writableDatabase
        val statement = db.compileStatement(insertQuery)

        // We can process data here and bind it to the statement
        // Fetch Time
        val currentTimeMillis = System.currentTimeMillis()

        statement.bindLong(1, currentTimeMillis)
        statement.bindDouble(2, 0.0)
        statement.bindString(3, "test comment")
        statement.bindString(4, "test location")

        statement.executeInsert()

        db.close()
    }

    // SELECT example
    fun fetchExerciseEntries(context: Context): ArrayList<MyHearingEntry> {
        val dbHelper = MyHearingDatabaseHelper(context)
        val db = dbHelper.readableDatabase
        val query = "SELECT * FROM $TABLE_NAME"
        val cursor = db.rawQuery(query, null)

        val entryList = ArrayList<MyHearingEntry>()

        while (cursor.moveToNext()) {
            val entry = MyHearingEntry(
                id = cursor.getInt(cursor.getColumnIndexOrThrow(MyHearingDatabaseHelper.ID_COLUMN)),
                dateTime = cursor.getString(cursor.getColumnIndexOrThrow(MyHearingDatabaseHelper.TIME_COLUMN)),
                dbLevel = cursor.getDouble(cursor.getColumnIndexOrThrow(MyHearingDatabaseHelper.DB_LEVEL_COLUMN)),
                comment = cursor.getString(cursor.getColumnIndexOrThrow(MyHearingDatabaseHelper.COMMENT_COLUMN)),
                location = cursor.getString(cursor.getColumnIndexOrThrow(MyHearingDatabaseHelper.LOCATION_COLUMN))
            )
            entryList.add(entry)
        }
        db.close()
        return entryList
    }

    // DELETE example
    fun deleteEntry(context: Context, entryId: String) {
        val dbHelper = MyHearingDatabaseHelper(context)
        val db = dbHelper.writableDatabase
        val query = "DELETE FROM $TABLE_NAME WHERE id = $entryId"

        val statement = db.compileStatement(query)
        statement.execute()
    }

    fun getRecordsSince(timestamp: Long): MutableList<Triple<Long, LatLng, Double>> {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME WHERE $TIME_COLUMN > ?", arrayOf(timestamp.toString()))
        val records = mutableListOf<Triple<Long, LatLng, Double>>()

        while (cursor.moveToNext()) {
            var timestamp = 0L
            val timeColumnIndex = cursor.getColumnIndex(TIME_COLUMN)
            if (timeColumnIndex != -1) {
                timestamp = cursor.getLong(timeColumnIndex)
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

            val locationRegex = Regex("(-?\\d+.\\d+),*(-?\\d+.\\d+)")
            val latLngPair = locationRegex.find(locationString)

            latLngPair?.let {
                val (lat, lng) = it.destructured
                val latLng = LatLng(
                    DecimalFormat("#.#######").format(lat).toDouble(),
                    DecimalFormat("#.#######").format(lng).toDouble()
                )

                records.add(Triple(timestamp, latLng, decibel.toDouble()))
            }
        }

        cursor.close()
        db.close()

        return records
    }

    fun getRecentDecibelRecords(): List<Pair<Long, Float>> {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME WHERE $TIME_COLUMN > ?", arrayOf((System.currentTimeMillis() - 20000).toString()))
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
            records.add(Pair(timestamp, decibel))
        }
        cursor.close()
        db.close()
        return records
    }

}
package com.example.myhearing.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class MyHearingDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "MyHearingDatabase"
        const val DATABASE_VERSION = 1
        const val TABLE_NAME = "MyHearingTable"

        const val ID_COLUMN = "id"
        const val DATE_TIME_COLUMN = "dateTime"
        const val DB_LEVEL_COLUMN = "dbLevel"
        const val COMMENT_COLUMN = "comment"
        const val LOCATION_COLUMN = "location"
        // Add more columns if needed
        // Delete the app's data in the phone's storage to be able to upgrade
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = "CREATE TABLE $TABLE_NAME (" +
                "$ID_COLUMN INTEGER PRIMARY KEY AUTOINCREMENT," +
                "$DATE_TIME_COLUMN TEXT," +
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
                "(dateTime, dbLevel, comment, location) " +
                "VALUES (?, ?, ?, ?)"
        val dbHelper = MyHearingDatabaseHelper(context)
        val db = dbHelper.writableDatabase
        val statement = db.compileStatement(insertQuery)

        // We can process data here and bind it to the statement
        statement.bindString(1, "test date and time")
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
                dateTime = cursor.getString(cursor.getColumnIndexOrThrow(MyHearingDatabaseHelper.DATE_TIME_COLUMN)),
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
}
package com.hackforsweden.touchmenot;

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.*

class DbHelper(context: Context,
               factory: SQLiteDatabase.CursorFactory?) :
    SQLiteOpenHelper(context, DATABASE_NAME,
        factory, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        val createLogTable = ("CREATE TABLE " +
                LOG_TABLE_NAME + "("
                + LOG_COLUMN_TIME + " INTEGER," +
                 LOG_COLUMN_TIMEFORMAT + " TEXT," +

                LOG_COLUMN_LOG_VALUE
                + " TEXT" + ")")
        db.execSQL(createLogTable)

        val historyTable = ("CREATE TABLE " +
                HISTORY_TABLE_NAME + "("
                + HISTORY_COLUMN_MAC + " TEXT," +
                HISTORY_COLUMN_DAY + " INTEGER " + ")")
        db.execSQL(historyTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $LOG_TABLE_NAME")
        db.execSQL("DROP TABLE IF EXISTS $HISTORY_TABLE_NAME")
        onCreate(db)
    }

    fun addLog(logValue: String) {
        val values = ContentValues()
        values.put(LOG_COLUMN_TIME, System.currentTimeMillis())
        values.put(LOG_COLUMN_TIMEFORMAT, SimpleDateFormat("yyyy-MM-dd HH:mm a").format(Date()))

        values.put(LOG_COLUMN_LOG_VALUE, logValue)

        val db = this.writableDatabase
        db.insert(LOG_TABLE_NAME, null, values)
        db.close()
    }

    fun addHistory(macAddress: String) {
        val values = ContentValues()
        val currentDate: String = SimpleDateFormat("dd", Locale.getDefault()).format(Date())
        values.put(HISTORY_COLUMN_MAC, macAddress)
        values.put(HISTORY_COLUMN_DAY,currentDate.toInt())
        val db = this.writableDatabase
        db.insert(HISTORY_TABLE_NAME, null, values)
        db.close()
    }


    fun getLogFile(logFile: File) {
        val db = this.readableDatabase
         val cursor =db.rawQuery("SELECT * FROM $LOG_TABLE_NAME", null)
        val writer = FileWriter(logFile)
        val myDeviceModel = Build.MODEL
        val myDeviceManufacturer = Build.MANUFACTURER
        val myDeviceBrand =Build.BRAND
        val myOS = Build.VERSION.RELEASE
        val mySDK = Build.VERSION.SDK_INT
        writer.append("Device Model : $myDeviceModel \n")
        writer.append("Device Brand : $myDeviceBrand \n")
        writer.append("Device Manufacturer  : $myDeviceManufacturer \n")
        writer.append("Device OS  : $myOS \n")
        writer.append("Device SDK  : $mySDK \n")
        cursor!!.moveToFirst()
         while (cursor.moveToNext()) {
             writer.append((cursor.getString(cursor.getColumnIndex(LOG_COLUMN_TIMEFORMAT))+" : "+cursor.getString(cursor.getColumnIndex(
                 LOG_COLUMN_LOG_VALUE
             ))))
              writer.append("\n\r")

         }
        writer.flush()
        writer.close()
        cursor.close()

        deleteAllLogs()
     }

    private fun deleteAllLogs(){
        val db = this.writableDatabase
        val deleteQuery = "DELETE from $LOG_TABLE_NAME"
        try {
            db.execSQL(deleteQuery)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "socialdistancelogs.db"
        const val LOG_TABLE_NAME = "logtable"
        const val HISTORY_TABLE_NAME = "historytable"
        const val LOG_COLUMN_TIME = "time"
        const val LOG_COLUMN_TIMEFORMAT = "timeformatted"
        const val LOG_COLUMN_LOG_VALUE = "logvalue"
        const val HISTORY_COLUMN_MAC = "macaddress"
        const val HISTORY_COLUMN_DAY = "day"

        private var instance: DbHelper? = null
        fun getInstance(context: Context): DbHelper
        {
            if(instance == null)
            {
                instance = DbHelper(context,null)
            }

            return instance!!
        }
    }

}


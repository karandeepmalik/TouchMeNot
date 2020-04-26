package com.hackforsweden.touchmenot

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.hackforsweden.touchmenot.DbHelper.Companion.HISTORY_TABLE_NAME

class HistoryUploadWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams)
{
    override fun doWork(): Result {
        try
        {
            val firestoreDb  = Firebase.firestore
            val db = DbHelper.instance.writableDatabase
            val cursor =db.rawQuery("SELECT * FROM ${DbHelper.HISTORY_TABLE_NAME}", null)
            cursor!!.moveToFirst()
            while (!cursor.isAfterLast)
            {
                val macTime = hashMapOf<String,String>(cursor.getString(cursor.getColumnIndex(DbHelper.HISTORY_COLUMN_MAC)) to
                        cursor.getString(cursor.getColumnIndex(DbHelper.HISTORY_COLUMN_DAY))+"-"+cursor.getString(cursor.getColumnIndex(DbHelper.HISTORY_COLUMN_MONTH)))

                firestoreDb.collection("macIDDateEntry")
                        .add(macTime)
                        .addOnSuccessListener { documentReference ->
                            log( "DocumentSnapshot added with ID: ${documentReference.id}",applicationContext) }
                        .addOnFailureListener { e ->
                            log("Error adding document $e", applicationContext) }
                cursor.moveToNext()
            }
            cursor.close()
            db.delete(HISTORY_TABLE_NAME,null,null)
            return Result.success()
        }
        catch (e: Exception)
        {
            return Result.retry()
        }
     }
}


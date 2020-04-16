package com.hackforsweden.touchmenot

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    companion object {
        var socialDistanceThreshold:Long = 1
        var socialTimeThreshold:Long = 1
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        try {


            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)
            setTitle(R.string.activity_main_title)
            val builder = StrictMode.VmPolicy.Builder()
            StrictMode.setVmPolicy(builder.build())
            val mediaStorageDir =
                File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "TouchMeNotLogs")

            findViewById<Button>(R.id.btnStartService).let {
                it.setOnClickListener {
                    log("Start Service Command Issued", this)
                    actionOnService(Actions.START)
                }
            }

            findViewById<Button>(R.id.btnStopService).let {
                it.setOnClickListener {
                    log("Stop Service Command Issued", this)
                    actionOnService(Actions.STOP)
                }
            }


       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
       {  // Only ask for these permissions on runtime when running Android 6.0 or higher
            when (ContextCompat.checkSelfPermission(
                baseContext,
                Manifest.permission.ACCESS_COARSE_LOCATION))
            {
                PackageManager.PERMISSION_DENIED -> (AlertDialog.Builder(this)
                    .setTitle("Runtime Permissions up ahead")
                    .setMessage(Html.fromHtml("<p>To find nearby bluetooth devices please click \"Allow\" on the runtime permissions popup.</p>" + "<p>For more info see <a href=\"http://developer.android.com/about/versions/marshmallow/android-6.0-changes.html#behavior-hardware-id\">here</a>.</p>"))
                    .setNeutralButton("Okay") { _, _ ->
                        if (ContextCompat.checkSelfPermission(
                                baseContext,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            ActivityCompat.requestPermissions(
                                this@MainActivity,
                                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                                1
                            )
                        }
                    }
                    .show()
                    .findViewById<View>(android.R.id.message) as TextView).movementMethod =
                    LinkMovementMethod.getInstance()       // Make the link clickable. Needs to be called after show(), in order to generate hyperlinks
                PackageManager.PERMISSION_GRANTED -> {
                }
            }

            if (ContextCompat.checkSelfPermission(
                   baseContext,
                   Manifest.permission.RECORD_AUDIO
               ) != PackageManager.PERMISSION_GRANTED) {
               ActivityCompat.requestPermissions(
                   this@MainActivity,
                   arrayOf(Manifest.permission.RECORD_AUDIO),
                   12)
               }
           }

        val discoverableIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 3600) }
            startActivity(discoverableIntent)

            val spinnerDistance =
                findViewById<View>(R.id.sp_distance_monitoring) as Spinner

            // Create an ArrayAdapter using the string array and a default spinner layout
            val distanceAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.distance_array, android.R.layout.simple_spinner_item
            )

            // Specify the layout to use when the list of choices appears
            distanceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            // Apply the adapter to the spinner
            spinnerDistance.adapter = distanceAdapter
            spinnerDistance.onItemSelectedListener = object : OnItemSelectedListener {
                override fun onItemSelected(
                    parentView: AdapterView<*>,
                    selectedItemView: View,
                    position: Int,
                    id: Long
                ) {
                    socialDistanceThreshold =
                        parentView.getItemAtPosition(position).toString().toLong()
                }

                override fun onNothingSelected(parentView: AdapterView<*>?) {
                }
            }


            val spinnerTime =
                findViewById<View>(R.id.sp_time_monitoring) as Spinner

            // Create an ArrayAdapter using the string array and a default spinner layout
            val timeAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.time_array, android.R.layout.simple_spinner_item
            )

            // Specify the layout to use when the list of choices appears
            timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            // Apply the adapter to the spinner
            spinnerTime.adapter = timeAdapter
            spinnerTime.onItemSelectedListener = object : OnItemSelectedListener {
                override fun onItemSelected(
                    parentView: AdapterView<*>,
                    selectedItemView: View,
                    position: Int,
                    id: Long
                ) {
                    socialTimeThreshold =
                        parentView.getItemAtPosition(position).toString().toLong()
                }

                override fun onNothingSelected(parentView: AdapterView<*>?) {
                }
            }

            findViewById<Button>(R.id.btnSendLogs).let {
                it.setOnClickListener {

                    if (mediaStorageDir.exists() || !mediaStorageDir.mkdirs()) {
                        val gpxfile = File(mediaStorageDir, "logfile.txt")

                        doAsync {
                            // do your background thread task
                            DbHelper.getInstance(applicationContext).getLogFile(gpxfile)

                            uiThread {
                                // use result here if you want to update ui
                                sendMail(mediaStorageDir.absolutePath)
                            }
                        }
                    }

                }
            }


        }
        catch (exc :Exception)
        {
            log(exc.message +"\n"+ exc.stackTrace , this)
        }
    }

    private fun actionOnService(action: Actions) {
        Intent(this, CheckForDistanceService::class.java).also {
            it.action = action.name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                log("Starting/Stopping the service in >=26 Mode",this)
                startForegroundService(it)
                return
            }
            log("Starting/Stopping the service in < 26 Mode",this)
            startService(it)
        }
    }

    private fun sendMail(path:String?){
        val fileLocation = File(path, "logfile.txt")
        val filePath = Uri.fromFile(fileLocation)
        val emailIntent = Intent(Intent.ACTION_SEND)
        // set the type to 'email'
        emailIntent.type = "vnd.android.cursor.dir/email"
        val to = arrayOf("ppdlteamrx@gmail.com")
        emailIntent.putExtra(Intent.EXTRA_EMAIL, to)
        // the attachment
        emailIntent.putExtra(Intent.EXTRA_STREAM, filePath)
        // the mail subject
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Reporting Log File")
        startActivity(Intent.createChooser(emailIntent, "Send email..."))
    }


}

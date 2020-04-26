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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    companion object {
        var socialDistanceThreshold:Long = 1
        var socialTimeThreshold:Long = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setTitle(R.string.activity_main_title)
        val builder = StrictMode.VmPolicy.Builder()
        StrictMode.setVmPolicy(builder.build())
        setupMainActivityUi()
        askForFineLocationPermission()
        askForBackGroundLocationPermission()
        askForRecordAudioPermission()
        setupHistoryUploadWorker()



    }

    private fun setupMainActivityUi()
    {
        setupStartMonitoringButton()
        setupStopMonitoringButton()
        setupSocialTimeSpinner()
        setupSocialDistanceSpinner()
    }

    private fun setupStartMonitoringButton()
    {
        findViewById<Button>(R.id.btnStartService).let {
            it.setOnClickListener {
                log("Start Service Command Issued", this)
                actionOnService(Actions.START)
            }
        }
    }

    private fun setupStopMonitoringButton()
    {
        findViewById<Button>(R.id.btnStopService).let {
            it.setOnClickListener {
                log("Stop Service Command Issued", this)
                actionOnService(Actions.STOP)
            }
        }
    }
    private fun setupSocialDistanceSpinner() {
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
    }

    private fun setupSocialTimeSpinner(){

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

            override fun onNothingSelected(parentView: AdapterView<*>?) {}
        }
    }

    private fun askForFineLocationPermission()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {  // Only ask for these permissions on runtime when running Android 6.0 or higher

            if (ContextCompat.checkSelfPermission(
                    baseContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    1
                )
            }
        }
    }

    private fun askForBackGroundLocationPermission()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    baseContext,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    13
                )
            }

        }
    }

    private fun askForRecordAudioPermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            if (ContextCompat.checkSelfPermission(
                baseContext,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    12
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        if(requestCode== 1){
            val permCondition =
                grantResults.size == 1 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED;

            if(permCondition){
                askForRecordAudioPermission()
            }

        }else  if(requestCode== 13){
            val permCondition =
                grantResults.size == 1 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED;

            if(permCondition)
            {
            }

        }
        else if (requestCode ==12)
        {
            val permCondition =
                grantResults.size == 1 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if(permCondition)
            {
                val discoverableIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 3600) }

                startActivity(discoverableIntent)
            }
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

    private fun setupHistoryUploadWorker()
    {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val historyUploadRequest =  PeriodicWorkRequestBuilder<HistoryUploadWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                PeriodicWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance().enqueue(historyUploadRequest)

    }

    private fun sendMail()
    {
        val mediaStorageDir =
            File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "TouchMeNotLogs")
        if (mediaStorageDir.exists() || !mediaStorageDir.mkdirs()) {
            val gpxfile = File(mediaStorageDir, "logfile.txt")

            doAsync {
                // do your background thread task
                DbHelper.instance.getLogFile(gpxfile)

                uiThread {
                    // use result here if you want to update ui
                    val fileLocation = File(mediaStorageDir.absolutePath, "logfile.txt")
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
        }

    }

    private fun sendHistory()
    {
        val mediaStorageDir =
            File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "TouchMeNotHistory")
        if (mediaStorageDir.exists() || !mediaStorageDir.mkdirs()) {
            val historyFile = File(mediaStorageDir, "historyFile.txt")

            doAsync {
                // do your background thread task
                DbHelper.instance.getHistoryFile(historyFile)

                uiThread {
                    // use result here if you want to update ui
                    val fileLocation = File(mediaStorageDir.absolutePath, "historyFile.txt")
                    val filePath = Uri.fromFile(fileLocation)
                    val emailIntent = Intent(Intent.ACTION_SEND)
                    // set the type to 'email'
                    emailIntent.type = "vnd.android.cursor.dir/email"
                    val to = arrayOf("ppdlteamrx@gmail.com")
                    emailIntent.putExtra(Intent.EXTRA_EMAIL, to)
                    // the attachment
                    emailIntent.putExtra(Intent.EXTRA_STREAM, filePath)
                    // the mail subject
                    emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Reporting History File")
                    startActivity(Intent.createChooser(emailIntent, "Send email..."))
                }
            }
        }

    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_send_logs -> { sendMail()
            true
        }

        R.id.action_device_exclusions-> {
            Intent(this, BluetoothScannedDevices::class.java).also {

                startActivity(it)}
            true
        }

        R.id.action_send_history-> {
            sendHistory()
            true
        }

        else -> {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

}

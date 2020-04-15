package com.hackforsweden.touchmenot


import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow


class CheckForDistanceService : Service() {

    companion object{
        private var TAG = "CheckForDistanceService"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val notificationChannelId = "CheckForDistance"
    private var countMap =  mutableMapOf<String,Int>()
    private var lastDetectedMap =  mutableMapOf<String,Long>()

    override fun onBind(intent: Intent): IBinder? {
        // We don't provide binding, so return null
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
    {
        if (intent != null)
        {
            val action = intent.action
            Log.d(TAG,"Service Start initiated. Using an intent with action $action")
            when (action) {
                Actions.START.name -> startService()
                Actions.STOP.name -> stopService()
                else -> Log.d(TAG,"This should never happen. No action in the received intent")
            }
        }
        else
        {
            Log.d(TAG,"Service Received null intent")
        }
        // by returning this we make sure the service is restarted if the system kills the service
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG,"The service has been created".toUpperCase())
        val notification = createNotification()
        startForeground(1, notification)
    }

    override fun onDestroy()
    {
        super.onDestroy()
        Log.d(TAG,"The service has been destroyed".toUpperCase())
        Toast.makeText(this, "Service destroyed", Toast.LENGTH_SHORT).show()
    }

    private fun startService()
    {
        if (isServiceStarted) return
        Log.d(TAG,"Starting the foreground service task")
        Toast.makeText(this, "Service starting its task", Toast.LENGTH_SHORT).show()
        isServiceStarted = true
        //populateDevicePowerMap()
        // we need this lock so our service gets not affected by Doze Mode
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CheckForDistanceService::lock").apply {
                    acquire(200)
                }
            }
        // we're starting a loop in a coroutine
        GlobalScope.launch(Dispatchers.IO)
        {
            while (isServiceStarted)
            {
                launch(Dispatchers.IO)
                {
                    detectBluetoothDevices()
                }
                delay(15000)
            }
            Log.d(TAG,"Service is no more started.End of the loop for the service")
        }

    }

    private fun stopService()
    {
        Log.d(TAG,"Stop Service Triggered. Cleaning of resources will happen now")
        Toast.makeText(this, "Service stopping", Toast.LENGTH_SHORT).show()
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            Log.d(TAG,"Service is being stopped without being started: ${e.message}")
        }


        this.unregisterReceiver(btReciever)

        isServiceStarted = false
    }


    private fun createNotification(): Notification
    {
        // depending on the Android API that we're dealing with we will have
        // to use a specific method to create the notification

        // For Oreo and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;

            val uri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE
                    + "://" + packageName + "/raw/alarm");
            // Creating an Audio Attribute
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            val channel = NotificationChannel(
                notificationChannelId,
                "Service notifications channel",
                NotificationManager.IMPORTANCE_HIGH
            ).let {
                it.description = "Service channel"
                it.enableLights(true)
                it.lightColor = Color.RED
                it.enableVibration(true)
                it.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
                it.importance=NotificationManager.IMPORTANCE_HIGH
                it.setSound(uri, audioAttributes)
                it
            }

            notificationManager.createNotificationChannel(channel)
            Log.d(TAG,"Created Notification Channel")
        }

        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, 0)
        }

        val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
            this,
            notificationChannelId
        ) else Notification.Builder(this)

        Log.d(TAG,"Returning the appropriate  Notification Builder")
        return builder
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.monitoring_social_distance))
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(Notification.PRIORITY_HIGH) // for under android 26 compatibility
            .build()
    }

    fun calculateDistance(rssi:Double, txPower :Double ) :Double
    {
        Log.d(TAG,"Calculating the distance")
        /*
         * RSSI = TxPower - 10 * n * lg(d)
         * n = 2 (in free space)
         *
         * d = 10 ^ ((TxPower - RSSI) / (10 * n))
         */

        return 10.0.pow((txPower - rssi) / (10 * 2))
    }

    fun detectBluetoothDevices()
    {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter!=null) {
            Log.d("Bluetooth Adapter","Bluetooth Adapter is not enabled")
            if (!bluetoothAdapter!!.isEnabled)
            {

                bluetoothAdapter!!.enable()
                Log.d("Bluetooth Adapter","Bluetooth Adapter is not enabled")
            }

            // Register for broadcasts when a device is discovered.
            var filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            // Register for broadcasts when discovery has finished
            this.registerReceiver(btReciever, filter)
            filter = IntentFilter(
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED
            )
            this.registerReceiver(btReciever, filter)

            if (bluetoothAdapter!!.isDiscovering) {
                Log.d(TAG,"Stop Discovery of devices if already discovering")
                bluetoothAdapter!!.cancelDiscovery()
            }
            Log.d(TAG,"Fresh start discovery of devices if already discovering")
            bluetoothAdapter!!.startDiscovery()
        }
        else
        {
            Log.d("Bluetooth Adapter","Bluetooth Adapter is null")
        }

    }
    // Create a BroadcastReceiver for ACTION_FOUND.
    private val btReciever: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                Log.d(TAG, "DEVICELIST Bluetooth device found\n")
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                // Create a new device item
                val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                if ( device!=null) {
                    if(lastDetectedMap.containsKey(device.address))
                    {
                        val timeDiff = lastDetectedMap[device.address]!! - System.currentTimeMillis()
                        if( timeDiff > 15000)
                        {
                            // Since the last time this device was detected is greater than 15 secs
                            // Stop tracking the count
                            val address = device.address
                            Log.d(TAG,"Stop tracking count of $address since it was lastly discovered > 15 secs before")
                            if(countMap.containsKey(device.address))
                                countMap.remove(device.address)
                        }
                    }

                    lastDetectedMap[device.address] = System.currentTimeMillis()
                }
                val txPower = 4.0
                var distance = calculateDistance(rssi.toDouble(),txPower)
                if (name !=null)
                    Log.d("Detected Device ", name!!)
                Log.d(TAG,"Rssi $rssi")
                Log.d(TAG, "Distance Measured $distance")
                distance /= 1700

                if (device != null) {
                    if (distance < MainActivity.socialDistanceThreshold) {

                        if (countMap.containsKey(device.address)) {

                            var count: Int? = countMap[device.address]
                            if (count != null) {
                                count += 1
                            }
                            val address = device.address
                            Log.d(
                                TAG,
                                "Increment the  count of $address  by 1. The new count is $count"
                            )
                            countMap[device.address] = count!!
                            Log.d(TAG, "Device: $device.address Count: $count")
                        } else {
                            countMap[device.address] = 1
                            Log.d(TAG, "Newly discovered Device: $device.address Count: 1")
                        }

                    } else {
                        Log.d(
                            TAG,
                            "This device " + device?.address + " is obeying social distancing so stop tracking its count"
                        )
                        if (countMap.containsKey(device!!.address)) {
                            countMap.remove(device.address)
                        }
                    }
                }

                for (value in countMap.values)
                {
                    if (value >= MainActivity.socialTimeThreshold * 4) {

                        Log.d(TAG, "This device "+device?.address+" has breached social distance for social distancing time "+MainActivity.socialTimeThreshold + " minutes. Issuing notification")
                        val uri = Uri.parse(
                            ContentResolver.SCHEME_ANDROID_RESOURCE
                                    + "://" + packageName + "/raw/alarm"
                        );
                        val builder = NotificationCompat.Builder(
                            this@CheckForDistanceService,
                            notificationChannelId
                        )
                            .setSmallIcon(R.drawable.ic_notification)
                            .setColor(resources.getColor(R.color.colorAccent))
                            .setContentTitle("Alert")
                            .setContentText("Please maintain enough social distance")
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setSound(uri)
                            // Set the intent that will fire when the user taps the notification
                            .setAutoCancel(true)
                            .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))

                        Log.d(TAG, "Breach of  social distance. Creating Notification")


                        with(NotificationManagerCompat.from(this@CheckForDistanceService)) {
                            // notificationId is a unique int for each notification that you must define
                            notify(12345, builder.build())
                        }
                        break;
                    }

                }



              /*  if (distance <MainActivity.socialDistanceThreshold)
                {
                    val uri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE
                            + "://" + packageName + "/raw/alarm");
                    val builder = NotificationCompat.Builder(this@CheckForDistanceService, notificationChannelId)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setColor(resources.getColor(R.color.colorAccent))
                        .setContentTitle("Alert")
                        .setContentText("Please maintain enough social distance")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setSound(uri)
                        // Set the intent that will fire when the user taps the notification
                        .setAutoCancel(true)
                        .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))

                    Log.d(TAG, "Breach of  social distance. Creating Notification")


                    with(NotificationManagerCompat.from(this@CheckForDistanceService)) {
                        // notificationId is a unique int for each notification that you must define
                        notify(12345, builder.build())
                    }

                }*/

            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action)
            {
                Log.d(TAG, "Scanning done..")
                Toast.makeText(context, "Scanning done..", Toast.LENGTH_SHORT).show()
            }

        }
    }


}

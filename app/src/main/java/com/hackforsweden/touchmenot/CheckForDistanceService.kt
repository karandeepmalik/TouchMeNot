package com.hackforsweden.touchmenot


import android.app.*
import android.app.NotificationManager.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import android.os.*
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.pow


class CheckForDistanceService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val notificationChannelId = "TouchMeNot"
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
            log("Service Start initiated. Using an intent with action $action", this)
            when (action) {
                Actions.START.name -> startService()
                Actions.STOP.name -> stopService()
                else -> log("This should never happen. No action in the received intent", this)
            }
        }
        else
        {
            log("Service Received null intent", this)
        }
        // by returning this we make sure the service is restarted if the system kills the service
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        log("The service has been created".toUpperCase(Locale.ROOT), this)
        val notification = createNotification()
        startForeground(1, notification)
    }

    override fun onDestroy()
    {
        super.onDestroy()
        log("The service has been destroyed".toUpperCase(Locale.ROOT), this)
        Toast.makeText(this, "Service destroyed", Toast.LENGTH_SHORT).show()
    }

    private fun startService()
    {
        try
        {
            if (isServiceStarted) return
            log("Starting the foreground service task", this)
            Toast.makeText(this, "Service starting its task", Toast.LENGTH_SHORT).show()
            isServiceStarted = true
            //populateDevicePowerMap()
            // we need this lock so our service gets not affected by Doze Mode
            wakeLock =
                (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                    newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "CheckForDistanceService::lock"
                    ).apply {
                        acquire()
                    }
                }
            // we're starting a loop in a coroutine
            GlobalScope.launch(Dispatchers.IO)
            {
                while (isServiceStarted) {
                    launch(Dispatchers.IO)
                    {
                        detectBluetoothDevices()
                    }
                    delay(15000)
                }
                log("Service is no more started.End of the loop for the service",this@CheckForDistanceService)
            }
        }

        catch (exc :Exception)
        {
            log(exc.message +"\n"+ exc.stackTrace , this)
        }


    }

    private fun stopService()
    {
        log("Stop Service Triggered. Cleaning of resources will happen now", this)
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
            log("Service is being stopped without being started: ${e.message}", this)
        }

        isServiceStarted = false
    }


    private fun createNotification(): Notification
    {
        // depending on the Android API that we're dealing with we will have
        // to use a specific method to create the notification

        // For Oreo and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val uri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE
                    + "://" + packageName + "/raw/alarm")
            // Creating an Audio Attribute
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            val channel = NotificationChannel(
                notificationChannelId,
                "Service notifications channel",
                IMPORTANCE_HIGH
            ).let {
                it.description = "Service channel"
                it.enableLights(true)
                it.lightColor = Color.RED
                it.enableVibration(true)
                it.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
                it.importance= IMPORTANCE_HIGH
                it.setSound(uri, audioAttributes)
                it
            }

            notificationManager.createNotificationChannel(channel)
            log("Created Notification Channel", this)
        }

        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, 0)
        }

        val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
            this,
            notificationChannelId
        ) else Notification.Builder(this)

       log("Returning the appropriate  Notification Builder", this)
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
        log("Calculating the distance", this)
        /*
         * RSSI = TxPower - 10 * n * lg(d)
         * n = 2 (in free space)
         *
         * d = 10 ^ ((TxPower - RSSI) / (10 * n))
         */

        return 10.0.pow((txPower - rssi) / (10 * 2))
    }

    private fun detectBluetoothDevices()
    {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter!=null) {
            log("Check if Bluetooth Adapter is enabled",this)
            if (!bluetoothAdapter!!.isEnabled)
            {

                bluetoothAdapter!!.enable()
                log("Bluetooth Adapter is enabled now", this)
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
                log("Stop Discovery of devices if already discovering", this)
                bluetoothAdapter!!.cancelDiscovery()
            }
            log("Fresh start discovery of devices", this)
            bluetoothAdapter!!.startDiscovery()
        }
        else
        {
            log("Bluetooth Adapter is null", this)
        }

    }
    // Create a BroadcastReceiver for ACTION_FOUND.
    private val btReciever: BroadcastReceiver = object : BroadcastReceiver()
    {

        override fun onReceive(context: Context, intent: Intent) {
            try
            {

                val action = intent.action
                if (BluetoothDevice.ACTION_FOUND == action)
                {
                    log("DEVICELIST Bluetooth device found\n", context)
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                    if(DbHelper.getInstance(context).checkDeviceIdExist(device.address)){
                        return
                    }

                    // Create a new device item
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                    val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                    if (device != null) {
                        if (lastDetectedMap.containsKey(device.address)) {
                            val timeDiff =
                                lastDetectedMap[device.address]!! - System.currentTimeMillis()
                            if (timeDiff > 15000) {
                                // Since the last time this device was detected is greater than 15 secs
                                // Stop tracking the count
                                val address = device.address
                                log(
                                    "Stop tracking count of $address since it was lastly discovered > 15 secs before",
                                    context
                                )
                                if (countMap.containsKey(device.address))
                                    countMap.remove(device.address)
                            }
                        }

                        lastDetectedMap[device.address] = System.currentTimeMillis()

                    }
                    val txPower = 4.0
                    var distance = calculateDistance(rssi.toDouble(), txPower)
                    if (name != null)
                        log("Detected Device $name", context)
                    log("Rssi $rssi", context)
                    log("Distance Measured $distance", context)
                    distance /= 2500

                    if (device != null) {
                        if (distance < MainActivity.socialDistanceThreshold) {

                            if (countMap.containsKey(device.address))
                            {
                                var count = countMap[device.address]!!
                                count += 1
                                val address = device.address
                                log(
                                    "Increment the  count of $address  by 1. The new count is $count",
                                    context
                                )
                                countMap[device.address] = count
                                log("Device: $device.address Count: $count", context)
                            }
                            else
                            {
                                countMap[device.address] = 1
                                log("Newly discovered Device: $device.address Count: 1", context)
                            }

                        }
                        else
                        {
                            log(
                                "This device " + device.address + " is obeying social distancing so  dont track its count",
                                context
                            )
                            if (countMap.containsKey(device.address)) {
                                countMap.remove(device.address)
                            }
                        }
                    }

                    for ( detectedDevice in countMap)
                    {
                        if (detectedDevice.value >= MainActivity.socialTimeThreshold * 4)
                        {

                            log(
                                "This device " +  detectedDevice.key + " has breached social distance for social distancing time " + MainActivity.socialTimeThreshold + " minutes. Issuing notification",
                                context
                            )
                            DbHelper.getInstance(context).addHistory(detectedDevice.key)
                            val uri = Uri.parse(
                                ContentResolver.SCHEME_ANDROID_RESOURCE
                                        + "://" + packageName + "/raw/alarm"
                            )
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

                            log("Breach of  social distance. Creating Notification", context)


                            with(NotificationManagerCompat.from(this@CheckForDistanceService)) {
                                // notificationId is a unique int for each notification that you must define
                                notify(12345, builder.build())
                            }

                            if (bluetoothAdapter!!.isDiscovering) {
                                log("Stop Discovery of devices if already discovering", this@CheckForDistanceService)
                                bluetoothAdapter!!.cancelDiscovery()
                            }
                            break
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

                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
                    log("Scanning done..", context)
                    Toast.makeText(context, "Scanning done..", Toast.LENGTH_SHORT).show()
                }

            }
            catch (exc :Exception)
            {
                log(exc.message +"\n"+ exc.stackTrace , this@CheckForDistanceService)
            }
        }

    }


}

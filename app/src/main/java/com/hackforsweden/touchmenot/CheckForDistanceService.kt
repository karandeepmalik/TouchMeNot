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
import org.jetbrains.anko.runOnUiThread
import java.util.*
import kotlin.math.pow


class CheckForDistanceService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val notificationChannelId = "TouchMeNot"
    private var deviceAndSocialDistanceViolationCount =  mutableMapOf<String,Int>()
    private var lastDetectedDeviceAndTimestamp =  mutableMapOf<String,Long>()
    private val kalmanFilter = KalmanFilter()
    private val bluetoothDiscoveryPeriod = 15000L


    private val apiKeyOfUltraSoundLib = "EH0GHbslb0pNWAxPf57qA6n23w4Zgu5U"

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

        if (isServiceStarted) return
        log("Starting the foreground service task", this)
        Toast.makeText(this, "Service starting its task", Toast.LENGTH_SHORT).show()
        isServiceStarted = true


        // we need this lock so our service gets not affected by Doze Mode
        getWakeLockToPreventMobileIntoDozeMode();

        GlobalScope.launch(Dispatchers.IO)
        {
            while (isServiceStarted) {
                detectBluetoothDevices()
                delay(bluetoothDiscoveryPeriod)
            }
        }

        log("Service is no more started.End of the loop for the service",this@CheckForDistanceService)

    }

    private fun getWakeLockToPreventMobileIntoDozeMode()
    {
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "CheckForDistanceService::lock"
                ).apply {
                    acquire()
                }
            }
    }

    private fun stopService()
    {
        isServiceStarted = false
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


    }


    private fun createNotification(): Notification
    {

        // For Oreo and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val customSoundUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE
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
                it.setSound(customSoundUri, audioAttributes)
                it
            }

            notificationManager.createNotificationChannel(channel)
            log("Created Notification Channel", this)
        }

        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, 0)
        }

        val builder: Notification.Builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
            this,
            notificationChannelId)
            else
                Notification.Builder(this)

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

        return kalmanFilter.filter(10.0.pow((txPower - rssi) / (10 * 2)),0.0)
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

            registerBluetoothDiscoveryReceivers()

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

    private fun registerBluetoothDiscoveryReceivers()
    {
        // Register for broadcasts when a device is discovered.
        var filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        // Register for broadcasts when discovery has finished
        this.registerReceiver(btDeviceDiscoveryStatusReciever, filter)
        filter = IntentFilter(
            BluetoothAdapter.ACTION_DISCOVERY_FINISHED
        )
        this.registerReceiver(btDeviceDiscoveryStatusReciever, filter)

        // Register for broadcasts when a device is discovered.
        val discFilter = IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
        // Register for broadcasts when discovery has finished
        this.registerReceiver(discReciever, discFilter)

    }
    // Create a BroadcastReceiver to check if device is discoverable
    private val discReciever: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            val action = intent.getIntExtra("BluetoothAdapter.EXTRA_SCAN_MODE", 1)
            if (BluetoothAdapter.SCAN_MODE_NONE == action || BluetoothAdapter.SCAN_MODE_CONNECTABLE == action) {
                val discoverableIntent: Intent =
                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                        putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 3600)
                    }
                startActivity(discoverableIntent)
            }
        }
    }

    private val btDeviceDiscoveryStatusReciever: BroadcastReceiver = object : BroadcastReceiver()
    {

        override fun onReceive(context: Context, intent: Intent)
        {
            val action = intent.action
            if (BluetoothDevice.ACTION_FOUND == action)
            {
                log("DEVICELIST Bluetooth device found\n", context)
                val detectedDevice =
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                GlobalScope.launch(Dispatchers.IO)
                {
                   // Do not take any action if this is a device from filtered list
                   if(!DbHelper.instance.deviceIdExistsInFilteredDeviceList(detectedDevice!!.address))
                   {
                       val detectedDeviceRSSI =
                           intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                       log("Rssi $detectedDeviceRSSI", context)

                       val detectedDeviceName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                       if (detectedDeviceName != null)
                           log("Detected Device $detectedDeviceName", context)

                       if (!discoveredInLastDiscovery(detectedDevice)) {
                           stopTrackingViolationCountOfDevice(detectedDevice)
                       }
                       lastDetectedDeviceAndTimestamp[detectedDevice.address] =
                           System.currentTimeMillis()

                       val approxTxPowerOfDetectedDevice = 4.0
                       var distanceFromCurrentDevice = calculateDistance(
                           detectedDeviceRSSI.toDouble(),
                           approxTxPowerOfDetectedDevice
                       )
                       log("Distance Measured $distanceFromCurrentDevice", context)

                       distanceFromCurrentDevice /= 2500

                       if (distanceFromCurrentDevice < MainActivity.socialDistanceThreshold)
                       {
                           incrementViolationCountOfDevice(detectedDevice);
                       }
                       else
                       {
                           log(
                               "This device " + detectedDevice.address + " is obeying social distancing so  dont track its count",
                               context
                           )
                           stopTrackingViolationCountOfDevice(detectedDevice)
                       }
                       raiseAlertIfAnyDetectedDeviceHasExceededViolationsForGivenTimeLimits()

                   }
                   else
                   {
                       log("Filtered Device Encountered :- " + detectedDevice.address, context)
                   }
                }


            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
                log("Scanning done..", context)
                Toast.makeText(context, "Scanning done..", Toast.LENGTH_SHORT).show()
            }


        }

    }

    private fun discoveredInLastDiscovery(detectedDevice: BluetoothDevice) :Boolean
    {
        if (lastDetectedDeviceAndTimestamp.containsKey(detectedDevice.address)) {
            val timeSinceLastDetection =
                lastDetectedDeviceAndTimestamp[detectedDevice.address]!! - System.currentTimeMillis()
            if (timeSinceLastDetection > bluetoothDiscoveryPeriod) {

                return false;
            }
        }

        return true
    }

    private  fun incrementViolationCountOfDevice(detectedDevice: BluetoothDevice)
    {
        if (deviceAndSocialDistanceViolationCount.containsKey(detectedDevice.address))
        {
            var count = deviceAndSocialDistanceViolationCount[detectedDevice.address]!!
            count += 1
            val address = detectedDevice.address
            log(
                "Increment the  count of $address  by 1. The new count is $count",
                this@CheckForDistanceService
            )
            deviceAndSocialDistanceViolationCount[detectedDevice.address] = count
            log("Device: " + detectedDevice.address + " Count: $count", this@CheckForDistanceService)
        }
        else
        {
            deviceAndSocialDistanceViolationCount[detectedDevice.address] = 1
            log(
                "Newly discovered Device: " + detectedDevice.address + "Count: 1",
                this@CheckForDistanceService
            )
        }

    }

    private fun stopTrackingViolationCountOfDevice(detectedDevice: BluetoothDevice)
    {
        if (deviceAndSocialDistanceViolationCount.containsKey(detectedDevice.address)) {
            deviceAndSocialDistanceViolationCount.remove(detectedDevice.address)
        }
    }

    private fun raiseAlertIfAnyDetectedDeviceHasExceededViolationsForGivenTimeLimits()
    {
        for (detectedDevice in deviceAndSocialDistanceViolationCount)
        {
            if ((detectedDevice.value >= MainActivity.socialTimeThreshold * 60000/bluetoothDiscoveryPeriod) ) {

                log(
                    "This device " + detectedDevice.key + " has breached social distance for social distancing time " + MainActivity.socialTimeThreshold + " minutes. Issuing notification",
                    this@CheckForDistanceService
                )
                DbHelper.instance.addHistory(detectedDevice.key)
                runOnUiThread()
                {
                    raiseAlert()

                    if (bluetoothAdapter!!.isDiscovering) {
                        log(
                            "Stop Discovery of devices if already discovering",
                            this@CheckForDistanceService
                        )
                        bluetoothAdapter!!.cancelDiscovery()
                    }
                }

            }

        }
    }

    private fun raiseAlert()
    {
        val customSoundUri = Uri.parse(
            ContentResolver.SCHEME_ANDROID_RESOURCE
                    + "://" + packageName + "/raw/alarm"
        )
        val notificationCompatBuilder = NotificationCompat.Builder(
            this@CheckForDistanceService,
            notificationChannelId
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(resources.getColor(R.color.colorAccent))
            .setContentTitle("Alert")
            .setContentText("Please maintain enough social distance")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSound(customSoundUri)
            // Set the intent that will fire when the user taps the notification
            .setAutoCancel(true)
            .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))

        log("Breach of  social distance. Creating Notification", this@CheckForDistanceService)


        with(NotificationManagerCompat.from(this@CheckForDistanceService)) {
            // notificationId is a unique int for each notification that you must define
            notify(12345, notificationCompatBuilder.build())
        }
    }


}

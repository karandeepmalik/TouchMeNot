package com.hackforsweden.touchmenot

import android.Manifest
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.DialogInterface
import android.text.Html
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hackforsweden.touchmenot.MainActivity


class CheckForDistanceService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var deviceItemList = ArrayList<DeviceItem>()
    private var isServiceStarted = false
    private var devicePowerMap = HashMap<String,Double>()
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val notificationChannelId = "CheckForDistanceService CHANNEL"
    override fun onBind(intent: Intent): IBinder? {
        // We don't provide binding, so return null
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            startService()

        } else {

        }
        // by returning this we make sure the service is restarted if the system kills the service
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        log("The service has been created".toUpperCase())
        var notification = createNotification()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        log("The service has been destroyed".toUpperCase())
        Toast.makeText(this, "Service destroyed", Toast.LENGTH_SHORT).show()
    }

    private fun startService() {
        if (isServiceStarted) return
        log("Starting the foreground service task")
        Toast.makeText(this, "Service starting its task", Toast.LENGTH_SHORT).show()
        isServiceStarted = true
        populateDevicePowerMap()
        // we need this lock so our service gets not affected by Doze Mode
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CheckForDistanceService::lock").apply {
                    acquire()
                }
            }

        // we're starting a loop in a coroutine
        GlobalScope.launch(Dispatchers.IO) {
            while (isServiceStarted) {
                launch(Dispatchers.IO) {
                    detectBluetoothDevices()
                    //getRSSI
                    //calculate Distance
                    // sendNotification
                }
                delay(30000)
            }
            log("End of the loop for the service")
        }
    }

    private fun stopService() {
        log("Stopping the foreground service")
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
            log("Service stopped without being started: ${e.message}")
        }
        isServiceStarted = false
    }



    private fun createNotification(): Notification {


        // depending on the Android API that we're dealing with we will have
        // to use a specific method to create the notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
            val channel = NotificationChannel(
                notificationChannelId,
                "Endless Service notifications channel",
                NotificationManager.IMPORTANCE_HIGH
            ).let {
                it.description = "Endless Service channel"
                it.enableLights(true)
                it.lightColor = Color.RED
                it.enableVibration(true)
                it.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
                it
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, 0)
        }

        val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
            this,
            notificationChannelId
        ) else Notification.Builder(this)

        return builder
            .setContentTitle("CheckForDistance")
            .setContentText("This is your favorite CheckforDistance Service working")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setTicker("Ticker text")
            .setPriority(Notification.PRIORITY_HIGH) // for under android 26 compatibility
            .build()
    }

    fun calculateDistance(rssi:Double, txPower :Double ) :Double
    {
        /*
         * RSSI = TxPower - 10 * n * lg(d)
         * n = 2 (in free space)
         *
         * d = 10 ^ ((TxPower - RSSI) / (10 * n))
         */

        return Math.pow(10.0, (txPower  - rssi) / (10 * 2))
    }

    fun detectBluetoothDevices()
    {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (!bluetoothAdapter!!.isEnabled())
        {
            bluetoothAdapter!!.enable()
        }


        // Register for broadcasts when a device is discovered.
        var filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        // Register for broadcasts when discovery has finished
        this.registerReceiver(btReciever, filter)
        filter = IntentFilter(
            BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        this.registerReceiver(btReciever, filter)

        if (bluetoothAdapter!!.isDiscovering) {
            bluetoothAdapter!!.cancelDiscovery()
        }
        bluetoothAdapter!!.startDiscovery()

    }
    // Create a BroadcastReceiver for ACTION_FOUND.
    private val btReciever: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                Log.d("DEVICELIST", "Bluetooth device found\n")
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                // Create a new device item
                val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                Log.d("DEVICELIST info", device.name+"$rssi  $name\n")
                val newDevice = DeviceItem(device.name, device.address, rssi.toDouble())
                deviceItemList.add(newDevice)

                // Add it to our adapter

            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action)
            {
                Log.d("DEVICELIST ", "Scanning done..")
                Toast.makeText(context, "Scanning done..", Toast.LENGTH_SHORT).show()
                for (deviceItem in deviceItemList)
                {
                    val rssi = deviceItem.rssi
                    val deviceName = deviceItem.deviceName
                    val txPower = getTxPower(deviceName)
                    val distance = calculateDistance(rssi,txPower!!)

                    if (distance <4000)
                    {
                        val builder = NotificationCompat.Builder(this@CheckForDistanceService, notificationChannelId)
                            .setSmallIcon(R.drawable.ic_notification)
                            .setColor(getResources().getColor(R.color.colorAccent))
                            .setContentTitle("Varna")
                            .setContentText("Behåll tillräckligt med social avstånd")
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            // Set the intent that will fire when the user taps the notification
                            .setAutoCancel(true)
                            .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))

                        with(NotificationManagerCompat.from(this@CheckForDistanceService)) {
                            // notificationId is a unique int for each notification that you must define
                            notify(12345, builder.build())
                        }
                    }



                }
                deviceItemList.clear()



            }
        }
    }

    fun getTxPower(deviceName: String?): Double?
    {
        var value = devicePowerMap.get(deviceName)
        if (value ==null)
            return 20.0
        else
            return value
    }

    fun populateDevicePowerMap()
    {
        devicePowerMap.put("Samsung Galaxy S9",20.0 )
        devicePowerMap.put("Samsung Galaxy S8",20.0 )
        devicePowerMap.put("Samsung Galaxy S9+",20.0 )
        devicePowerMap.put("Samsung Galaxy S10",20.0 )
        devicePowerMap.put("Samsung Galaxy S10+",20.0 )
        devicePowerMap.put("Samsung Galaxy S8+",20.0 )
        devicePowerMap.put("Samsung Galaxy S7",20.0 )
        devicePowerMap.put("Huawei P30 Pro",20.0)
        devicePowerMap.put("Huawei P20 Pro",20.0)
        devicePowerMap.put("Sony Xperia Series",20.0)
        devicePowerMap.put("Samsung Galaxy A10",20.0)
        devicePowerMap.put("OnePlus 6",20.0)
        devicePowerMap.put("Huawei Honor 9",20.0)
        devicePowerMap.put("Huawei Honor 8",20.0)
        devicePowerMap.put("Huawei Mate 20 Pro",20.0)
        devicePowerMap.put("Xiaomi",20.0)
        devicePowerMap.put("Samsung Galaxy Note 10 Plus 5G",20.0)
        devicePowerMap.put("Samsung Galaxy Note 10 Plus",20.0)
        devicePowerMap.put("Samsung Galaxy Note 10 Pro",20.0)
        devicePowerMap.put("Samsung Galaxy Note 10 Lite",20.0)
        devicePowerMap.put("Samsung Galaxy Note 10 5G",20.0)
        devicePowerMap.put("Samsung Galaxy Note 10",20.0)
        devicePowerMap.put("Samsung Galaxy Note 9",20.0)
        devicePowerMap.put("Samsung Galaxy Note FE",20.0)
        devicePowerMap.put("Samsung Galaxy Note 8",20.0)
        devicePowerMap.put("Smsung Galaxy Note 5 Dual Sim",20.0)
        devicePowerMap.put("Samsung Galaxy Note 5 Winter Edition",10.0)
        devicePowerMap.put("Samsung Galaxy Note 5 EDGE",10.0)
        devicePowerMap.put("Samsung Galaxy Note 5",20.0)
        devicePowerMap.put("Samsung Galaxy C11",20.0)
        devicePowerMap.put("Samsung Galaxy C7 2017",20.0)
        devicePowerMap.put("Samsung Galaxy C5 Pro",20.0)
        devicePowerMap.put("Samsung Galaxy C9 Pro",20.0)
        devicePowerMap.put("Samsung Galaxy C5",10.0)
        devicePowerMap.put("Galaxy J2 Core",20.0)
        devicePowerMap.put("Samsung Galaxy S20",20.0)
        devicePowerMap.put("Samsung Galaxy S10",20.0)
        devicePowerMap.put("Samsung Galaxy S6",10.0)
        devicePowerMap.put("Samsung Galaxy A",20.0)
        devicePowerMap.put("Samsung J",20.0)
    }

}

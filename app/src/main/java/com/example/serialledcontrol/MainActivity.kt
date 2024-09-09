package com.example.serialledcontrol

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), SerialInputOutputManager.Listener {

    private lateinit var usbManager: UsbManager
    private var serialPort: UsbSerialPort? = null
    private lateinit var logTextView: TextView
    private var usbIoManager: SerialInputOutputManager? = null
    private val ACTION_USB_PERMISSION = "com.example.serialledcontrol.USB_PERMISSION"

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let {
                            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
                            val driver = availableDrivers.firstOrNull { it.device == device }
                            if (driver != null) {
                                openUsbConnection(driver)
                            }
                        }
                    } else {
                        updateLog("USB permission denied.")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usbManager = getSystemService(USB_SERVICE) as UsbManager
        logTextView = findViewById(R.id.logTextView)

        // Register receiver for USB permission with RECEIVER_NOT_EXPORTED flag
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)

        setupUsbConnection()

        val buttonOn: Button = findViewById(R.id.buttonOn)
        val buttonOff: Button = findViewById(R.id.buttonOff)

        buttonOn.setOnClickListener {
            sendSerialData("ON\n")
        }

        buttonOff.setOnClickListener {
            sendSerialData("OFF\n")
        }
    }


    private fun setupUsbConnection() {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            updateLog("No USB devices found.")
            return
        }

        val driver = availableDrivers[0]
        val device = driver.device

        // Check if permission is granted, request if not
        if (usbManager.hasPermission(device)) {
            openUsbConnection(driver)
        } else {
            val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
            usbManager.requestPermission(device, permissionIntent)
            updateLog("Requesting USB permission.")
        }
    }

    private fun openUsbConnection(driver: UsbSerialDriver) {
        val connection = usbManager.openDevice(driver.device)
        if (connection == null) {
            updateLog("Failed to open USB device.")
            return
        }

        serialPort = driver.ports[0] // Most devices have just one port (port 0)
        serialPort?.open(connection)
        serialPort?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

        // Start event-driven read
        usbIoManager = SerialInputOutputManager(serialPort, this)
        Executors.newSingleThreadExecutor().submit(usbIoManager)

        updateLog("USB device connected: ${driver.device.productName}")
    }

    private fun sendSerialData(data: String) {
        serialPort?.write(data.toByteArray(StandardCharsets.UTF_8), 1000)
        updateLog("Sent data: $data")
    }

    private fun updateLog(message: String) {
        runOnUiThread {
            logTextView.append("\n$message")
            Log.e("BAHH", message)
        }
    }

    override fun onNewData(data: ByteArray?) {
        runOnUiThread {
            data?.let {
                val receivedData = String(it, StandardCharsets.UTF_8)
                logTextView.append("\nReceived: $receivedData")
                Log.e("BAHH", "Received: $receivedData")
            }
        }
    }

    override fun onRunError(e: Exception?) {
        runOnUiThread {
            logTextView.append("\nError: ${e?.message}")
            Log.e("BAHH", "Error: ${e?.message}")
        }
    }

    override fun onDestroy() {
        unregisterReceiver(usbReceiver)
        usbIoManager?.stop()
        serialPort?.close()
        updateLog("USB connection closed.")
        super.onDestroy()
    }
}

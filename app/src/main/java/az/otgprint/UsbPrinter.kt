package az.otgprint

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.*
import android.os.Build

class UsbPrinter(private val ctx: Context) {
    private val mgr = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
    private val action = "az.otgprint.USB_PERMISSION"

    private fun printerIntf(d: UsbDevice): UsbInterface? =
        (0 until d.interfaceCount).map { d.getInterface(it) }
            .firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_PRINTER }

    fun find(): UsbDevice? = mgr.deviceList.values.firstOrNull { printerIntf(it) != null }

    fun withPermission(dev: UsbDevice, onGranted: () -> Unit) {
        if (mgr.hasPermission(dev)) { onGranted(); return }
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                ctx.unregisterReceiver(this)
                if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) onGranted()
            }
        }
        if (Build.VERSION.SDK_INT >= 33)
            ctx.registerReceiver(rx, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        else ctx.registerReceiver(rx, IntentFilter(action))
        val pi = PendingIntent.getBroadcast(ctx, 0,
            Intent(action).setPackage(ctx.packageName), PendingIntent.FLAG_MUTABLE)
        mgr.requestPermission(dev, pi)
    }

    /** Sends raw bytes to the bulk OUT endpoint of the printer interface. */
    fun send(dev: UsbDevice, data: ByteArray, progress: (Int) -> Unit = {}) {
        val intf = printerIntf(dev) ?: error("Printer interface not found")
        val ep = (0 until intf.endpointCount).map { intf.getEndpoint(it) }.firstOrNull {
            it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT
        } ?: error("Bulk OUT endpoint not found")
        val conn = mgr.openDevice(dev) ?: error("Cannot open device")
        try {
            check(conn.claimInterface(intf, true)) { "claimInterface failed" }
            val chunk = 16 * 1024
            var off = 0
            while (off < data.size) {
                val n = minOf(chunk, data.size - off)
                val r = conn.bulkTransfer(ep, data, off, n, 15000)
                check(r > 0) { "USB write failed at $off" }
                off += r
                progress(off * 100 / data.size)
            }
            conn.releaseInterface(intf)
        } finally { conn.close() }
    }
}

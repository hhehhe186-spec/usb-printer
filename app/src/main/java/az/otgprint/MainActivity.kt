package az.otgprint

import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var usb: UsbPrinter
    private var files: List<Uri> = emptyList()

    private val pick = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        files = it; log("${it.size} fayl seçildi")
    }
    private val pickRaw = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let { sendRaw(it) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        usb = UsbPrinter(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        fun btn(t: String, a: () -> Unit) = Button(this).apply { text = t; setOnClickListener { a() } }
            .also { root.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, -2)) }
        btn("Printeri tap") { findPrinter() }
        btn("Fayl seç (JPG/PNG/HEIC/PDF)") { pick.launch(arrayOf("image/*", "application/pdf")) }
        btn("Çap et") { print() }
        btn("Raw fayl göndər (test)") { pickRaw.launch(arrayOf("*/*")) }
        status = TextView(this)
        root.addView(ScrollView(this).apply { addView(status) })
        setContentView(root)
    }

    private fun log(s: String) = runOnUiThread { status.append(s + "\n") }

    private fun findPrinter() {
        val d = usb.find() ?: return log("Printer tapılmadı. OTG kabelini yoxlayın.")
        log("Tapıldı: ${d.productName ?: d.deviceName} VID=%04X PID=%04X".format(d.vendorId, d.productId))
        usb.withPermission(d) { log("İcazə verildi") }
    }

    private fun print() {
        val d = usb.find() ?: return log("Printer yoxdur")
        if (files.isEmpty()) return log("Əvvəl fayl seçin")
        usb.withPermission(d) {
            thread {
                try {
                    val pages = files.flatMap { Pages.load(this, it) }
                    log("${pages.size} səhifə hazırlandı")
                    usb.send(d, CanonEngine.encode(pages)) { log("Göndərilir: $it%") }
                    log("Bitdi")
                } catch (e: Throwable) { log("Xəta: ${e.message}") }
            }
        }
    }

    private fun sendRaw(u: Uri) {
        val d = usb.find() ?: return log("Printer yoxdur")
        usb.withPermission(d) {
            thread {
                try {
                    val bytes = contentResolver.openInputStream(u)!!.use { it.readBytes() }
                    usb.send(d, bytes) { log("Raw: $it%") }
                    log("Raw göndərildi")
                } catch (e: Throwable) { log("Xəta: ${e.message}") }
            }
        }
    }
}

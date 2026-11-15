package az.otgprint

import android.content.Context
import android.graphics.*
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri

/** Turns any supported file into a list of page bitmaps. */
object Pages {
    fun load(ctx: Context, uri: Uri, dpi: Int = 300): List<Bitmap> {
        val cr = ctx.contentResolver
        val mime = cr.getType(uri) ?: ""
        val name = uri.lastPathSegment?.lowercase() ?: ""
        return when {
            mime == "application/pdf" || name.endsWith(".pdf") -> pdf(ctx, uri, dpi)
            mime.startsWith("image/") || name.matches(Regex(".*\\.(jpe?g|png|heic|heif|webp)")) -> {
                val src = ImageDecoder.createSource(cr, uri)
                listOf(ImageDecoder.decodeBitmap(src) { d, _, _ ->
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                })
            }
            else -> throw UnsupportedOperationException(
                "Dəstəklənmir: $mime. DOCX/PPTX faylını əvvəlcə PDF-ə çevirin.")
        }
    }

    private fun pdf(ctx: Context, uri: Uri, dpi: Int): List<Bitmap> {
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "r")!!
        val out = ArrayList<Bitmap>()
        PdfRenderer(pfd).use { r ->
            for (i in 0 until r.pageCount) r.openPage(i).use { p ->
                val bmp = Bitmap.createBitmap(p.width * dpi / 72, p.height * dpi / 72, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                out += bmp
            }
        }
        pfd.close()
        return out
    }
}

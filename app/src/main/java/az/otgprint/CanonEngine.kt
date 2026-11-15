package az.otgprint

import android.graphics.Bitmap

/**
 * Bitmap -> printer byte stream.
 * Canon G2410 uses a proprietary raster protocol. Fill this in after the
 * USB capture is analysed. Until then use "Raw fayl göndər" to replay
 * a captured job (.bin) and verify the USB path works.
 */
object CanonEngine {
    fun encode(pages: List<Bitmap>): ByteArray =
        throw NotImplementedError("Canon raster protokolu hələ yazılmayıb (USB capture lazımdır)")
}

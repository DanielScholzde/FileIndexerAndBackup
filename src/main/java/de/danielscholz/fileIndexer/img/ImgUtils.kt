package de.danielscholz.fileIndexer.img

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Directory
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.jpeg.JpegDirectory
import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.Global
import org.imgscalr.Scalr
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.util.*
import javax.imageio.ImageIO

object ImgUtils {

   private val logger = LoggerFactory.getLogger(javaClass)

   data class ImgAttr(val originalDate: Instant?, val width: Int?, val height: Int?)

   fun extractExifOriginalDateAndDimension(imgBytes: ByteArray, defaultTimeZone: TimeZone): ImgAttr {
      val metadata = ImageMetadataReader.readMetadata(ByteArrayInputStream(imgBytes))

      var directory: Directory? = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
      var date = directory?.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL, defaultTimeZone) ?: directory?.getDate(ExifSubIFDDirectory.TAG_DATETIME, defaultTimeZone)
      var width = directory?.getInteger(ExifSubIFDDirectory.TAG_IMAGE_WIDTH) ?: directory?.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH)
      var height = directory?.getInteger(ExifSubIFDDirectory.TAG_IMAGE_HEIGHT) ?: directory?.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT)

      directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
      if (width == null) {
         width = directory?.getInteger(ExifIFD0Directory.TAG_IMAGE_WIDTH)
         height = directory?.getInteger(ExifIFD0Directory.TAG_IMAGE_HEIGHT)
      }
      if (date == null) {
         date = directory?.getDate(ExifIFD0Directory.TAG_DATETIME_ORIGINAL, defaultTimeZone) ?: directory?.getDate(ExifIFD0Directory.TAG_DATETIME, defaultTimeZone)
      }
      if (width == null) {
         directory = metadata.getFirstDirectoryOfType(JpegDirectory::class.java)
         width = directory?.getInt(JpegDirectory.TAG_IMAGE_WIDTH)
         height = directory?.getInt(JpegDirectory.TAG_IMAGE_HEIGHT)
      }
      return ImgAttr(date?.toInstant(), width, height)
   }

   fun extractThumbnail(imgBytes: ByteArray): ByteArray? {
      var thumbnail: ByteArray? = null
      var i = -1
      while (i < imgBytes.size - 2) {
         i++
         if (imgBytes[i] == 0xFF.toByte()) {
            if (imgBytes[i + 1] == 0xD8.toByte()
               && i + 2 < imgBytes.size
               && imgBytes[i + 2] == 0xFF.toByte()
            ) {
               val img = tryExtractImg(imgBytes, i)
               if (img != null) {
                  if (thumbnail == null || thumbnail.size < img.size) {
                     thumbnail = img
                  }
               }
               continue
            }
         }
      }
      return thumbnail
   }

   fun tryExtractImg(imgBytes: ByteArray, begin: Int): ByteArray? {
      var i = begin + 2
      var j = -1
      var w = -1
      var h = -1

      // within header of JPG
      while (i < imgBytes.size - 10) {
         val ff = imgBytes[i].toUByte()
         if (ff != 0xFF.toUByte()) {
            return null
         }
         val marker = imgBytes[i + 1].toUByte()
         i += 2

         if (marker in 0xd0.toUByte()..0xd7.toUByte())
            continue
         if (marker == 0x01.toUByte())
            continue    // TEM

         if (marker == 0xc0.toUByte()) {
            h = (imgBytes[i + 3].toInt() shl 8 or imgBytes[i + 4].toUByte().toInt())
            w = (imgBytes[i + 5].toInt() shl 8 or imgBytes[i + 6].toUByte().toInt())
         }

         val len = (imgBytes[i].toUByte().toInt() shl 8) or imgBytes[i + 1].toUByte().toInt()

         if (marker == 0xda.toUByte()) { // SOS Start of Scan
            j = i + len
            break
         }
         i += len
      }

      if (j < 0 || w < 0 || h < 0) return null

      // within body of JPG
      while (j < imgBytes.size - 2) {
         if (imgBytes[j].toUByte() == 0xff.toUByte()
            && imgBytes[j + 1].toUByte() == 0xd9.toUByte()
         ) {
            if (w in 1..20_000 && h in 1..20_000) {
               return imgBytes.copyOfRange(begin, j + 2)
            }
            return null
         }
         j++
      }

      return null
   }

//   @ExperimentalUnsignedTypes
//   fun jpegProps(data: ByteArray) {          // data is an array of bytes
//      var off = 0
//      var w = 0
//      var h = 0
//      while (off < data.size) {
//         while (data[off] == 0xff.toByte()) off++
//         var mrkr = data[off]
//         off++
//
//         if (mrkr == 0xd8.toByte())
//            continue    // SOI
//         if (mrkr == 0xd9.toByte())
//            break       // EOI
//         if (mrkr.toUByte() in 0xd0.toUByte()..0xd7.toUByte())
//            continue
//         if (mrkr == 0x01.toByte())
//            continue    // TEM
//
//         var len = (data[off].toUByte().toInt() shl 8) or data[off + 1].toUByte().toInt()
//         off += 2;
//
//         if (mrkr == 0xc0.toByte()) {
//            w = (data[off + 1].toUByte().toInt() shl 8 or data[off + 2].toUByte().toInt())
//            h = (data[off + 3].toUByte().toInt() shl 8 or data[off + 4].toUByte().toInt())
//            println("w=$w h=$h")
//         }
//         off += len - 2;
//      }
//   }

   fun scaleAndSaveImg(imgContent: ByteArray, fullPathAndFilename: String) {
      try {
         val image = ImageIO.read(ByteArrayInputStream(imgContent))
         if (image != null) {
            val resizedImage = Scalr.resize(image, Scalr.Method.QUALITY, Config.INST.thumbnailSize)
            val file = File(fullPathAndFilename)
            ImageIO.write(resizedImage, "jpeg", file) // writes with quality 85%
            Global.createdFilesCallback(file)
         }
      } catch (e: Exception) {
         logger.warn("Error on scaling the image: " + e.message)
      }
   }

}
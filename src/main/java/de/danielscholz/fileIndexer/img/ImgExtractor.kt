package de.danielscholz.fileIndexer.img

//object ImgExtractor {
//
//   fun extract(fromImg: File, toImg: File): Boolean {
//
//      try {
//         val file = File(Config.INST.exifToolPath + "exiftool.exe").canonicalFile
//         val builder = ProcessBuilder(file.toString(), "-b", "-PreviewImage", fromImg.canonicalPath)
//         val process = builder.start()
//
//         val image = ImageIO.read(process.inputStream)
//
//         val image1 = Scalr.resize(image, Scalr.Method.QUALITY, 400)
//
//         ImageIO.write(image1, "jpeg", toImg) // schreibt mit Quality 85%
//
////      val ios = ImageIO.createImageOutputStream(File("d:/testtt.jpg"))
////      val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
////      val writeParam = writer.defaultWriteParam
////      writeParam.compressionMode = ImageWriteParam.MODE_EXPLICIT
////      writeParam.compressionQuality = 0.6f
////      writer.output = ios
////      writer.write(null, IIOImage(image1, null, null), writeParam)
////      writer.dispose()
////      ios.close()
//
//         return true
//      } catch (e: Exception) {
//         return false
//      }
//   }
//
//}
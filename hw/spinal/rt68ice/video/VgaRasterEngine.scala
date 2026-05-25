package rt68ice.video

import rt68ice.video.VgaRasterEngine.rgbConfig
import spinal.core._
import spinal.lib._
import spinal.lib.graphic.RgbConfig

import scala.language.postfixOps

object VgaRasterEngine {
  val rgbConfig = RgbConfig(8, 8, 8)
}

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class VgaRasterEngine() extends Component {
  val io = new Bundle {
    // Interface to the top-level memory blocks
    val wordAddress  = out UInt(15 bits)
    val bank0Data    = in Bits(16 bits)
    val bank1Data    = in Bits(16 bits)

    // Pixel color index routed out to the Palette
    val colorIndex = out Bits(2 bits)

    // VGA signals
    val vSync   = out Bool()
    val hSync   = out Bool()
    val colorEn = out Bool()
  }

  val vgaCounter = VgaCounter(rgbConfig)
  vgaCounter.io.timings.setAs_h640_v480_r60

  val addressGen = new Area {
    val timings = vgaCounter.io.timings
    val hCounter = vgaCounter.io.hCounter
    val vCounter = vgaCounter.io.vCounter

    val pixelX = hCounter - timings.h.colorStart
    val pixelY = vCounter - timings.v.colorStart

    // 1 bpp Word Address calculation:
    // Each line has 640 pixels / 16 pixels per word = 40 words per line.
    // (pixelY * 40) + (pixelX >> 4) // 640x480 1 bpp
    val wordAddress = ((pixelY << 5) + (pixelY << 3)) + (pixelX >> 4)

    // We need to keep track of which bit within the 16-bit word we want.
    // Because RAM read takes 1 cycle, we delay this bit index selector by 1 cycle
    // so it matches the moment memData becomes valid.
    // To match 68000 big-endian layout: Bit index 0 is mapped to memData(15).
    val pixelBitIdx = Delay(~pixelX(3 downto 0), 1)
  }

  io.wordAddress := addressGen.wordAddress.resized

  val plane0Bit = io.bank0Data(addressGen.pixelBitIdx)
  val plane1Bit = io.bank1Data(addressGen.pixelBitIdx)

  // Map the 1-bit pixel to full 24-bit RGB with palette
  // BufferCC for clock cross domain handling
  io.colorIndex := (plane1Bit ## plane0Bit)

  io.hSync   := vgaCounter.io.hSync
  io.vSync   := vgaCounter.io.vSync
  io.colorEn := vgaCounter.io.colorEn
}


package rt68ice.video

import rt68ice.core.M68KBus
import rt68ice.video.StreamedVgaDevice.rgbConfig
import spinal.core._
import spinal.lib._
import spinal.lib.graphic.RgbConfig
import spinal.lib.graphic.vga._

import scala.language.postfixOps

object VgaDevice {
  val rgbConfig = RgbConfig(8, 8, 8)
}

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class VgaDevice(vgaCd : ClockDomain) extends Component {
  val io = new Bundle {
    val bus = slave(M68KBus())
    val sel = in Bool()
    val vga = master(Vga(StreamedVgaDevice.rgbConfig))
  }

  val framebuffer = Mem(Bits(16 bits), 32768) // 64 KB

  // --- 68000 bus side ---
  io.bus.dataIn := framebuffer.readWriteSync(
    address = io.bus.address(15 downto 1).asUInt,
    data = io.bus.dataOut,
    enable = io.sel,
    write = io.bus.wr,
    mask = io.bus.uds ## io.bus.lds,
    clockCrossing = true,
  )

  // ------ VGA side ------
  // vgaCd { ... } equivalent to new ClockingArea(vgaCd) { ... }
  val vgaArea = new ClockingArea(vgaCd) {
    // VGA Controller
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
      val wordAddress = (pixelY * 40) + (pixelX >> 4) // 640x480 1 bpp

      // We need to keep track of which bit within the 16-bit word we want.
      // Because RAM read takes 1 cycle, we delay this bit index selector by 1 cycle
      // so it matches the moment memData becomes valid.
      val pixelBitIdx = Delay(pixelX(3 downto 0), 1)
    }

    val memData = framebuffer.readSync(
      address = addressGen.wordAddress.resized,
      enable = True,
      clockCrossing = true,
    )

    // Extract the exact monochrome pixel bit.
    // To match 68000 big-endian layout: Bit index 0 is mapped to memData(15)
    val pixelBit = memData(15 - addressGen.pixelBitIdx)

    // Map the 1-bit pixel to full 24-bit RGB (White when true, Black when false)
    val colorValue = Mux(pixelBit, U(0xFF, 8 bits), U(0x00, 8 bits))

    io.vga.color.r := colorValue
    io.vga.color.g := colorValue
    io.vga.color.b := colorValue

    io.vga.hSync := vgaCounter.io.hSync
    io.vga.vSync := vgaCounter.io.vSync
    io.vga.colorEn := vgaCounter.io.colorEn
  }
}
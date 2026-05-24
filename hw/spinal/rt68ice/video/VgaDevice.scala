package rt68ice.video

import rt68ice.core.M68KBus
import rt68ice.video.StreamedVgaDevice.rgbConfig
import spinal.core._
import spinal.lib._
import spinal.lib.experimental.chisel.Bundle
import spinal.lib.graphic.RgbConfig
import spinal.lib.graphic.vga._

import scala.language.postfixOps

object VgaDevice {
  val rgbConfig = RgbConfig(8, 8, 8)
}

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class VgaDevice(vgaCd : ClockDomain, testPatter: Boolean = true) extends Component {
  val io = new Bundle {
    val bus   = slave(M68KBus())
    val sel   = in Bool()
    val vga = master(Vga(StreamedVgaDevice.rgbConfig))
  }

  val framebuffer = Mem(Bits(16 bits), 32768) // 64 KB
  framebuffer.init(new TestPatterns(sizeInWords = 32768, width = 640, height = 51).box())

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
      val colorEn = vgaCounter.io.colorEn

      val pixelX = hCounter - timings.h.colorStart
      val pixelY = vCounter - timings.v.colorStart

      //val wordAddress = (pixelY.resized * 40) + (pixelX.resized >> 4) // This is for 1 bpp
      val wordAddress = (pixelY * 640) + pixelX
    }

    val memData = framebuffer.readSync(
      address = addressGen.wordAddress.resized,
      enable  = addressGen.colorEn,
      clockCrossing = true,
    )

    io.vga.color.r := (memData(15 downto 11) ## B"000").asUInt
    io.vga.color.g := (memData(10 downto  5) ## B"00").asUInt
    io.vga.color.b := (memData(4  downto  0) ## B"000").asUInt


    // TODO: the first pixel (0,0) is missing, not matters centering the display or the delay
    io.vga.hSync := vgaCounter.io.hSync
    io.vga.vSync := vgaCounter.io.vSync
    io.vga.colorEn := vgaCounter.io.colorEn

    /*
    val delay = 1
    io.vga.hSync := Delay(vgaCounter.io.hSync, delay)
    io.vga.vSync := Delay(vgaCounter.io.vSync, delay)
    io.vga.colorEn := Delay(vgaCounter.io.colorEn, delay)
     */
  }
}
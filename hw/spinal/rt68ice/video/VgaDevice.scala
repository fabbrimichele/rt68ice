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

  // 75 KB -> 640x480 2bpp
  val bank0 = Mem(Bits(16 bits), 19200)
  val bank1 = Mem(Bits(16 bits), 19200)

  // --- 68000 bus side ---
  val bankSelect = io.bus.address(1)
  val ramAddress = io.bus.address(16 downto 2).asUInt

  val cpuBank0Data = bank0.readWriteSync(
    address = ramAddress,
    data    = io.bus.dataOut,
    enable  = io.sel && !bankSelect,
    write   = io.bus.wr,
    mask    = io.bus.uds ## io.bus.lds,
    clockCrossing = true
  )

  val cpuBank1Data = bank1.readWriteSync(
    address = ramAddress,
    data    = io.bus.dataOut,
    enable  = io.sel && bankSelect,
    write   = io.bus.wr,
    mask    = io.bus.uds ## io.bus.lds,
    clockCrossing = true
  )

  io.bus.dataIn := Mux(bankSelect, cpuBank1Data, cpuBank0Data)

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
      // The following might work and be optimized (to be verified)
      // val wordAddress = ((pixelY << 5) + (pixelY << 3)) + (pixelX >> 4)

      // We need to keep track of which bit within the 16-bit word we want.
      // Because RAM read takes 1 cycle, we delay this bit index selector by 1 cycle
      // so it matches the moment memData becomes valid.
      val pixelBitIdx = Delay(pixelX(3 downto 0), 1)
    }

    val vgaBank0Data = bank0.readSync(
      address = addressGen.wordAddress.resized,
      enable = True,
      clockCrossing = true,
    )

    val vgaBank1Data = bank1.readSync(
      address = addressGen.wordAddress.resized,
      enable = True,
      clockCrossing = true,
    )

    // To match 68000 big-endian layout: Bit index 0 is mapped to memData(15)
    val plane0Bit = vgaBank0Data(15 - addressGen.pixelBitIdx)
    val plane1Bit = vgaBank1Data(15 - addressGen.pixelBitIdx)

    // Map the 1-bit pixel to full 24-bit RGB with palette
    // TODO: use dual port memory shared on 68000 bus
    switch(plane1Bit ## plane0Bit) {
      is(B"00") {
        io.vga.color.r := 0
        io.vga.color.g := 0
        io.vga.color.b := 0
      }
      is(B"01") {
        io.vga.color.r := 0
        io.vga.color.g := 255
        io.vga.color.b := 0
      }
      is(B"10") {
        io.vga.color.r := 255
        io.vga.color.g := 0
        io.vga.color.b := 0
      }
      is(B"11") {
        io.vga.color.r := 255
        io.vga.color.g := 255
        io.vga.color.b := 255
      }
    }

    io.vga.hSync := vgaCounter.io.hSync
    io.vga.vSync := vgaCounter.io.vSync
    io.vga.colorEn := vgaCounter.io.colorEn
  }
}
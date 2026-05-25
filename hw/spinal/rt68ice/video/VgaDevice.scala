package rt68ice.video

import rt68ice.core.M68KBus
import rt68ice.video.VgaDevice.rgbConfig
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
    val vga = master(Vga(rgbConfig))
  }

  // ----------------------
  // Memory definitions
  // ----------------------
  // Framebuffer: 640x480 2bpp = 75 KB
  val bank0 = Mem(Bits(16 bits), 19200)
  val bank1 = Mem(Bits(16 bits), 19200)

  // Palette: 4 colors * 8 bytes = 32 bytes
  val palette = Vec(Reg(Bits(24 bits)), 4)
  palette(0).init(B"24'x000000") // 00: Black
  palette(1).init(B"24'x00FF00") // 01: Green
  palette(2).init(B"24'xFF0000") // 10: Red
  palette(3).init(B"24'xFFFFFF") // 11: White

  // ----------------------
  // 68000 bus side
  // ----------------------
  // Framebuffer
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

  // Palette
  // TODO: connect to 68K bus

  // ----------------------
  // VGA side
  // ----------------------
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
      // To match 68000 big-endian layout: Bit index 0 is mapped to memData(15).
      val pixelBitIdx = Delay(15 - pixelX(3 downto 0), 1)
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

    val plane0Bit = vgaBank0Data(addressGen.pixelBitIdx)
    val plane1Bit = vgaBank1Data(addressGen.pixelBitIdx)

    // Map the 1-bit pixel to full 24-bit RGB with palette
    val colorIndex = (plane1Bit ## plane0Bit).asUInt
    val pixelColor = palette(colorIndex)

    io.vga.color.r := pixelColor(23 downto 16).asUInt
    io.vga.color.g := pixelColor(15 downto 8).asUInt
    io.vga.color.b := pixelColor(7 downto 0).asUInt

    io.vga.hSync := vgaCounter.io.hSync
    io.vga.vSync := vgaCounter.io.vSync
    io.vga.colorEn := vgaCounter.io.colorEn
  }
}
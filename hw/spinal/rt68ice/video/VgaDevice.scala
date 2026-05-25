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
    val bus     = slave(M68KBus())
    val fbSel   = in Bool()
    val palSel  = in Bool()
    val vga     = master(Vga(rgbConfig))
  }

  // ----------------------
  // Memory definitions
  // ----------------------
  // Framebuffer: 640x480 2bpp = 75 KB
  val bank0 = Mem(Bits(16 bits), 19200)
  val bank1 = Mem(Bits(16 bits), 19200)

  // Palette: 4 colors * 3 bytes = 12 bytes
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
    enable  = io.fbSel && !bankSelect,
    write   = io.bus.wr,
    mask    = io.bus.uds ## io.bus.lds,
    clockCrossing = true
  )

  val cpuBank1Data = bank1.readWriteSync(
    address = ramAddress,
    data    = io.bus.dataOut,
    enable  = io.fbSel && bankSelect,
    write   = io.bus.wr,
    mask    = io.bus.uds ## io.bus.lds,
    clockCrossing = true
  )

  // Palette
  // each entry is mapped as 32 bits but only 24 bits are used
  val palAddress = io.bus.address(3 downto 2).asUInt
  val isLowerWord = io.bus.address(1)

  when(io.palSel) {
    when(io.bus.wr) {
      // Write
      // TODO: manage LDS/UDS
      when(isLowerWord) {
        // Lower word, mapping full word (green+blue)
        palette(palAddress)(15 downto 0) := io.bus.dataOut
      } otherwise {
        // Upper word, mapping only lower bytes (red)
        palette(palAddress)(23 downto 16) := io.bus.dataOut(7 downto 0)
      }
    } otherwise {
      when(isLowerWord) {
        // Lower word, mapping full word (green+blue)
        io.bus.dataIn := palette(palAddress)(15 downto 0)
      } otherwise {
        // Upper word, mapping only lower bytes (red)
        io.bus.dataIn := B"8'x00" ## palette(palAddress)(23 downto 16)
      }
    }
  }

  // Memory blocks routing when reading
  // neither io.fbSel nor io.palSel case is managed by the bus controller
  when (io.fbSel) {
    io.bus.dataIn := Mux(bankSelect, cpuBank1Data, cpuBank0Data)
  } otherwise {
    when(isLowerWord) {
      io.bus.dataIn := palette(palAddress)(15 downto 0).resized
    } otherwise {
      io.bus.dataIn := palette(palAddress)(23 downto 16).resized
    }
  }

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
      val pixelBitIdx = Delay(~pixelX(3 downto 0), 1)
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
    // BufferCC for clock cross domain handling
    val vgaPalette = Vec(BufferCC(palette(0)), BufferCC(palette(1)), BufferCC(palette(2)), BufferCC(palette(3)))
    val colorIndex = (plane1Bit ## plane0Bit).asUInt
    val pixelColor = vgaPalette(colorIndex)

    io.vga.color.r := pixelColor(23 downto 16).asUInt
    io.vga.color.g := pixelColor(15 downto 8).asUInt
    io.vga.color.b := pixelColor(7 downto 0).asUInt

    io.vga.hSync := vgaCounter.io.hSync
    io.vga.vSync := vgaCounter.io.vSync
    io.vga.colorEn := vgaCounter.io.colorEn
  }
}
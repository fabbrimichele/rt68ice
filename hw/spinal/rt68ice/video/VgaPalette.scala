package rt68ice.video

import rt68ice.core.M68KBus._
import spinal.core._
import spinal.lib._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class VgaPalette(vgaCd : ClockDomain) extends Component {
  val io = new Bundle {
    // 68K side (Main clock)
    val sel     = in Bool()
    val wr      = in Bool()
    val uds     = in Bool()
    val lds     = in Bool()
    val address = in Bits(ADDRESS_WIDTH bits)
    val dataOut = in Bits(DATA_WIDTH bits)
    val dataIn  = out Bits(DATA_WIDTH bits)

    // VGA side (VGA clock)
    val colorIndex  = in Bits(4 bits)
    val pixelColor  = out Bits(24 bits)
  }

  // ------------------------------------------------------------------
  // Memory definitions: 16 colors * 3 bytes = 48 bytes total
  // ------------------------------------------------------------------
  val palette = Vec(Reg(Bits(24 bits)), 16)

  // Initialize with a standard 16-color retro palette baseline
  palette(0).init(B"24'x000000")  // 0: Black
  palette(1).init(B"24'x0000AA")  // 1: Blue
  palette(2).init(B"24'x00AA00")  // 2: Green
  palette(3).init(B"24'x00AAAA")  // 3: Cyan
  palette(4).init(B"24'xAA0000")  // 4: Red
  palette(5).init(B"24'xAA00AA")  // 5: Magenta
  palette(6).init(B"24'xAA5500")  // 6: Brown
  palette(7).init(B"24'xAAAAAA")  // 7: Light Gray
  palette(8).init(B"24'x555555")  // 8: Dark Gray
  palette(9).init(B"24'x5555FF")  // 9: Bright Blue
  palette(10).init(B"24'x55FF55") // 10: Bright Green
  palette(11).init(B"24'x55FFFF") // 11: Bright Cyan
  palette(12).init(B"24'xFF5555") // 12: Bright Red
  palette(13).init(B"24'xFF55FF") // 13: Bright Magenta
  palette(14).init(B"24'xFFFF55") // 14: Yellow
  palette(15).init(B"24'xFFFFFF") // 15: White

  // ----------------------
  // 68000 bus side
  // ----------------------
  val palAddress = io.address(5 downto 2).asUInt
  val isLowerWord = io.address(1)

  io.dataIn := 0

  when(io.sel) {
    when(io.wr) {
      when(isLowerWord) {
        // Lower Word: D15-D8 maps to Green, D7-D0 maps to Blue
        when(io.uds) { palette(palAddress)(15 downto 8) := io.dataOut(15 downto 8) }
        when(io.lds) { palette(palAddress)(7 downto 0)  := io.dataOut(7 downto 0) }
      } otherwise {
        // Upper Word: D7-D0 maps to Red (Big Endian longword formatting)
        when(io.lds) { palette(palAddress)(23 downto 16) := io.dataOut(7 downto 0) }
      }
    } otherwise {
      when(isLowerWord) {
        // Lower word, mapping full word (green+blue)
        io.dataIn := palette(palAddress)(15 downto 0)
      } otherwise {
        // Upper word, mapping only lower bytes (red)
        io.dataIn := B"8'x00" ## palette(palAddress)(23 downto 16)
      }
    }
  }

  // Vga side
  vgaCd {
    val synchronizedPalette = Vec(palette.map(reg => BufferCC(reg)))

    io.pixelColor := synchronizedPalette(io.colorIndex.asUInt)
  }
}


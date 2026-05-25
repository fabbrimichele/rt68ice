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
    val colorIndex  = in Bits(2 bits)
    val pixelColor  = out Bits(24 bits)
  }

  // Palette: 4 colors * 3 bytes = 12 bytes
  val palette = Vec(Reg(Bits(24 bits)), 4)
  palette(0).init(B"24'x000000") // 00: Black
  palette(1).init(B"24'x00FF00") // 01: Green
  palette(2).init(B"24'xFF0000") // 10: Red
  palette(3).init(B"24'xFFFFFF") // 11: White

  // ----------------------
  // 68000 bus side
  // ----------------------
  val palAddress = io.address(3 downto 2).asUInt
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
    val vgaPalette = Vec(BufferCC(palette(0)), BufferCC(palette(1)), BufferCC(palette(2)), BufferCC(palette(3)))
    io.pixelColor := vgaPalette(io.colorIndex.asUInt)
  }
}


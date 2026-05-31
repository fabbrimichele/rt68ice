package rt68ice.video

import rt68ice.core.M68KBus._
import spinal.core._

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
    val colorIndex  = in Bits(8 bits)
    val pixelColor  = out Bits(24 bits)
  }

  // ------------------------------------------------------------------
  // Memory definitions: 256 colors * 3 bytes
  // ------------------------------------------------------------------
  val memR = Mem(Bits(8 bits), 256)
  val memG = Mem(Bits(8 bits), 256)
  val memB = Mem(Bits(8 bits), 256)

  // ----------------------
  // 68000 bus side
  // ----------------------
  val palAddress  = io.address(9 downto 2).asUInt
  val isLowerWord = io.address(1)

  // Decoded write enables
  val enR = io.sel && !isLowerWord && io.lds
  val enG = io.sel && isLowerWord  && io.uds
  val enB = io.sel && isLowerWord  && io.lds

  val readR = memR.readWriteSync(
    address = palAddress,
    data    = io.dataOut(7 downto 0),
    enable  = enR,
    write   = io.wr,
    clockCrossing = true
  )

  val readG = memG.readWriteSync(
    address = palAddress,
    data    = io.dataOut(15 downto 8),
    enable  = enG,
    write   = io.wr,
    clockCrossing = true
  )

  val readB = memB.readWriteSync(
    address = palAddress,
    data    = io.dataOut(7 downto 0),
    enable  = enB,
    write   = io.wr,
    clockCrossing = true
  )

  io.dataIn := 0
  when(io.sel && !io.wr) {
    when(isLowerWord) {
      io.dataIn := readG ## readB
    } otherwise {
      io.dataIn := B"8'x00" ## readR
    }
  }
  // Vga side
  vgaCd {
    val vgaIdx = io.colorIndex.asUInt

    val rOut = memR.readSync(vgaIdx, enable = True, clockCrossing = true)
    val gOut = memG.readSync(vgaIdx, enable = True, clockCrossing = true)
    val bOut = memB.readSync(vgaIdx, enable = True, clockCrossing = true)

    io.pixelColor := rOut ## gOut ## bOut
  }
}


package rt68ice.io

import rt68ice.core.M68KBus
import spinal.core._
import spinal.lib._

import scala.language.postfixOps

case class SpiMasterConfig(portCount: Int)

case class SpiMasterDevice(config: SpiMasterConfig) extends Component {
  val io = new Bundle {
    val bus = slave(M68KBus())
    val sel = in Bool() // chip select from decoder
    val spis = Vec(master(Spi()), config.portCount)
  }

  val spiMaster = new SpiMasterBB
  // The address decoder remains selected while the CPU holds an SPI address
  // between transactions.  Qualify it with a byte-lane strobe so the VHDL
  // core only sees an actual 68000 bus access, as it did in rt68f via !AS.
  val spiAccess = io.sel && (io.bus.uds || io.bus.lds)

  // 68000 bus
  spiMaster.io.addr := io.bus.address(2 downto 1).asBits
  spiMaster.io.cs := spiAccess
  spiMaster.io.data_in := io.bus.dataOut(7 downto 0)
  spiMaster.io.rw := !io.bus.wr
  io.bus.dataIn := spiMaster.io.data_out.resized
  // spiMaster.io.irq not used // TODO?

  // SPI bus
  spiMaster.io.spi_miso := spiMaster.io.spi_cs_n.muxList(
    defaultValue = True,
    // spi_cs_n is asserted low, need false
    // Patterns = 11111110, 11111101, 11111011, etc
    mappings = for(i <- 0 until config.portCount) yield {
      val pattern = B(8 bits, default -> true) // pattern = 11111111
      pattern(i) := False                      // e.g. i = 1 -> pattern = 11111101
      pattern -> io.spis(i).miso
    },
  )

  // SPIs outputs
  for (i <- 0 until config.portCount) {
    io.spis(i).mosi := spiMaster.io.spi_mosi
    io.spis(i).clk := spiMaster.io.spi_clk
    io.spis(i).cs := spiMaster.io.spi_cs_n(i)
  }
}

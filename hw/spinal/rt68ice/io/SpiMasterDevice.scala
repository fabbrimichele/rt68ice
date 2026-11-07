package rt68ice.io

import rt68ice.core.M68KDeviceBus
import spinal.core._
import spinal.lib._

import scala.language.postfixOps

case class SpiMasterConfig(portCount: Int)

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class SpiMasterDevice(config: SpiMasterConfig) extends Component {
  val io = new Bundle {
    val bus = slave(M68KDeviceBus())
    val spis = Vec(master(Spi()), config.portCount)
  }

  // Fixed bus wait states are handled by BusController.
  io.bus.ready := True

  // This device does not generate interrupt requests.
  io.bus.irq := False

  val spiMaster = new SpiMasterBB
  // Keep CPU byte-lane decoding here; the core retains a generic word interface.
  // Data writes require both lanes; low-byte reads are safe and side-effect free.
  // Control registers only use the low byte, so LDS-only accesses are safe and
  // allow direct memory BTST polling at base + 3. UDS-only accesses are ignored.
  val wordAccess = io.bus.uds && io.bus.lds
  val controlRegister = io.bus.address(2 downto 1) === B"01" ||
    io.bus.address(2 downto 1) === B"10"
  val dataByteRead = io.bus.address(2 downto 1) === B"00" && !io.bus.wr && io.bus.lds
  val spiAccess = io.bus.sel && (wordAccess || (controlRegister && io.bus.lds) || dataByteRead)

  // Convert CPU byte addresses to the core's register indices:
  // base + 0: 16-bit data, base + 2: command/status, base + 4: configuration.
  // Low bytes: data read at base + 1, status at +3, configuration at +5.
  // Base + 6 is reserved. SPI transfer length is configured independently.
  spiMaster.io.addr := io.bus.address(2 downto 1).asBits
  spiMaster.io.cs := spiAccess
  spiMaster.io.data_in := io.bus.dataOut
  spiMaster.io.rw := !io.bus.wr
  io.bus.dataIn := 0
  when(spiAccess) {
    io.bus.dataIn := spiMaster.io.data_out
  }
  // The core IRQ output is unused; software polls the BUSY status bit.

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

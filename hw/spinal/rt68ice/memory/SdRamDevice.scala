package rt68ice.memory

import rt68ice.core.M68KBus
import spinal.core._
import spinal.lib._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class SdRamDevice() extends Component {
  val io = new Bundle {
    val bus       = slave(M68KBus())
    val sel       = in Bool()
    val cpuClkEn  = out Bool()
    val sdRam     = master(SdRam())
  }

  val sdRam = new SdRamBB()

  io.sdRam.clock  := sdRam.io.SDRAM_CLK
  io.sdRam.cke    := sdRam.io.SDRAM_CKE
  io.sdRam.dq     := sdRam.io.SDRAM_DQ
  io.sdRam.a      := sdRam.io.SDRAM_A
  io.sdRam.dm     := sdRam.io.SDRAM_DQM
  io.sdRam.we_n   := sdRam.io.SDRAM_nWE
  io.sdRam.ras_n  := sdRam.io.SDRAM_nRAS
  io.sdRam.cas_n  := sdRam.io.SDRAM_nCAS
  io.sdRam.ba     := sdRam.io.SDRAM_BA
  io.sdRam.cs_n   := sdRam.io.SDRAM_nCS

  // CPU/Chipset interface
  sdRam.io.p0_data    := io.bus.dataOut
  io.bus.dataIn       := sdRam.io.output
  sdRam.io.p0_addr    := io.bus.address(23 downto 1).resized // access by words
  sdRam.io.p0_byte_en := io.bus.uds ## io.bus.lds
  sdRam.io.p0_wr_req  := io.bus.wr && io.sel
  sdRam.io.p0_rd_req  := !io.bus.wr && io.sel

  // TODO:
  sdRam.io.p0_available
  sdRam.io.p0_ready
  io.cpuClkEn
}

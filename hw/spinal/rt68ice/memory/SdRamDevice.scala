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

  // --- CPU/Chipset interface ---

  sdRam.io.p0_data    := io.bus.dataOut
  io.bus.dataIn       := sdRam.io.p0_q
  sdRam.io.p0_addr    := io.bus.address(23 downto 1).resized // access by words
  sdRam.io.p0_byte_en := io.bus.uds ## io.bus.lds

  // 1. Request Generation
  val isWrite = io.bus.wr
  val isRead = !io.bus.wr
  val validDataPhase = io.bus.uds || io.bus.lds

  // Reads trigger immediately on address select.
  // Writes wait for UDS/LDS to ensure stable data.
  val rdTrigger = io.sel.rise(False) && isRead
  val wrTrigger = (io.sel && isWrite && validDataPhase).rise(False)

  sdRam.io.p0_wr_req  := wrTrigger
  sdRam.io.p0_rd_req  := rdTrigger

  // 2. CPU Clock Enable / Wait States
  // Stall the CPU (drop cpuClkEn) whenever the memory is selected,
  // and resume the CPU clock exactly when the SDRAM signals it is ready.
  io.cpuClkEn := !io.sel || sdRam.io.p0_ready

  // 3. Prevent unused signal warnings
  sdRam.io.p0_available // Read but unused in this specific clock-gating strategy
}
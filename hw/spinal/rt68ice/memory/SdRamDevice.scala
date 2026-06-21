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

  // An active cycle is when the chip is selected AND the CPU has asserted strobes.
  // In a 32-bit move.l, io.sel stays High continuously, but validDataPhase will toggle.
  val activeCycle = io.sel && validDataPhase

  // Detect if the CPU seamlessly moves to the second half of a 32-bit transfer.
  // The TG68K does not negate UDS/LDS between the two 16-bit halves;
  // instead, it increments the address bus.
  val lastAddress = RegNext(io.bus.address)
  val addressChanged = io.bus.address =/= lastAddress
  val newPhase = activeCycle && addressChanged

  // Trigger SDRAM operations on the initial strobe, OR when the address increments
  val phaseTrigger = activeCycle.rise(False) || newPhase

  val rdTrigger = phaseTrigger && isRead
  val wrTrigger = phaseTrigger && isWrite

  sdRam.io.p0_wr_req  := wrTrigger
  sdRam.io.p0_rd_req  := rdTrigger

  // 2. CPU Clock Enable / Wait States
  // We need to know if we are currently in the middle of a longword transfer.
  // A longword is two back-to-back phases.
  val isLongword = RegInit(False)
  when(activeCycle.rise(False)) {
    isLongword := True
  } elsewhen(!activeCycle) {
    isLongword := False
  }

  // The operation is only "Done" when the second phase finishes.
  val fullCycleDone = RegInit(False)
  when(!activeCycle || newPhase) {
    fullCycleDone := False
  } elsewhen(isLongword && sdRam.io.p0_ready) {
    fullCycleDone := True
  }

  // Stall the CPU until the full 32-bit transaction is complete.
  // We allow the CPU to run if the cycle is finished OR if the CPU is currently idle.
  io.cpuClkEn := !activeCycle || (fullCycleDone && !newPhase)
}
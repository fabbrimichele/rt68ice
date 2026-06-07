package rt68ice.memory

import rt68ice.core.M68KBus
import spinal.core._
import spinal.lib._
import spinal.lib.fsm._

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

  io.sdRam.clock  := sdRam.io.sd_clk
  io.sdRam.cke    := sdRam.io.sd_cke
  io.sdRam.dq     := sdRam.io.sd_data
  io.sdRam.a      := sdRam.io.sd_addr
  io.sdRam.dm     := sdRam.io.sd_dqm
  io.sdRam.we_n   := sdRam.io.sd_we
  io.sdRam.ras_n  := sdRam.io.sd_ras
  io.sdRam.cas_n  := sdRam.io.sd_cas
  io.sdRam.ba     := sdRam.io.sd_ba
  // NOTE: cs_n is mapped manually in the FSM below, do not wire it directly to sel

  // CPU/Chipset interface
  sdRam.io.din      := io.bus.dataOut
  io.bus.dataIn     := sdRam.io.dout
  sdRam.io.addr     := io.bus.address(23 downto 1).resized // access by words
  sdRam.io.ds       := io.bus.uds ## io.bus.lds
  sdRam.io.we       := io.bus.wr

  // -------------------------------------------------------------------------
  // 1. Refresh Timer
  // -------------------------------------------------------------------------
  // The IS42S16160B requires 8192 refreshes every 64ms (~7.8us per refresh).
  // SpinalHDL will automatically calculate the tick count based on your ClockDomain.
  val refreshTimer = Timeout(7.5 us)
  val refreshReq   = RegInit(False)

  when(refreshTimer) {
    refreshReq := True
    refreshTimer.clear()
  }

  // -------------------------------------------------------------------------
  // 2. Arbiter State Machine
  // -------------------------------------------------------------------------
  val latencyCounter = Counter(0 to 6)

  // Default register states
  val ramCs     = RegInit(False)
  val doRefresh = RegInit(False)
  val dataReady = RegInit(True)

  sdRam.io.cs      := ramCs
  sdRam.io.refresh := doRefresh

  val fsm = new StateMachine {
    val stateIdle     = new State with EntryPoint
    val stateRefresh  = new State
    val stateAccess   = new State
    val stateRecovery = new State

    stateIdle.whenIsActive {
      ramCs     := False
      doRefresh := False

      // Prioritize refresh to prevent data loss
      when(refreshReq && sdRam.io.ready) {
        latencyCounter.clear()
        goto(stateRefresh)
      } elsewhen(io.sel && sdRam.io.ready) {
        // Handle CPU request
        ramCs     := True
        dataReady := False // Halt the CPU
        latencyCounter.clear()
        goto(stateAccess)
      }
    }

    stateRefresh.whenIsActive {
      ramCs      := True
      doRefresh  := True
      refreshReq := False

      latencyCounter.increment()
      // Wait enough cycles for the Auto-Refresh to complete (tRC parameter)
      when(latencyCounter.value === 4) {
        goto(stateIdle)
      }
    }

    stateAccess.whenIsActive {
      ramCs := True
      latencyCounter.increment()

      // sdram_nano takes a fixed number of cycles to reach STATE_READ
      // You may need to tweak '5' depending on RASCAS_DELAY and CAS_LATENCY
      when(latencyCounter.value === 5) {
        dataReady := True // Resume the CPU
        ramCs     := False
        goto(stateRecovery)
      }
    }

    stateRecovery.whenIsActive {
      // Wait for the CPU to drop the request before returning to Idle.
      // This prevents the FSM from immediately looping back into an access
      // if io.sel stays high for an extra clock cycle.
      when(!io.sel) {
        goto(stateIdle)
      }
    }
  }

  // -------------------------------------------------------------------------
  // 3. CPU Clock Enable Control
  // -------------------------------------------------------------------------
  io.cpuClkEn := sdRam.io.ready && dataReady
  io.sdRam.cs_n := ramCs // Wire the active-high internal signal to the external active-low (if necessary, check sdRamBB port polarity)
}

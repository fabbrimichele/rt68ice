package rt68ice.core

import rt68ice.core.M68KType.{M68000, M68010, M68020, M68KType}
import spinal.core._

import scala.language.postfixOps

object M68KType extends Enumeration {
  type M68KType = Value
  val M68000, M68010, M68020 = Value
}

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class M68KSync(cpuType: M68KType = M68000, autovector: Boolean = true) extends Component {
  val io = new Bundle {
    val address = out Bits(32 bits)
    val dataOut = out Bits(16 bits)
    val dataIn  = in Bits(16 bits)
    val ipl		  = in Bits(3 bits)
    val busErr  = in Bool()
    val wr      = out Bool()
    val uds     = out Bool()
    val lds     = out Bool()
  }

  val tg68kernel = new TG68KdotCBB

  // Configure CPU
  val CPU = cpuType match {
    case M68000 => B"00"
    case M68010 => B"01"
    case M68020 => B"11"
  }
  tg68kernel.io.CPU := CPU
  tg68kernel.io.IPL_autovector := Bool(autovector)

  // Assign IO
  io.address := tg68kernel.io.addr_out
  io.dataOut := tg68kernel.io.data_write
  io.wr := !tg68kernel.io.nWr
  io.uds := !tg68kernel.io.nUDS
  io.lds := !tg68kernel.io.nLDS

  tg68kernel.io.data_in := io.dataIn
  tg68kernel.io.IPL := io.ipl
  tg68kernel.io.berr := io.busErr

  // TODO: move to a BusController class once starting using SDRAM
  // 1. Remove internal 'isWaiting' register from M68KSync.
  // 2. Expose 'bus_ready' input to M68KSync.
  // 3. Update 'clkena_in' logic to be: tg68kernel.io.clkena_in := bus_ready
  // 4. Implement BusController with Address Decoding and variable wait-states for:
  //    - BlockRAM (1 cycle)
  //    - Peripherals (0-N cycles)
  //    - SDRAM (Dynamic/Handshaking)

  // Handle memory sync access
  // disable the CPU for one clock cycle when accessing memory
  // ROM
  // 1. Detect if the CPU is trying to use the bus
  // We pause for Fetch (00), Data Read (10), and Data Write (11)
  val busActive = tg68kernel.io.busstate === B"00" ||
    tg68kernel.io.busstate === B"10" ||
    tg68kernel.io.busstate === B"11"

  // 2. The Wait-State Register
  // This keeps track of whether we are currently in the middle of a pause
  val isWaiting = RegInit(False)

  // 3. The Handshake Logic
  when(busActive && !isWaiting) {
    // A bus cycle just started.
    // Pull the brake (clkena_in = Low) and set the flag.
    tg68kernel.io.clkena_in := False
    isWaiting := True
  } otherwise {
    // Either the bus is idle (01), or we already finished our 1-cycle wait.
    // Release the brake.
    tg68kernel.io.clkena_in := True
    isWaiting := False
  }
}

package rt68ice.core

import rt68ice.core.M68KType.{M68000, M68010, M68020, M68KType}
import spinal.core._
import spinal.lib.master

import scala.language.postfixOps

object M68KType extends Enumeration {
  type M68KType = Value
  val M68000, M68010, M68020 = Value
}

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class M68K(
  cpuType: M68KType = M68000,
  autovector: Boolean = true
) extends Component {

  val io = new Bundle {
    val bus       = master(M68KBus())
    val ipl		    = in Bits(3 bits)
    val busState  = out Bits(2 bits)
    val busErr    = in Bool()
    val enable    = in Bool()
  }

  // CPU
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
  tg68kernel.io.data_in := io.bus.dataIn
  tg68kernel.io.IPL := io.ipl
  tg68kernel.io.berr := io.busErr
  tg68kernel.io.clkena_in := io.enable
  io.bus.address := tg68kernel.io.addr_out
  io.bus.dataOut := tg68kernel.io.data_write
  io.bus.uds := !tg68kernel.io.nUDS
  io.bus.lds := !tg68kernel.io.nLDS
  io.bus.wr := !tg68kernel.io.nWr
  io.busState := tg68kernel.io.busstate
}

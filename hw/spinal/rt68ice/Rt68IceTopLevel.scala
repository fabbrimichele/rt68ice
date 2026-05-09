package rt68ice

import rt68ice.core._
import spinal.core._

import scala.annotation.unused
import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class Rt68IceTopLevel() extends Component {
  val io = new Bundle {
    val led = out Bits(3 bits)
  }

  // Reset
  val reset = Reset(resetCycles = 25000) // 1 ms at 25 MHz

  // Domain with reset
  val coreClockDomain = ClockDomain.current.copy(
    config = ClockDomainConfig(resetKind = SYNC),
    reset = reset.io.resetOut
  )

  // Area with reset
  @unused
  val coreArea = new ClockingArea(coreClockDomain) {
    // CPU
    val cpu = new TG68KdotC
    cpu.io.data_in := B"x4E71" // NOP
    cpu.io.IPL := B"111"
    cpu.io.IPL_autovector := True // Do not ask peripheral for a vector number
    cpu.io.berr := False
    cpu.io.CPU := B"00" // 68000, let's start simple

    // LED Device
    io.led := cpu.io.addr_out(24 downto 22)
  }
}

object Rt68IceTopLevelVerilog extends App {
  private val report = Config.spinal.generateVerilog(Rt68IceTopLevel())
  report.mergeRTLSource("mergeRTL") // Merge all rtl sources into mergeRTL.vhd and mergeRTL.v files
}


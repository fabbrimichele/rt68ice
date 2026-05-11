package rt68ice.core

import spinal.core._
import spinal.lib.ResetCtrl

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class ClockCtrl() extends Component {
  val pll = new PllBB
  val reset = Reset(resetCycles = 25000) // 1 ms at 25 MHz

  val clk20Domain = ClockDomain.internal(
    name = "clk20Mhz",
    config = ClockDomainConfig(resetKind = SYNC),
    frequency = FixedFrequency(20 MHz)
  )

  clk20Domain.clock := pll.io.clkout0
  clk20Domain.reset := ResetCtrl.asyncAssertSyncDeassert(
    input = reset.io.resetOut || !pll.io.locked,
    clockDomain = clk20Domain
  )
}

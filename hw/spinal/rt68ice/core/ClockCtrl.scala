package rt68ice.core

import spinal.core._
import spinal.lib.ResetCtrl

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class ClockCtrl() extends Component {
  val pll = new PllBB
  val reset = Reset(resetCycles = 25000) // 1 ms at 25 MHz

  val cd20MHz = createClockDomain(
    name = "clk20Mhz",
    frequency = 20 MHz,
    pllClock = pll.io.clockout20,
  )

  val cd25MHz = createClockDomain(
    name = "clk25Mhz",
    frequency = 25 MHz,
    pllClock = pll.io.clockout25,
  )

  val cd125MHz = createClockDomain(
    name = "clk125Mhz",
    frequency = 125 MHz,
    pllClock = pll.io.clockout125,
  )

  private def createClockDomain(
    name: String,
    frequency: HertzNumber,
    pllClock: Bool,
  ): ClockDomain = {
    val clkDomain = ClockDomain.internal(
      name = name,
      config = ClockDomainConfig(resetKind = SYNC),
      frequency = FixedFrequency(frequency)
    )

    clkDomain.clock := pllClock
    clkDomain.reset := ResetCtrl.asyncAssertSyncDeassert(
      input = reset.io.resetOut || !pll.io.locked,
      clockDomain = clkDomain
    )

    clkDomain
  }
}

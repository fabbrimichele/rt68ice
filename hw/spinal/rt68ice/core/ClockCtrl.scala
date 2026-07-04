package rt68ice.core

import spinal.core._
import spinal.lib.ResetCtrl

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class ClockCtrl() extends Component {
  private val pll = new PllBB
  private val reset = Reset(resetCycles = 25000) // 1 ms at 25 MHz

  val vgaCd = createClockDomain(
    name = "clkVga",
    frequency = 25 MHz,
    pllClock = pll.io.clk_vga,
  )

  val hdmiCd = createClockDomain(
    name = "clkHdmi",
    frequency = 125 MHz,
    pllClock = pll.io.clk_hdmi,
  )

  val systemCd = createClockDomain(
    name = "clkSystem",
    frequency = 28.4091 MHz,
    pllClock = pll.io.clk_cpu,
  )

  val sdRamCd = createClockDomain(
    name = "clkSdRam",
    frequency = 28.4091 MHz,
    pllClock = pll.io.clk_cpu,
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

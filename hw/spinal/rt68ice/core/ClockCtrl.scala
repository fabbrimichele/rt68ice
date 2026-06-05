package rt68ice.core

import spinal.core._
import spinal.lib.ResetCtrl

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class ClockCtrl() extends Component {
  val pll1 = new Pll1BB
  val pll2 = new Pll2BB
  val reset = Reset(resetCycles = 25000) // 1 ms at 25 MHz

  val cpuCd = createClockDomain(
    name = "clk8Mhz",
    frequency = 8.09859 MHz,
    pllClock = pll1.io.clk_8_cpu,
  )

  val vgaCd = createClockDomain(
    name = "clk25Mhz",
    frequency = 25 MHz,
    pllClock = pll2.io.clk_25_vga,
  )

  val hdmiCd = createClockDomain(
    name = "clk125Mhz",
    frequency = 125 MHz,
    pllClock = pll2.io.clk_125_hdmi,
  )

  val sdRamCd = createClockDomain(
    name = "clk93Mhz",
    frequency = 95.8333 MHz,
    pllClock = pll1.io.clk_100_sdram,
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
      input = reset.io.resetOut || !pll1.io.locked || !pll2.io.locked,
      clockDomain = clkDomain
    )

    clkDomain
  }
}

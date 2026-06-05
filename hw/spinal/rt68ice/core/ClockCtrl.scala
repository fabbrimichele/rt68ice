package rt68ice.core

import spinal.core._
import spinal.lib.ResetCtrl

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class ClockCtrl() extends Component {
  val pll = new Pll2BB
  val reset = Reset(resetCycles = 25000) // 1 ms at 25 MHz

  val cpuCd = createClockDomain(
    name = "clk7Mhz",
    frequency = 7.8125 MHz,
    pllClock = pll.io.clk_7_cpu,
  )

  val vgaCd = createClockDomain(
    name = "clk25Mhz",
    frequency = 25 MHz,
    pllClock = pll.io.clk_25_vga,
  )

  val hdmiCd = createClockDomain(
    name = "clk125Mhz",
    frequency = 125 MHz,
    pllClock = pll.io.clk_125_hdmi,
  )

  val sdRamCd = createClockDomain(
    name = "clk93Mhz",
    frequency = 93.75 MHz,
    pllClock = pll.io.clk_93_sdram,
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

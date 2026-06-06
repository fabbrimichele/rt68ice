package rt68ice.core

import spinal.core._
import spinal.lib.ResetCtrl

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class ClockCtrl() extends Component {
  val io = new Bundle {
    val cpuClkEn = out Bool()
  }

  private val pll = new PllBB
  private val reset = Reset(resetCycles = 25000) // 1 ms at 25 MHz

  val vgaCd = createClockDomain(
    name = "clk25Mhz",
    frequency = 25 MHz,
    pllClock = pll.io.clk_25,
  )

  val hdmiCd = createClockDomain(
    name = "clk125Mhz",
    frequency = 125 MHz,
    pllClock = pll.io.clk_125,
  )

  val systemCd = createClockDomain(
    name = "clk32Mhz",
    frequency = 32 MHz,
    pllClock = pll.io.clk_31,
  )

  // Generate the 8MHz strobe synchronously within the 32MHz domain
  private val clockingArea = new ClockingArea(systemCd) {
    val clockGenerator = new ClockGenerator()
  }

  io.cpuClkEn := clockingArea.clockGenerator.io.mhz8_en

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

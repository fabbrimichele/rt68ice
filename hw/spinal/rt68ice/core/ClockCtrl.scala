package rt68ice.core

import spinal.core._
import spinal.lib._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class ClockCtrl() extends Component {
  val io = new Bundle {
    val resetButton = in Bool()
  }

  private val pll = new PllBB

  private val reset = Reset(20 ms)
  reset.io.resetButton := io.resetButton

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
    frequency = 25 MHz,
    pllClock = pll.io.clk_cpu,
  )

  val sdRamCd = createClockDomain(
    name = "clkSdRam",
    frequency = 50 MHz, // It's used to calculate the SDRAM timing
    pllClock = pll.io.clk_sdram,
  )

  val usbCd = createClockDomain(
    name = "clkUsb",
    frequency = 12 MHz,
    pllClock = pll.io.clk_usb,
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

package rt68ice.core

import spinal.core._
import spinal.core.sim._

import scala.language.postfixOps

object ResetSim extends App {
  // Use real millisecond durations at a slower clock to keep simulation small.
  // BOOT matches the top-level board clock domain: this circuit must keep
  // running while the system domains it resets are held in reset.
  val simConfig = SpinalConfig(
    defaultConfigForClockDomains = ClockDomainConfig(resetKind = BOOT),
    defaultClockDomainFrequency = FixedFrequency(1 MHz)
  )
  val pressCycles = 10000
  val holdCycles = 20000

  SimConfig.withConfig(simConfig).compile(Reset(20 ms, 10 ms)).doSim { dut =>
    dut.io.resetButton #= true
    dut.clockDomain.forkStimulus(period = 1000)

    def checkReset(expected: Boolean, message: String): Unit =
      assert(dut.io.resetOut.toBoolean == expected, message)

    def cycles(count: Int, expected: Boolean, message: String): Unit = {
      for (_ <- 0 until count) {
        dut.clockDomain.waitSampling()
        sleep(1)
        checkReset(expected, message)
      }
    }

    def button(pressed: Boolean): Unit = {
      dut.clockDomain.waitFallingEdge()
      dut.io.resetButton #= !pressed
      sleep(1)
    }

    def awaitReset(expected: Boolean, maxCycles: Int, message: String): Unit = {
      var elapsed = 0
      while (dut.io.resetOut.toBoolean != expected && elapsed < maxCycles) {
        dut.clockDomain.waitSampling()
        sleep(1)
        elapsed += 1
      }
      checkReset(expected, message)
    }

    // No button is needed to generate power-on reset. It must also expire
    // without a button press; neither synchronizer nor timer may start pressed.
    sleep(1)
    checkReset(true, "Power-on reset must start asserted")
    // forkStimulus has already supplied its initial edge at time zero.
    cycles(holdCycles - 2, true, "Power-on reset must last the configured hold time")
    awaitReset(false, 4, "Power-on reset must release with the button untouched")

    // A pulse entirely between sample edges catches any remaining raw bypass.
    button(pressed = true)
    sleep(100)
    checkReset(false, "A sub-clock raw pulse must not assert reset asynchronously")
    dut.io.resetButton #= true
    cycles(8, false, "An unsampled pulse must not reset the system")

    // Pulses well below and just below the threshold must be rejected. Released
    // intervals let the synchronized input clear the qualification counter.
    for (width <- Seq(100, 1000, 5000, pressCycles - 1)) {
      button(pressed = true)
      cycles(width, false, s"A $width-cycle press must not assert reset")
      button(pressed = false)
      cycles(16, false, "Release must discard a short press, including synchronizer tail")
    }

    // Several almost-long-enough presses must not accumulate into one press.
    for (_ <- 0 until 4) {
      button(pressed = true)
      cycles(9000, false, "Separate short presses must not accumulate")
      button(pressed = false)
      cycles(1000, false, "Each released interval must restart qualification")
    }

    // A real press first waits the full minimum interval, then asserts reset
    // after synchronizer latency. Holding it must prevent the release timeout.
    button(pressed = true)
    cycles(pressCycles, false, "Reset must not assert before a continuous 10 ms press")
    awaitReset(true, 4, "A sustained press must assert reset after synchronization")
    cycles(holdCycles * 2, true, "A held button must keep reset asserted indefinitely")
    button(pressed = false)
    cycles(holdCycles, true, "Qualified release must retain the full 20 ms hold")
    awaitReset(false, 4, "Reset must release after the post-release hold")

    // Glitches during the release hold must not restart that hold timer.
    button(pressed = true)
    cycles(pressCycles, false, "A second press must qualify from scratch")
    awaitReset(true, 4, "A second sustained press must assert reset")
    button(pressed = false)
    cycles(5000, true, "Reset must remain held after release")
    button(pressed = true)
    cycles(1000, true, "A short bounce during hold must not requalify")
    button(pressed = false)
    cycles(13000, true, "Hold must not finish early")
    awaitReset(false, 1100, "A short bounce must not extend the original hold")

    // A new fully qualified press during the hold must extend reset through
    // that press and another complete hold after its final release.
    button(pressed = true)
    cycles(pressCycles, false, "Press after a bounce must still need qualification")
    awaitReset(true, 4, "Sustained press must work after release bounce")
    button(pressed = false)
    cycles(1000, true, "Reset must be held between qualified presses")
    button(pressed = true)
    cycles(pressCycles + 8, true, "A sustained re-press during hold must keep reset asserted")
    button(pressed = false)
    cycles(holdCycles, true, "A qualified re-press must restart the full release hold")
    awaitReset(false, 4, "Reset must release after the final qualified press")
    cycles(holdCycles * 2, false, "Idle timer wrap must not spontaneously assert reset")
    println("Reset filter checks passed: power-on, raw glitches, short/repeated presses, sustained presses and release hold")
  }

  // A button held at configuration time must not allow reset to release
  // between the power-on hold and button qualification.
  SimConfig.withConfig(simConfig).compile(Reset(20 ms, 10 ms)).doSim { dut =>
    dut.io.resetButton #= false
    dut.clockDomain.forkStimulus(period = 1000)
    for (_ <- 0 until holdCycles * 3) {
      dut.clockDomain.waitSampling()
      sleep(1)
      assert(dut.io.resetOut.toBoolean, "A button held at power-on must keep reset asserted")
    }
    dut.clockDomain.waitFallingEdge()
    dut.io.resetButton #= true
    for (_ <- 0 until holdCycles) {
      dut.clockDomain.waitSampling()
      sleep(1)
      assert(dut.io.resetOut.toBoolean, "Startup-held button must receive the full release hold")
    }
    dut.clockDomain.waitSampling(4)
    sleep(1)
    assert(!dut.io.resetOut.toBoolean, "Startup-held button must eventually release reset")
    println("Reset filter startup-held-button check passed")
  }
}

package playground

import spinal.core.sim._

object BlinkSim extends App {
  Config.sim.compile(Blink()).doSim { dut =>
    // Fork a process to generate the reset and the clock on the dut
    dut.clockDomain.forkStimulus(period = 10)

    for (_ <- 0 to 99) {
      println(s"LED: ${dut.io.led.toInt} Count: ${dut.clockCounter.toInt}")
      dut.clockDomain.waitRisingEdge(1_250_000)
    }
  }
}

package rt68ice.video

import spinal.core._
import spinal.core.sim._
import spinal.lib.graphic.vga._
import spinal.lib.graphic._

object VgaCounterSim extends App {
  // 1. Configure the simulation context (enabling wave dumps)
  val simConfig = SimConfig
    .withFstWave
    .withConfig(SpinalConfig(defaultConfigForClockDomains = ClockDomainConfig(resetKind = SYNC, resetActiveLevel = HIGH)))

  simConfig.compile(VgaCounter(RgbConfig(8, 8, 8), timingsWidth = 12)).doSim { dut =>

    // 2. Setup the clock domain (e.g., a 25MHz pixel clock)
    dut.clockDomain.forkStimulus(period = 10)
    dut.clockDomain.waitRisingEdge()

    dut.io.softReset #= false
    dut.clockDomain.waitRisingEdge()

    //dut.io.timings.setAs_h640_v480_r60 // Can't be used, it breaks the simulation
    dut.io.timings.h.syncStart #= 95
    dut.io.timings.h.syncEnd #= 799
    dut.io.timings.h.colorStart #= 143
    dut.io.timings.h.colorEnd #= 783
    dut.io.timings.v.syncStart #= 1
    dut.io.timings.v.syncEnd #= 524
    dut.io.timings.v.colorStart #= 34
    dut.io.timings.v.colorEnd #= 514
    dut.io.timings.h.polarity #= false
    dut.io.timings.v.polarity #= false
    dut.clockDomain.waitRisingEdge(250_000)


    println("Simulation finished successfully!")
  }
}
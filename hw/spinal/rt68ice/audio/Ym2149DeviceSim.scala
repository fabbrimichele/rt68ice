package rt68ice.audio

import rt68ice.core.{BusController, M68KBus}
import spinal.core._
import spinal.core.sim._
import spinal.lib._

// Include the real bus controller to exercise write gating and the combined enable.
case class Ym2149BusHarness() extends Component {
  val io = new Bundle {
    val bus = slave(M68KBus())
    val busState = in Bits(2 bits)
    val sdRamClkEn = in Bool()
    val cpuClkEn = out Bool()
  }
  val controller = BusController()
  val psg = Ym2149Device()
  controller.io.cpuBus <> io.bus
  controller.io.busState := io.busState
  Seq(
    controller.io.romBus, controller.io.ramBus, controller.io.ledBus,
    controller.io.uartBus, controller.io.videoBus, controller.io.sdRamBus,
    controller.io.counterBus, controller.io.ledsBus, controller.io.usbBus,
    controller.io.timerBus, controller.io.spiBus, controller.io.ds1302Bus
  ).foreach { port =>
    port.dataIn := 0
    port.irq := False
  }
  psg.io.bus <> controller.io.psgBus

  io.cpuClkEn := io.sdRamClkEn && controller.io.clockEn && psg.io.cpuClkEn
}

object Ym2149DeviceSim extends App {
  SimConfig.withGhdl.compile {
    val dut = Ym2149BusHarness()
    val core = dut.psg.children.collectFirst { case bb: Ym2149BB => bb }.get
    core.io.en_clk_psg_i.simPublic()
    core.io.bdir_i.simPublic()
    core.io.bc_i.simPublic()
    dut.psg.io.cpuClkEn.simPublic()
    dut
  }.doSim { dut =>
    val core = dut.psg.children.collectFirst { case bb: Ym2149BB => bb }.get
    dut.clockDomain.forkStimulus(10)
    dut.io.bus.address #= 0xf24000
    dut.io.bus.dataOut #= 0
    dut.io.bus.wr #= false
    dut.io.bus.uds #= false
    dut.io.bus.lds #= false
    dut.io.busState #= 1
    dut.io.sdRamClkEn #= true
    dut.clockDomain.waitSampling(10)
    sleep(1)

    var cycle = 0
    var tickCycles = Vector.empty[Int]
    var executed = Vector.empty[Int]
    var registeredCommand = 0
    // Observe the same one-cycle command pipeline used by the VHDL core.
    def step(): Unit = {
      val tick = core.io.en_clk_psg_i.toBoolean
      val command = (if (core.io.bdir_i.toBoolean) 2 else 0) |
        (if (core.io.bc_i.toBoolean) 1 else 0)
      if (tick) {
        tickCycles :+= cycle
        if (registeredCommand != 0) executed :+= registeredCommand
      }
      registeredCommand = command
      dut.clockDomain.waitSampling()
      sleep(1)
      cycle += 1
    }
    def idle(cycles: Int = 1): Unit = {
      dut.io.bus.uds #= false
      dut.io.bus.lds #= false
      dut.io.busState #= 1
      sleep(1)
      for (_ <- 0 until cycles) step()
      assert(dut.io.cpuClkEn.toBoolean, "Idle PSG held the CPU")
    }
    def access(offset: Int, write: Boolean, data: Int = 0, hold: Int = 0): Int = {
      val before = executed.size
      dut.io.bus.address #= 0xf24000 + offset
      dut.io.bus.wr #= write
      dut.io.bus.dataOut #= data
      dut.io.bus.lds #= true
      dut.io.busState #= (if (write) 3 else 2)
      sleep(1)
      assert(!dut.psg.io.cpuClkEn.toBoolean, "PSG failed to stall a new access")
      var waited = 0
      while (!dut.io.cpuClkEn.toBoolean && waited < 30) {
        step()
        waited += 1
      }
      assert(dut.io.cpuClkEn.toBoolean, "PSG access timed out")
      val result = dut.io.bus.dataIn.toInt
      // Other devices may keep the CPU stopped after PSG completion. It must
      // keep its result stable and must not execute the command again.
      dut.io.sdRamClkEn #= false
      sleep(1)
      assert(!dut.io.cpuClkEn.toBoolean)
      for (_ <- 0 until hold) {
        step()
        assert(dut.io.bus.dataIn.toInt == result, "Read result changed while stalled")
      }
      dut.io.sdRamClkEn #= true
      val expectedCommand = if (write) { if (offset == 0) 3 else 2 }
                            else { if (offset == 0) 1 else 0 }
      assert(executed.drop(before) == (if (expectedCommand == 0) Vector.empty else Vector(expectedCommand)),
        s"Expected one command $expectedCommand, got ${executed.drop(before)}")
      result
    }

    // Sweep every divider phase, including long holds across additional ticks.
    for (phase <- 0 until 25) {
      idle()
      while (cycle % 25 != phase) step()
      access(0, write = true, data = 14, hold = 20)
      // Change address without dropping strobes, as in consecutive word phases.
      val value = (phase * 37 + 11) & 0xff
      access(2, write = true, data = value, hold = 20)
      val actual = access(0, write = false, hold = 20)
      assert(actual == value, s"Phase $phase: read $actual instead of $value")
      assert(access(2, write = false, hold = 20) == value) // Inactive PSG bus state.
    }
    // Repeated writes to the same address must work after an idle bus phase.
    idle()
    access(0, write = true, data = 15)
    for (value <- Seq(0x55, 0xaa)) {
      idle()
      access(2, write = true, data = value)
      idle()
      assert(access(0, write = false) == value)
    }
    idle(25)
    assert(tickCycles.sliding(2).forall(pair => Set(6, 7).contains(pair(1) - pair(0))))
    val windows = tickCycles.groupBy(_ / 25)
    assert(windows.filter { case (window, _) => window > windows.keys.min && window < windows.keys.max }
      .values.forall(_.size == 4), "Divider did not generate four ticks per 25 clocks")
    println("PSG timing passed: 25 tick alignments, register readback, consecutive phases, and no repeated commands.")
  }
}

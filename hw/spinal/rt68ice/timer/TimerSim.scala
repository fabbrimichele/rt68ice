package rt68ice.timer

import rt68ice.Config
import spinal.core.sim._

object TimerSim extends App {
  Config.sim.compile(Timer()).doSim { dut =>
    dut.clockDomain.forkStimulus(period = 10)

    dut.io.sel #= false
    dut.io.bus.address #= 0
    dut.io.bus.dataOut #= 0
    dut.io.bus.wr #= false
    dut.io.bus.uds #= false
    dut.io.bus.lds #= false
    dut.clockDomain.waitSampling(2)

    def writeWord(offset: Int, data: Long, uds: Boolean = true, lds: Boolean = true): Unit = {
      dut.io.bus.address #= offset
      dut.io.bus.dataOut #= data
      dut.io.bus.uds #= uds
      dut.io.bus.lds #= lds
      dut.io.bus.wr #= true
      dut.io.sel #= true
      dut.clockDomain.waitSampling()
      dut.io.sel #= false
      dut.io.bus.wr #= false
      sleep(1)
    }

    def readWord(offset: Int): Int = {
      dut.io.bus.address #= offset
      dut.io.bus.uds #= true
      dut.io.bus.lds #= true
      dut.io.bus.wr #= false
      dut.io.sel #= true
      sleep(1)
      val result = dut.io.bus.dataIn.toInt
      dut.clockDomain.waitSampling()
      dut.io.sel #= false
      result
    }

    // Byte strobes update the corresponding bytes of 32-bit registers.
    writeWord(0x04, 0x1200, uds = true, lds = false)
    writeWord(0x04, 0x0034, uds = false, lds = true)
    writeWord(0x06, 0x5678)
    assert(readWord(0x04) == 0x1234, "DIVIDER_HI byte writes were not merged")
    assert(readWord(0x06) == 0x5678, "DIVIDER_LO was not readable")

    // Configure a short periodic interval: one tick per two clocks, three
    // ticks per expiry. Starting loads RELOAD into VALUE.
    writeWord(0x04, 0)
    writeWord(0x06, 1)
    writeWord(0x08, 0)
    writeWord(0x0a, 3)
    writeWord(0x00, 0x0007) // enable, auto-reload, interrupt enable

    assert(!dut.io.int.toBoolean, "Timer interrupt was already active at start")
    dut.clockDomain.waitSampling(5)
    assert(!dut.io.int.toBoolean, "Timer expired before six input clocks")
    dut.clockDomain.waitSampling()
    assert(dut.io.int.toBoolean, "Periodic timer did not raise an interrupt")
    assert((readWord(0x02) & 1) != 0, "STATUS did not latch the expiry")

    writeWord(0x02, 1)
    assert(!dut.io.int.toBoolean, "W1C acknowledgement did not clear the interrupt")

    // Explicit reload restarts the divider phase and the high/low VALUE read
    // returns one coherent snapshot.
    writeWord(0x00, 0x000f)
    val valueHigh = readWord(0x0c)
    val valueLow = readWord(0x0e)
    assert(valueHigh == 0 && valueLow <= 3, "VALUE snapshot was outside the configured interval")

    // In one-shot mode expiry stops the timer. Pending state remains visible
    // while masked and starts driving the IRQ as soon as it is unmasked.
    writeWord(0x00, 0)
    writeWord(0x02, 1)
    writeWord(0x04, 0)
    writeWord(0x06, 0)
    writeWord(0x0a, 2)
    writeWord(0x00, 0x0001) // enable, one-shot, interrupt masked
    dut.clockDomain.waitSampling(2)

    assert(!dut.io.int.toBoolean, "A masked timer expiry drove the interrupt output")
    val oneShotStatus = readWord(0x02)
    assert((oneShotStatus & 1) != 0, "Masked expiry was not latched")
    assert((oneShotStatus & 2) == 0, "One-shot timer did not stop after expiry")

    writeWord(0x00, 0x0004) // unmask the already-pending interrupt
    assert(dut.io.int.toBoolean, "Unmasking a pending expiry did not raise the interrupt")
    writeWord(0x02, 1)
    assert(!dut.io.int.toBoolean, "Final acknowledgement did not clear the interrupt")
  }
}

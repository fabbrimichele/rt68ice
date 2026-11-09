package rt68ice.memory

import spinal.core.sim._

object DirectMappedCacheSim extends App {
  SimConfig.compile(DirectMappedCache()).doSim{ dut =>
    dut.clockDomain.forkStimulus(period = 10)

    dut.io.mainBus.address #= 0x00001234L
    dut.io.mainBus.dataOut #= 0xA55A
    dut.io.mainBus.wr #= true
    dut.io.mainBus.uds #= true
    dut.io.mainBus.lds #= true
    dut.io.mainBus.sel #= true

    dut.io.ramBus.dataIn #= 0x5AA5
    dut.io.ramBus.ready #= true
    dut.io.ramBus.irq #= false

    dut.clockDomain.waitSampling(2)

    // Assert
    assert(dut.io.ramBus.address.toInt == 0x00001234L)
    assert(dut.io.ramBus.dataOut.toInt == 0xA55A)
    assert(dut.io.ramBus.wr.toBoolean)
    assert(dut.io.ramBus.uds.toBoolean)
    assert(dut.io.ramBus.lds.toBoolean)
    assert(dut.io.ramBus.sel.toBoolean)

    assert(dut.io.mainBus.dataIn.toInt == 0x5AA5)
    assert(dut.io.mainBus.ready.toBoolean)
    assert(!dut.io.mainBus.irq.toBoolean)
  }
}

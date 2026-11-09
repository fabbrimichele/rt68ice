package rt68ice.memory

import spinal.core.sim._

/** Pass-through wiring checks. No cache behavior or SDRAM timing is modeled yet. */
object DirectMappedCacheSim extends App {
  SimConfig.compile(DirectMappedCache()).doSim { dut =>
    dut.clockDomain.forkStimulus(period = 10)

    // Drive every input: the test acts as both the CPU and the RAM device.
    // Start with the CPU bus idle and the RAM ready to accept a transfer.
    dut.io.mainBus.address #= 0
    dut.io.mainBus.dataOut #= 0
    dut.io.mainBus.wr #= false
    dut.io.mainBus.uds #= false
    dut.io.mainBus.lds #= false
    dut.io.mainBus.sel #= false
    dut.io.ramBus.dataIn #= 0
    dut.io.ramBus.ready #= true
    dut.io.ramBus.irq #= false
    dut.clockDomain.waitSampling(2)

    assert(!dut.io.ramBus.sel.toBoolean)
    assert(!dut.io.ramBus.uds.toBoolean)
    assert(!dut.io.ramBus.lds.toBoolean)
    assert(!dut.io.ramBus.wr.toBoolean)
    assert(dut.io.mainBus.ready.toBoolean)
    assert(!dut.io.mainBus.irq.toBoolean)

    // Full-word write: both byte lanes are selected. Mixed data and a nonzero
    // address make swapped or stuck-at-zero signals easier to detect.
    dut.io.mainBus.address #= 0x00001234L
    dut.io.mainBus.dataOut #= 0xA55A
    dut.io.mainBus.wr #= true
    dut.io.mainBus.uds #= true
    dut.io.mainBus.lds #= true
    dut.io.mainBus.sel #= true
    dut.io.ramBus.ready #= false
    dut.clockDomain.waitSampling(2)

    // Requests travel from mainBus to ramBus. RAM back-pressure travels back.
    assert(dut.io.ramBus.address.toInt == 0x00001234L)
    assert(dut.io.ramBus.dataOut.toInt == 0xA55A)
    assert(dut.io.ramBus.wr.toBoolean)
    assert(dut.io.ramBus.uds.toBoolean)
    assert(dut.io.ramBus.lds.toBoolean)
    assert(dut.io.ramBus.sel.toBoolean)
    assert(!dut.io.mainBus.ready.toBoolean)

    // Finish the write while keeping the CPU request unchanged.
    dut.io.ramBus.ready #= true
    dut.clockDomain.waitSampling(2)
    assert(dut.io.mainBus.ready.toBoolean)
    assert(dut.io.ramBus.address.toInt == 0x00001234L)
    assert(dut.io.ramBus.dataOut.toInt == 0xA55A)

    // Upper-byte-only write: only dataOut[15:8] is meaningful to RAM.
    // These consecutive combinations test wiring, not a CPU transaction model.
    dut.io.mainBus.address #= 0x00001236L
    dut.io.mainBus.dataOut #= 0xAB00
    dut.io.mainBus.uds #= true
    dut.io.mainBus.lds #= false
    dut.clockDomain.waitSampling(2)

    assert(dut.io.ramBus.address.toInt == 0x00001236L)
    assert(dut.io.ramBus.dataOut.toInt == 0xAB00)
    assert(dut.io.ramBus.uds.toBoolean)
    assert(!dut.io.ramBus.lds.toBoolean)
    assert(dut.io.ramBus.sel.toBoolean)
    assert(dut.io.ramBus.wr.toBoolean)

    // Lower-byte-only write: only dataOut[7:0] is meaningful to RAM.
    dut.io.mainBus.address #= 0x00001237L
    dut.io.mainBus.dataOut #= 0x00CD
    dut.io.mainBus.uds #= false
    dut.io.mainBus.lds #= true
    dut.clockDomain.waitSampling(2)

    assert(dut.io.ramBus.address.toInt == 0x00001237L)
    assert(dut.io.ramBus.dataOut.toInt == 0x00CD)
    assert(!dut.io.ramBus.uds.toBoolean)
    assert(dut.io.ramBus.lds.toBoolean)
    assert(dut.io.ramBus.sel.toBoolean)
    assert(dut.io.ramBus.wr.toBoolean)

    // Full-word read: wr goes low and data returns through dataIn.
    dut.io.mainBus.address #= 0x00001238L
    dut.io.mainBus.wr #= false
    dut.io.mainBus.uds #= true
    dut.io.mainBus.lds #= true
    dut.io.ramBus.dataIn #= 0x5AA5
    dut.io.ramBus.ready #= false
    dut.clockDomain.waitSampling(2)

    assert(dut.io.ramBus.address.toInt == 0x00001238L)
    assert(dut.io.ramBus.sel.toBoolean)
    assert(!dut.io.ramBus.wr.toBoolean)
    assert(dut.io.ramBus.uds.toBoolean)
    assert(dut.io.ramBus.lds.toBoolean)
    assert(!dut.io.mainBus.ready.toBoolean)

    dut.io.ramBus.ready #= true
    dut.clockDomain.waitSampling(2)
    assert(dut.io.mainBus.dataIn.toInt == 0x5AA5)
    assert(dut.io.mainBus.ready.toBoolean)

    // Exercise both levels of the IRQ return wire. Real SDRAM never raises IRQ;
    // this checks the current generic bus pass-through connection only.
    dut.io.ramBus.irq #= true
    dut.clockDomain.waitSampling(2)
    assert(dut.io.mainBus.irq.toBoolean)
    dut.io.ramBus.irq #= false
    dut.clockDomain.waitSampling(2)
    assert(!dut.io.mainBus.irq.toBoolean)

    // Deselect the bus and remove both byte strobes after the transfer.
    dut.io.mainBus.sel #= false
    dut.io.mainBus.uds #= false
    dut.io.mainBus.lds #= false
    dut.io.mainBus.wr #= false
    dut.clockDomain.waitSampling(2)

    assert(!dut.io.ramBus.sel.toBoolean)
    assert(!dut.io.ramBus.uds.toBoolean)
    assert(!dut.io.ramBus.lds.toBoolean)
    assert(!dut.io.ramBus.wr.toBoolean)
  }
}

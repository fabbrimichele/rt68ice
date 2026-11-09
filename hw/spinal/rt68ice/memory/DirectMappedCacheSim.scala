package rt68ice.memory

import spinal.core.sim._

/** Wiring checks only; cache behavior and RAM transactions will be tested later. */
object DirectMappedCacheSim extends App {
  SimConfig.compile(DirectMappedCache()).doSim { dut =>
    dut.clockDomain.forkStimulus(period = 10)
    val main = dut.io.mainBus
    val ram = dut.io.ramBus

    def checkPassThrough(): Unit = {
      sleep(1) // Allow the combinational connection to settle.
      assert(ram.address.toLong == main.address.toLong, "address")
      assert(ram.dataOut.toInt == main.dataOut.toInt, "write data")
      assert(ram.wr.toBoolean == main.wr.toBoolean, "write direction")
      assert(ram.uds.toBoolean == main.uds.toBoolean, "upper byte")
      assert(ram.lds.toBoolean == main.lds.toBoolean, "lower byte")
      assert(ram.sel.toBoolean == main.sel.toBoolean, "selection")
      assert(main.dataIn.toInt == ram.dataIn.toInt, "read data")
      assert(main.ready.toBoolean == ram.ready.toBoolean, "ready")
      assert(main.irq.toBoolean == ram.irq.toBoolean, "IRQ")
    }

    // Initialize every input with the CPU bus idle.
    main.address #= 0
    main.dataOut #= 0
    main.wr #= false
    main.uds #= false
    main.lds #= false
    main.sel #= false
    ram.dataIn #= 0
    ram.ready #= true
    ram.irq #= false
    checkPassThrough()

    // Full-word write, first waiting for RAM and then completing.
    main.address #= 0x1234L
    main.dataOut #= 0xA55A
    main.wr #= true
    main.uds #= true
    main.lds #= true
    main.sel #= true
    ram.ready #= false
    checkPassThrough()
    ram.ready #= true
    checkPassThrough()

    // Upper-byte-only write, then lower-byte-only write.
    main.address #= 0x1236L
    main.dataOut #= 0xAB00
    main.lds #= false
    checkPassThrough()
    main.address #= 0x1237L
    main.dataOut #= 0x00CD
    main.uds #= false
    main.lds #= true
    checkPassThrough()

    // Read data and readiness return from RAM to the CPU.
    main.address #= 0x1238L
    main.wr #= false
    main.uds #= true
    ram.dataIn #= 0x5AA5
    ram.ready #= false
    checkPassThrough()
    ram.ready #= true
    checkPassThrough()

    // Check the generic IRQ wire, although real SDRAM does not raise IRQ.
    ram.irq #= true
    checkPassThrough()
    ram.irq #= false
    checkPassThrough()

    // Release the bus.
    main.sel #= false
    main.uds #= false
    main.lds #= false
    checkPassThrough()
  }
}

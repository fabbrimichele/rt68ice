package rt68ice.memory

import spinal.core.sim._

object DirectMappedCacheSim extends App {
  SimConfig.compile(DirectMappedCache()).doSim { dut =>
    dut.clockDomain.forkStimulus(period = 10)
    val main = dut.io.mainBus
    val ram = dut.io.ramBus

    // Start a full-word CPU read; RAM responses remain controlled by each test.
    def startRead(address: Long): Unit = {
      main.address #= address
      main.wr #= false
      main.uds #= true
      main.lds #= true
      main.sel #= true
      sleep(1)
    }

    // Call after checking readiness and data, to accept and end the CPU read.
    def finishRead(): Unit = {
      dut.clockDomain.waitSampling()
      main.sel #= false
      main.uds #= false
      main.lds #= false
      sleep(1)
    }

    // Supply a RAM response; the test checks completion before the next edge.
    def respondFromRam(data: Int): Unit = {
      ram.dataIn #= data
      ram.ready #= true
      sleep(1)
    }

    // Check a full-word downstream read without advancing simulation time.
    def checkRamRead(address: Long): Unit = {
      assert(ram.sel.toBoolean, "RAM request should be selected")
      assert(ram.uds.toBoolean, "RAM upper byte strobe should be asserted")
      assert(ram.lds.toBoolean, "RAM lower byte strobe should be asserted")
      assert(!ram.wr.toBoolean, "RAM request should be a read")
      assert(ram.address.toLong == address, "RAM request should use the expected address")
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

    // waitSampling counts only clock edges with reset released.
    // Allow two such edges for the state machine to enter Idle.
    dut.clockDomain.waitSampling(2)

    // ------- Read -------
    ram.ready #= false
    startRead(0x1234L)

    assert(!main.ready.toBoolean, "CPU should be stalled")
    assert(!ram.sel.toBoolean, "RAM should not be selected yet")

    dut.clockDomain.waitSampling()
    sleep(1)

    assert(!main.ready.toBoolean, "CPU should be stalled")
    checkRamRead(0x1234L)

    // RAM takes three more cycles to respond. The request must stay active.
    for (_ <- 0 until 3) {
      dut.clockDomain.waitSampling()
      sleep(1)
      assert(!main.ready.toBoolean, "CPU should remain stalled while RAM waits")
      checkRamRead(0x1234L)
    }

    // Present valid RAM data. Check completion before its sampling edge.
    respondFromRam(0xA55A)
    assert(main.ready.toBoolean, "CPU should be ready when RAM completes")
    assert(main.dataIn.toInt == 0xA55A, "CPU should receive the RAM word")

    // Accept the result and cache fill, then release the CPU request.
    finishRead()
    assert(!ram.sel.toBoolean, "RAM should be deselected after completion")


    // ------- Cache-hit -------
    ram.dataIn #= 0xDEAD // Different from the cached word; RAM must not supply this read.
    ram.ready #= false
    startRead(0x1234L)

    assert(!main.ready.toBoolean, "CPU should stall during the hit lookup")
    assert(!ram.sel.toBoolean, "Hit lookup should not select RAM")

    dut.clockDomain.waitSampling()
    sleep(1)
    assert(main.ready.toBoolean, "Cache hit should complete without waiting for RAM")
    assert(main.dataIn.toInt == 0xA55A, "Cache hit should return the stored word")
    assert(!ram.sel.toBoolean, "Cache hit should not select RAM")
    assert(!ram.uds.toBoolean, "Cache hit should not assert the RAM upper byte strobe")
    assert(!ram.lds.toBoolean, "Cache hit should not assert the RAM lower byte strobe")

    // Accept the cached result and release the CPU request.
    finishRead()
    assert(!ram.sel.toBoolean, "RAM should remain deselected after the hit")

    // ------- Conflict and replacement -------
    // $1234 and $1A34 share slot $11A, but have different tags.
    // Replace $1234 with $1A34, then verify that reading $1234 misses again.
    for ((address, data) <- Seq((0x1A34L, 0xBEEF), (0x1234L, 0xA55A))) {
      ram.ready #= false
      ram.dataIn #= 0xDEAD
      startRead(address)
      assert(!main.ready.toBoolean, "Conflicting read should stall during lookup")
      assert(!ram.sel.toBoolean, "Conflict lookup should not select RAM yet")

      dut.clockDomain.waitSampling()
      sleep(1)
      assert(!main.ready.toBoolean, "Different tag in the same slot must cause a miss")
      checkRamRead(address)

      // Enter the RAM-wait state before supplying the replacement word.
      dut.clockDomain.waitSampling()
      sleep(1)
      assert(!main.ready.toBoolean, "Conflicting read should wait for RAM")
      checkRamRead(address)
      respondFromRam(data)
      assert(main.ready.toBoolean, "Replacement read should complete with RAM")
      assert(main.dataIn.toInt == data, "Replacement read should return the RAM word")
      finishRead()
      assert(!ram.sel.toBoolean, "RAM should be deselected after replacement")

      // The replacement must now hit, independently of RAM data and readiness.
      ram.ready #= false
      ram.dataIn #= 0xDEAD
      startRead(address)
      assert(!main.ready.toBoolean, "Replacement hit should stall during lookup")
      assert(!ram.sel.toBoolean, "Replacement hit lookup should not select RAM")
      dut.clockDomain.waitSampling()
      sleep(1)
      assert(main.ready.toBoolean, "Replacement word should now hit")
      assert(main.dataIn.toInt == data, "Hit should return the replacement word")
      assert(!ram.sel.toBoolean, "Replacement hit should not access RAM")
      assert(!ram.uds.toBoolean, "Replacement hit should not assert upper RAM strobe")
      assert(!ram.lds.toBoolean, "Replacement hit should not assert lower RAM strobe")
      finishRead()
      assert(!ram.sel.toBoolean, "RAM should remain deselected after replacement hit")
    }
  }
}

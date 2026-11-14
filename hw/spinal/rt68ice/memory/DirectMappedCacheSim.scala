package rt68ice.memory

import spinal.core.sim._

object DirectMappedCacheSim extends App {
  SimConfig.compile(DirectMappedCache()).doSim { dut =>
    dut.clockDomain.forkStimulus(period = 10)
    val main = dut.io.mainBus
    val ram = dut.io.ramBus

    // Start a CPU read; default to both byte lanes for a full word.
    def startRead(address: Long, upper: Boolean = true, lower: Boolean = true): Unit = {
      main.address #= address
      main.wr #= false
      main.uds #= upper
      main.lds #= lower
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

    // Small word-addressed RAM model used by the write tests. Apply the actual
    // downstream write lanes at the completion edge, not the expected test value.
    val ramWords = scala.collection.mutable.Map.empty[Long, Int]

    def releaseCpuRequest(): Unit = {
      main.sel #= false
      main.uds #= false
      main.lds #= false
      main.wr #= false
      sleep(1)
      assert(!ram.sel.toBoolean, "RAM should be deselected after releasing the CPU request")
    }

    def startWrite(address: Long, data: Int, upper: Boolean = true, lower: Boolean = true): Unit = {
      ram.ready #= false
      main.address #= address
      main.dataOut #= data
      main.wr #= true
      main.uds #= upper
      main.lds #= lower
      main.sel #= true
      sleep(1)
    }

    def checkRamWrite(address: Long, data: Int, upper: Boolean, lower: Boolean): Unit = {
      assert(ram.sel.toBoolean, "RAM write should remain selected")
      assert(ram.wr.toBoolean, "RAM direction should be write")
      assert(ram.address.toLong == address, "RAM write address should remain stable")
      assert(ram.dataOut.toInt == data, "RAM write data should remain stable")
      assert(ram.uds.toBoolean == upper, "RAM write should preserve the upper byte strobe")
      assert(ram.lds.toBoolean == lower, "RAM write should preserve the lower byte strobe")
    }

    // Accept a delayed write, keeping the CPU request active for a possible
    // next word. Call releaseCpuRequest only when the caller wants an idle gap.
    def completeRamWrite(address: Long, data: Int,
                         upper: Boolean = true, lower: Boolean = true): Unit = {
      assert(!main.ready.toBoolean, "CPU must stall when starting a RAM write")
      checkRamWrite(address, data, upper, lower)
      for (_ <- 0 until 3) {
        dut.clockDomain.waitSampling()
        sleep(1)
        assert(!main.ready.toBoolean, "CPU must remain stalled until RAM accepts the write")
        checkRamWrite(address, data, upper, lower)
      }

      val wordAddress = ram.address.toLong & ~1L
      val mask = (if (ram.uds.toBoolean) 0xff00 else 0) |
        (if (ram.lds.toBoolean) 0x00ff else 0)
      val oldWord = ramWords.getOrElse(wordAddress, 0)
      val newWord = (oldWord & (~mask & 0xffff)) | (ram.dataOut.toInt & mask)
      ram.ready #= true
      sleep(1)
      assert(main.ready.toBoolean, "CPU write should complete when RAM is ready")
      checkRamWrite(address, data, upper, lower)
      dut.clockDomain.waitSampling()
      ramWords(wordAddress) = newWord
      sleep(1)
    }

    // Read the RAM model through the cache and check whether RAM was accessed.
    // The returned value is also checked against explicit constants by callers.
    def readStoredWord(address: Long, expectHit: Boolean): Int = {
      ram.ready #= false
      ram.dataIn #= 0xDEAD
      startRead(address)
      assert(!main.ready.toBoolean, "CPU read should stall during lookup")
      assert(!ram.sel.toBoolean, "Lookup should not select RAM before its clock edge")
      dut.clockDomain.waitSampling()
      sleep(1)
      if (expectHit) {
        assert(main.ready.toBoolean, "Cached word should hit without waiting for RAM")
        assert(!ram.sel.toBoolean, "Read hit must not access RAM")
      } else {
        assert(!main.ready.toBoolean, "Invalid or unallocated word should miss")
        checkRamRead(address)
        dut.clockDomain.waitSampling()
        sleep(1)
        assert(!main.ready.toBoolean, "Read miss must wait for the RAM response")
        checkRamRead(address)
        respondFromRam(ramWords(address & ~1L))
        assert(main.ready.toBoolean, "Read should complete with the RAM response")
      }
      val result = main.dataIn.toInt
      assert(result == ramWords(address & ~1L), "CPU should see the word stored in RAM")
      finishRead()
      assert(!ram.sel.toBoolean, "RAM should be deselected after reading the stored word")
      result
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

    // ------- Reset invalidation -------

    // Read hits
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

    // Reset
    dut.clockDomain.assertReset()
    dut.clockDomain.waitRisingEdge(2)
    dut.clockDomain.deassertReset()
    dut.clockDomain.waitSampling(2)

    // The previously cached address must miss after reset.
    ram.ready #= false
    ram.dataIn #= 0xDEAD
    startRead(0x1234L)

    assert(!main.ready.toBoolean, "CPU should stall during the post-reset lookup")
    assert(!ram.sel.toBoolean, "Post-reset lookup should not select RAM before the lookup edge")

    dut.clockDomain.waitSampling()
    sleep(1)
    assert(!main.ready.toBoolean, "Reset should invalidate the entry and cause a miss")
    checkRamRead(0x1234L)

    // Enter the RAM-wait state and verify the CPU is still stalled.
    dut.clockDomain.waitSampling()
    sleep(1)
    assert(!main.ready.toBoolean, "Post-reset miss should wait for RAM data")
    checkRamRead(0x1234L)

    // A different word proves this read uses RAM rather than stale cached data.
    respondFromRam(0xCAFE)
    assert(main.ready.toBoolean, "Post-reset read should complete when RAM is ready")
    assert(main.dataIn.toInt == 0xCAFE, "Post-reset read should return the new RAM word")

    // Accept the RAM result and refill, then release the CPU request.
    finishRead()
    assert(!ram.sel.toBoolean, "RAM should be deselected after the post-reset read")

    // ------- Consecutive words without deselection -------
    // First read both uncached words, then repeat with both words cached.
    for (cached <- Seq(false, true)) {
      for ((address, data) <- Seq((0x2000L, 0x1122), (0x2002L, 0x3344))) {
        ram.ready #= false
        ram.dataIn #= 0xDEAD
        startRead(address)
        assert(!main.ready.toBoolean, "Each word should stall during lookup")
        assert(!ram.sel.toBoolean, "Word lookup should not select RAM before its edge")

        dut.clockDomain.waitSampling()
        sleep(1)
        if (cached) {
          assert(main.ready.toBoolean, "Cached consecutive word should hit")
          assert(main.dataIn.toInt == data, "Hit should return the correct consecutive word")
          assert(!ram.sel.toBoolean, "Consecutive hit should not select RAM")
          assert(!ram.uds.toBoolean && !ram.lds.toBoolean,
            "Consecutive hit should not assert RAM strobes")
        } else {
          assert(!main.ready.toBoolean, "Uncached consecutive word should miss")
          checkRamRead(address)
          // Exercise a delayed response for each half of the longword.
          for (_ <- 0 until 2) {
            dut.clockDomain.waitSampling()
            sleep(1)
            assert(!main.ready.toBoolean, "CPU should wait for each consecutive RAM read")
            checkRamRead(address)
          }
          respondFromRam(data)
          assert(main.ready.toBoolean, "Consecutive RAM read should complete")
          assert(main.dataIn.toInt == data, "Miss should return the correct consecutive word")
        }

        assert(main.sel.toBoolean && main.uds.toBoolean && main.lds.toBoolean,
          "CPU selection and both strobes should remain asserted for the longword")
        if (address == 0x2000L) {
          // Accept the first word, then change only the address for the second.
          // Do not call finishRead(): it would deselect the CPU bus.
          dut.clockDomain.waitSampling()
        } else {
          finishRead()
          assert(!ram.sel.toBoolean, "RAM should be deselected after the second word")
        }
      }
    }

    // ------- Byte reads -------
    // Exercise both an even/upper-byte miss and an odd/lower-byte miss.
    // Each miss must fetch a whole word so the other byte can subsequently hit.
    for ((base, firstLower, data) <- Seq((0x3000L, false, 0xA17C), (0x3002L, true, 0x6BD2))) {
      for (cached <- Seq(false, true)) {
        val lower = if (cached) !firstLower else firstLower
        val address = base + (if (lower) 1 else 0)
        val expectedByte = if (lower) data & 0xff else (data >> 8) & 0xff
        ram.ready #= false
        ram.dataIn #= 0xDEAD
        startRead(address, upper = !lower, lower = lower)
        assert(!main.ready.toBoolean, "Byte read should stall during lookup")
        assert(!ram.sel.toBoolean, "Byte lookup should not select RAM before its edge")

        dut.clockDomain.waitSampling()
        sleep(1)
        if (cached) {
          assert(main.ready.toBoolean, "The other byte of the filled word should hit")
          assert(!ram.sel.toBoolean, "Byte hit should not select RAM")
          assert(!ram.uds.toBoolean && !ram.lds.toBoolean,
            "Byte hit should not assert RAM strobes")
        } else {
          assert(!main.ready.toBoolean, "Uncached byte read should miss")
          // SdRamDevice drops address bit 0; both strobes request the entire word.
          checkRamRead(address)
          dut.clockDomain.waitSampling()
          sleep(1)
          assert(!main.ready.toBoolean, "Byte miss should wait for RAM")
          checkRamRead(address)
          respondFromRam(data)
          assert(main.ready.toBoolean, "Byte miss should complete with the RAM response")
        }

        // TG68K selects the requested byte from its original bus lane.
        val receivedByte = if (lower) main.dataIn.toInt & 0xff else (main.dataIn.toInt >> 8) & 0xff
        assert(receivedByte == expectedByte, "Byte read should return the correct byte lane")
        finishRead()
        assert(!ram.sel.toBoolean, "RAM should be deselected after the byte read")
      }

      // Both bytes, including the one not originally requested, must be cached.
      ram.ready #= false
      ram.dataIn #= 0xDEAD
      startRead(base)
      assert(!main.ready.toBoolean, "Word read after byte fill should stall during lookup")
      assert(!ram.sel.toBoolean, "Word lookup after byte fill should not select RAM")
      dut.clockDomain.waitSampling()
      sleep(1)
      assert(main.ready.toBoolean, "Byte fill should make the complete word hit")
      assert(main.dataIn.toInt == data, "Byte fill should preserve both bytes of the word")
      assert(!ram.sel.toBoolean, "Word hit after byte fill should not access RAM")
      finishRead()
      assert(!ram.sel.toBoolean, "RAM should remain deselected after the word hit")
    }

    // ------- Delayed word write-through -------
    ramWords(0x4100L) = 0xFFFF
    startWrite(0x4100L, 0x2468)
    completeRamWrite(0x4100L, 0x2468)
    assert(ramWords(0x4100L) == 0x2468, "Word write must reach RAM")
    releaseCpuRequest()

    // ------- Write invalidates a cached word -------
    ramWords(0x4300L) = 0x1357
    assert(readStoredWord(0x4300L, expectHit = false) == 0x1357)
    assert(readStoredWord(0x4300L, expectHit = true) == 0x1357)
    startWrite(0x4300L, 0xCAFE)
    completeRamWrite(0x4300L, 0xCAFE)
    releaseCpuRequest()
    assert(ramWords(0x4300L) == 0xCAFE, "Cached-address write must update RAM")
    assert(readStoredWord(0x4300L, expectHit = false) == 0xCAFE,
      "Write must invalidate the old cached word")
    assert(readStoredWord(0x4300L, expectHit = true) == 0xCAFE,
      "Refilled entry must contain the new word")

    // ------- Write miss must not allocate -------
    ramWords(0x4400L) = 0xFFFF
    startWrite(0x4400L, 0x789A)
    completeRamWrite(0x4400L, 0x789A)
    releaseCpuRequest()
    assert(ramWords(0x4400L) == 0x789A, "Uncached-address write must update RAM")
    assert(readStoredWord(0x4400L, expectHit = false) == 0x789A,
      "Write miss must not create a valid cache entry")
    assert(readStoredWord(0x4400L, expectHit = true) == 0x789A)

    // ------- Byte writes preserve the unselected RAM byte -------
    ramWords(0x4500L) = 0xA17C
    assert(readStoredWord(0x4500L, expectHit = false) == 0xA17C)
    // The unselected data lane deliberately differs from the preserved byte.
    startWrite(0x4500L, 0xB6E9, upper = true, lower = false)
    completeRamWrite(0x4500L, 0xB6E9, upper = true, lower = false)
    releaseCpuRequest()
    assert(ramWords(0x4500L) == 0xB67C, "Upper byte write must preserve the lower byte")
    assert(readStoredWord(0x4500L, expectHit = false) == 0xB67C,
      "Upper byte write must invalidate the cached word")
    assert(readStoredWord(0x4500L, expectHit = true) == 0xB67C)

    startWrite(0x4501L, 0x5AD2, upper = false, lower = true)
    completeRamWrite(0x4501L, 0x5AD2, upper = false, lower = true)
    releaseCpuRequest()
    assert(ramWords(0x4500L) == 0xB6D2, "Lower byte write must preserve the upper byte")
    assert(readStoredWord(0x4500L, expectHit = false) == 0xB6D2,
      "Odd-address byte write must invalidate the containing cached word")
    assert(readStoredWord(0x4500L, expectHit = true) == 0xB6D2)

    // ------- Consecutive writes without CPU bus deselection -------
    for ((address, data) <- Seq((0x4600L, 0x1122), (0x4602L, 0x3344))) {
      startWrite(address, data)
      completeRamWrite(address, data)
      assert(main.sel.toBoolean && main.uds.toBoolean && main.lds.toBoolean,
        "Selection and both strobes must stay asserted between longword halves")
      assert(ramWords(address) == data, "Each longword half must reach its own RAM address")
      // No release between words: the next startWrite changes address and data
      // after the preceding word has been accepted, while strobes stay high.
    }
    releaseCpuRequest()
    assert(readStoredWord(0x4600L, expectHit = false) == 0x1122)
    assert(readStoredWord(0x4602L, expectHit = false) == 0x3344)

    // ------- Write followed by read without an idle cycle -------
    ramWords(0x4700L) = 0x0F0F
    assert(readStoredWord(0x4700L, expectHit = false) == 0x0F0F)
    startWrite(0x4700L, 0x9ABC)
    completeRamWrite(0x4700L, 0x9ABC)
    assert(main.sel.toBoolean, "CPU request must remain selected before changing direction")
    // readStoredWord changes direction directly to read, without releasing sel.
    assert(readStoredWord(0x4700L, expectHit = false) == 0x9ABC,
      "Immediate read must fetch the completed write rather than stale cached data")
    assert(readStoredWord(0x4700L, expectHit = true) == 0x9ABC)
  }
}

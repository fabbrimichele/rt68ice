package rt68ice.io

import spinal.core.sim._

object CacheStatsDeviceSim extends App {
  SimConfig.compile {
    val dut = CacheStatsDevice()
    dut.counters.foreach(_.simPublic())
    dut
  }.doSim { dut =>
    // Slow stimulus leaves room to read all snapshot words between clock edges.
    dut.clockDomain.forkStimulus(1000)
    val bus = dut.io.bus
    val events = Seq(dut.io.events.readHit, dut.io.events.readMiss,
      dut.io.events.writeRequest, dut.io.events.readStall, dut.io.events.writeStall,
      dut.io.events.readRamWait, dut.io.events.writeRamWait)
    events.foreach(_ #= false)
    bus.address #= 0
    bus.dataOut #= 0
    bus.wr #= false
    bus.sel #= false
    bus.uds #= false
    bus.lds #= false
    dut.clockDomain.waitSampling(2)
    sleep(1)

    def read(offset: Int): Int = {
      bus.address #= 0xF30000L + offset
      bus.sel #= true
      bus.wr #= false
      sleep(1)
      assert(bus.ready.toBoolean && !bus.irq.toBoolean, "CSR accesses must never stall or interrupt")
      bus.dataIn.toInt
    }
    def command(value: Int, upper: Boolean = true, lower: Boolean = true,
                offset: Int = 4, selected: Boolean = true): Unit = {
      bus.address #= 0xF30000L + offset
      bus.dataOut #= value
      bus.wr #= true
      bus.uds #= upper
      bus.lds #= lower
      bus.sel #= selected
      dut.clockDomain.waitSampling()
      sleep(1)
      bus.sel #= false
      bus.wr #= false
    }
    def count(index: Int): BigInt = (0 until 4).foldLeft(BigInt(0)) { (n, word) =>
      (n << 16) | BigInt(read(0x20 + index * 8 + word * 2))
    }
    def ticks(n: Int): Unit = { dut.clockDomain.waitSampling(n); sleep(1) }

    assert(read(0) == 0x4343 && read(2) == 1, "Hardware ID/version must match CACHESTAT")
    assert(((read(12).toLong << 16) | read(14).toLong) == 25000000L, "Report the counter clock")
    assert(read(4) == 0 && read(6) == 0, "Reset must disable counting and invalidate snapshots")
    assert(read(0x100) == 0, "Unused offsets must read zero")
    events.foreach(_ #= true)
    ticks(3)
    command(4) // Snapshot while disabled.
    assert((0 until 8).forall(count(_) == 0), "Disabled events must not increment counters")

    command(3) // Clear and start, atomically.
    assert(read(4) == 1 && read(6) == 0, "Start must clear the counter bank")
    // Deliberately drive some events for more than one cycle (stall semantics).
    events.foreach(_ #= false)
    dut.io.events.readHit #= true
    dut.io.events.readStall #= true
    ticks(3)
    events.foreach(_ #= false)
    dut.io.events.readMiss #= true
    dut.io.events.writeRequest #= true
    dut.io.events.writeStall #= true
    dut.io.events.readRamWait #= true
    dut.io.events.writeRamWait #= true
    ticks(2)
    command(4) // Stop and snapshot, excluding the command edge.
    val expected = Seq(5, 3, 2, 2, 3, 2, 2, 2).map(BigInt(_))
    assert((0 until 8).map(count) == expected, "Events must count each qualified cycle exactly")
    assert(read(4) == 0 && read(6) == 3, "Snapshot records valid state and previous RUN")
    events.foreach(_ #= true)
    ticks(3)
    assert((0 until 8).map(count) == expected, "Frozen values must not change during reads")
    command(4)
    assert((0 until 8).map(count) == expected, "Stopped counters must remain frozen too")

    // Partial writes, read-only writes, or unselected accesses cannot change control.
    command(3, lower = false)
    command(3, upper = false)
    command(3, offset = 0)
    command(3, selected = false)
    assert(read(4) == 0 && count(0) == 5, "Only a selected full-word control write may act")
    events.foreach(_ #= false)
    command(1) // Resume without clear.
    ticks(2)
    assert(count(0) == 0 && read(6) % 2 == 0,
      "Running values must be hidden until frozen, avoiding torn reads")
    command(5) // SNAPSHOT takes priority over RUN: freeze the bank in place.
    assert(count(0) == 7 && count(1) == 3 && read(4) == 0,
      "Resume retains counts and every snapshot freezes atomically")
    ticks(2)
    assert(count(0) == 7, "Frozen bank must not change while it is read")
    command(1)
    ticks(2)
    command(4)
    assert(count(0) == 9, "Resume must continue from the frozen values")

    // Exercise carry and big-endian word ordering beyond both 16 and 32 bits.
    command(1)
    dut.counters(0) #= BigInt("12345678ffffffff", 16)
    ticks(1)
    command(4)
    assert(count(0) == BigInt("1234567900000000", 16), "64-bit carry/snapshot word order is wrong")
    command(2)
    assert((0 until 8).forall(count(_) == 0) && read(6) == 0,
      "Clear must reset counters and invalidate the previous snapshot")
    dut.clockDomain.assertReset()
    dut.clockDomain.waitRisingEdge(2)
    dut.clockDomain.deassertReset()
    dut.clockDomain.waitSampling(2)
    assert(read(4) == 0 && read(6) == 0, "Reset must restore disabled/invalid state")
  }
}

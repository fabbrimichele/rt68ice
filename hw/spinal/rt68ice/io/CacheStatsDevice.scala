package rt68ice.io

import rt68ice.core.M68KDeviceBus
import rt68ice.memory.CacheEvents
import spinal.core._
import spinal.lib._

/** Eight 64-bit counters; freezing in place gives coherent 16-bit bus reads. */
case class CacheStatsDevice(clockHz: Long = 25000000L) extends Component {
  require(clockHz > 0 && clockHz <= 0xffffffffL)
  val io = new Bundle {
    val bus = slave(M68KDeviceBus())
    val events = in(CacheEvents())
  }

  val enabled = RegInit(False)
  val snapshotValid = RegInit(False)
  val snapshotRunning = RegInit(False)
  val counters = Vec.fill(8)(Reg(UInt(64 bits)) init 0)
  val increments = Seq(True, io.events.readHit, io.events.readMiss,
    io.events.writeRequest, io.events.readStall, io.events.writeStall,
    io.events.readRamWait, io.events.writeRamWait)
  val wordAddress = io.bus.address(13 downto 1).asUInt
  // BusController supplies a qualified write pulse for this standard peripheral.
  // Require a word write: partial control writes must not clear counters.
  val command = io.bus.sel && io.bus.wr && io.bus.uds && io.bus.lds && (wordAddress === 2)
  when(command) {
    enabled := io.bus.dataOut(0)
    when(io.bus.dataOut(1)) {
      counters.foreach(_ := 0)
      snapshotValid := False
      snapshotRunning := False
    } elsewhen(io.bus.dataOut(2)) {
      // Freeze in place instead of duplicating all 512 bits of counter storage.
      enabled := False
      snapshotValid := True
      snapshotRunning := enabled
    } elsewhen(io.bus.dataOut(0)) {
      snapshotValid := False
    }
  } elsewhen(enabled) {
    for (i <- 0 until 8) {
      when(increments(i)) { counters(i) := counters(i) + 1 }
    }
  }

  io.bus.ready := True
  io.bus.irq := False
  io.bus.dataIn := 0
  when(io.bus.sel && !io.bus.wr) {
    switch(wordAddress) {
      is(0) { io.bus.dataIn := B(0x4343, 16 bits) } // "CC"
      is(1) { io.bus.dataIn := B(1, 16 bits) }       // Register ABI version
      is(2) { io.bus.dataIn := enabled.asBits.resized }
      is(3) { io.bus.dataIn := (snapshotRunning ## snapshotValid).resized }
      is(6) { io.bus.dataIn := B((clockHz >> 16) & 0xffffL, 16 bits) }
      is(7) { io.bus.dataIn := B(clockHz & 0xffffL, 16 bits) }
      for (counter <- 0 until 8; word <- 0 until 4) {
        is(16 + counter * 4 + word) {
          // Hide changing values until stopped/snapshotted; avoid torn reads.
          when(snapshotValid && !enabled) {
            io.bus.dataIn := counters(counter)(63 - word * 16 downto 48 - word * 16).asBits
          }
        }
      }
    }
  }
}

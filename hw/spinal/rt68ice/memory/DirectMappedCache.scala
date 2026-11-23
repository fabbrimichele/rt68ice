package rt68ice.memory

import rt68ice.core.M68KDeviceBus
import spinal.core._
import spinal.lib._
import spinal.lib.fsm._

import scala.language.postfixOps

/*
Four-word direct-mapped cache: 1,024 lines, 8 KiB of data.
Address bits: tag [24:13], slot [12:3], word offset [2:1], byte offset [0].
Each memory entry stores 76 bits: 12-bit tag + 64-bit line data.
A separate 1,024-bit register holds one valid bit per line.
In the packed data, word 0 is bits [15:0], word 3 is bits [63:48].

Read misses fetch an eight-byte-aligned SDRAM burst and cache all four words.
The original CPU address selects the returned word on both misses and hits.
Writes are write-through with one posted entry holding address, data and byte
strobes until SDRAM completion. After lookup, a write hit updates only the
selected cached word or byte(s), preserving its tag, other data and valid bit;
a write miss leaves the cache unchanged and does not allocate. The CPU is
acknowledged after lookup/update while SDRAM continues independently. Read
hits may proceed; another write or a read miss waits for the entry to drain.
Addresses $1234 and $3234 share slot $246 and word offset 2, but differ in tag.
*/

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class DirectMappedCache() extends Component {
  val io = new Bundle {
    val mainBus = slave(M68KDeviceBus())
    val ramBus = master(M68KDeviceBus())
    val ramReadLineData  = in(Bits(64 bits))
    val events = out(CacheEvents())
  }

  val active = io.mainBus.sel && (io.mainBus.uds || io.mainBus.lds)
  val isWrite = active && io.mainBus.wr
  val isRead = active && !io.mainBus.wr
  val cache = Mem(Bits(76 bits), 1024)
  val valid = Reg(Bits(1024 bits)) init 0

  // Capture the current CPU request in the system clock domain. Reads retain
  // it through completion; posted writes use the separate pending entry below.
  // No live CPU address/data path crosses to SDRAM.
  val requestAddress = Reg(Bits(32 bits)) init 0
  val requestData = Reg(Bits(16 bits)) init 0
  val requestWrite = Reg(Bool()) init False
  val requestUds = Reg(Bool()) init False
  val requestLds = Reg(Bool()) init False
  val ramSelected = Reg(Bool()) init False

  // Capture the pending request (required by the write buffer)
  val pendingWriteAddress = Reg(Bits(32 bits)) init 0
  val pendingWriteData = Reg(Bits(16 bits)) init 0
  val pendingWriteUds = Reg(Bool()) init False
  val pendingWriteLds = Reg(Bool()) init False
  val writePending = Reg(Bool()) init False

  // Qualify completion with the current request: RAM is also ready while idle.
  // Sample data and completion together before using them in the cache/CPU path.
  val responseReady = RegNext(ramSelected && io.ramBus.ready) init False
  val responseData = Reg(Bits(64 bits)) init 0
  when(ramSelected && io.ramBus.ready) {
    responseData := io.ramReadLineData
  }
  val tag = requestAddress(24 downto 13)
  val requestSlot = requestAddress(12 downto 3).asUInt
  val wordOffset = requestAddress(2 downto 1).asUInt
  val isValid = Reg(Bool())
  val readEnable = Bool()
  val writeEnable = Bool()

  val slot = io.mainBus.address(12 downto 3).asUInt
  val line = cache.readSync(slot, readEnable)

  val updateWriteHit = Bool()
  val writeData = Bits(64 bits)
  when(updateWriteHit) {
    val lineWords = line(63 downto 0).subdivideIn(16 bits)

    // Copy the old word and line first, preserving all untouched bytes and words.
    val updatedWord = Bits(16 bits)
    updatedWord := lineWords(wordOffset)
    when (requestUds) {
      updatedWord(15 downto 8) := requestData.takeHigh(8)
    }
    when (requestLds) {
      updatedWord(7 downto 0) := requestData.takeLow(8)
    }

    val updatedWords = Vec.fill(4)(Bits(16 bits))
    updatedWords := lineWords
    updatedWords(wordOffset) := updatedWord

    writeData := updatedWords.asBits
  } otherwise {
    writeData := responseData
  }

  cache.write(requestSlot, tag ## writeData, writeEnable)

  io.mainBus.irq := False
  io.mainBus.dataIn := 0
  io.mainBus.ready := True
  io.ramBus.sel := ramSelected
  when (writePending) {
    io.ramBus.address := pendingWriteAddress
    io.ramBus.dataOut := pendingWriteData
    io.ramBus.wr := ramSelected
    io.ramBus.uds := ramSelected && pendingWriteUds
    io.ramBus.lds := ramSelected && pendingWriteLds
  } otherwise {
    io.ramBus.address := requestAddress(31 downto 3) ## B"000"
    io.ramBus.dataOut := requestData
    io.ramBus.wr := False
    io.ramBus.uds := ramSelected && requestUds
    io.ramBus.lds := ramSelected && requestLds
  }

  when (writePending && responseReady) {
    // Write done, release RAM
    writePending := False
    ramSelected := False
  }

  readEnable := False
  writeEnable := False
  updateWriteHit := False
  io.events.readHit := False
  io.events.readMiss := False
  io.events.writeRequest := False
  // Total CPU stalls include lookup/launch; RAM-wait events below are a subset.
  io.events.readStall := isRead && !io.mainBus.ready
  io.events.writeStall := isWrite && !io.mainBus.ready
  io.events.readRamWait := False
  io.events.writeRamWait := False

  new StateMachine {
    val idle = new State with EntryPoint
    val readLookup = new State
    val readDramWait = new State
    val writeDramWait = new State

    idle.whenIsActive {
      when(active) {
        requestAddress := io.mainBus.address
        requestData := io.mainBus.dataOut
        requestWrite := io.mainBus.wr
        // Read misses fill a complete four-word line even for CPU byte reads.
        requestUds := io.mainBus.uds || isRead
        requestLds := io.mainBus.lds || isRead
        isValid := valid(slot)
      }
      when(isRead) {
        // Read
        io.mainBus.ready := False
        readEnable := True // Read from cache sync
        goto(readLookup)
      } elsewhen isWrite {
        // Write
        io.mainBus.ready := False

        when (!writePending) {
          // Start a new write only when the previous one is done
          io.events.writeRequest := True
          readEnable := True // Read from cache sync

          // Required by buffered write
          pendingWriteAddress := io.mainBus.address
          pendingWriteData := io.mainBus.dataOut
          pendingWriteUds := io.mainBus.uds
          pendingWriteLds := io.mainBus.lds
          writePending := True

          // Start SDRAM write
          ramSelected := True
          goto(writeDramWait)
        }
      }
    }

    writeDramWait.whenIsActive {
      val lineTag = line(75 downto 64)
      io.events.writeRamWait := !responseReady

      // Let CPU and write continue in parallel
      when(isValid && lineTag === tag) {
        // hit, update the cache
        updateWriteHit := True
        writeEnable := True
      }

      io.mainBus.ready := True
      goto(idle)
    }

    readLookup.whenIsActive {
      val lineTag = line(75 downto 64)
      val lineData = line(63 downto 0)

      when(isValid && lineTag === tag) {
        // Hit
        io.events.readHit := True
        io.mainBus.dataIn := wordOffset.mux(
          0 -> lineData(15 downto 0),
          1 -> lineData(31 downto 16),
          2 -> lineData(47 downto 32),
          3 -> lineData(63 downto 48),
        )
        goto(idle)
      } otherwise {
        // Miss
        io.mainBus.ready := False

        // Wait until SDRAM is free
        when (!writePending) {
          io.events.readMiss := True
          // Start SDRAM only after the lookup has established a miss. Selection
          // is registered too, so the tag comparator does not cross domains.
          ramSelected := True
          goto(readDramWait)
        }
      }
    }

    readDramWait.whenIsActive {
      io.mainBus.ready := False
      io.events.readRamWait := !responseReady
      when(responseReady) {
        ramSelected := False
        io.mainBus.ready := True
        writeEnable := True
        valid(requestSlot) := True
        io.mainBus.dataIn := wordOffset.mux(
          0 -> responseData(15 downto 0),
          1 -> responseData(31 downto 16),
          2 -> responseData(47 downto 32),
          3 -> responseData(63 downto 48),
        )
        goto(idle)
      }
    }
  }
}

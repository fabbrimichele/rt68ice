package rt68ice.memory

import rt68ice.core.M68KDeviceBus
import spinal.core._
import spinal.lib._
import spinal.lib.fsm._

import scala.language.postfixOps

/*
Four-word direct-mapped cache: 1,024 lines, 8 KiB of data.
Address bits: tag [23:13], slot [12:3], word offset [2:1], byte offset [0].
Each memory entry stores 75 bits: 11-bit tag + 64-bit line data.
A separate 1,024-bit register holds one valid bit per line.
In the packed data, word 0 is bits [15:0], word 3 is bits [63:48].

Read misses fetch an eight-byte-aligned SDRAM burst and cache all four words.
The original CPU address selects the returned word on both misses and hits.
Writes keep their original address and byte strobes, reach SDRAM, and
invalidate the entire selected cache line without allocating a new line.
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
  val cache = Mem(Bits(75 bits), 1024)
  val valid = Reg(Bits(1024 bits)) init 0
  // Capture the entire downstream request in the system clock domain. Keep it
  // stable until completion; no live CPU address/data path crosses to SDRAM.
  val requestAddress = Reg(Bits(32 bits)) init 0
  val requestData = Reg(Bits(16 bits)) init 0
  val requestWrite = Reg(Bool()) init False
  val requestUds = Reg(Bool()) init False
  val requestLds = Reg(Bool()) init False
  val ramSelected = Reg(Bool()) init False
  // Qualify completion with the current request: RAM is also ready while idle.
  // Sample data and completion together before using them in the cache/CPU path.
  val responseReady = RegNext(ramSelected && io.ramBus.ready) init False
  val responseData = Reg(Bits(64 bits)) init 0
  when(ramSelected && io.ramBus.ready) {
    responseData := io.ramReadLineData
  }
  val tag = requestAddress(23 downto 13)
  val requestSlot = requestAddress(12 downto 3).asUInt
  val wordOffset = requestAddress(2 downto 1).asUInt
  val isValid = Reg(Bool())
  val readEnable = Bool()
  val writeEnable = Bool()

  val slot = io.mainBus.address(12 downto 3).asUInt
  val line = cache.readSync(slot, readEnable)
  cache.write(requestSlot, tag ## responseData, writeEnable)

  io.mainBus.irq := False
  io.mainBus.dataIn := 0
  io.mainBus.ready := True
  io.ramBus.sel := ramSelected
  when (requestWrite) {
    io.ramBus.address := requestAddress
  } otherwise {
    io.ramBus.address := (requestAddress(23 downto 3) ## B"000").resized
  }

  io.ramBus.dataOut := requestData
  io.ramBus.wr := ramSelected && requestWrite
  io.ramBus.uds := ramSelected && requestUds
  io.ramBus.lds := ramSelected && requestLds

  readEnable := False
  writeEnable := False
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
        io.events.writeRequest := True
        readEnable := True // Read from cache sync
        io.mainBus.ready := False
        ramSelected := True
        goto(writeDramWait)
      }
    }

    writeDramWait.whenIsActive {
      val lineTag = line(74 downto 64)
      io.mainBus.ready := False
      io.events.writeRamWait := !responseReady

      when(responseReady) {
        ramSelected := False
        when(isValid && lineTag === tag) {
          // hit, invalidate the line
          valid(requestSlot) := False
        }
        io.mainBus.ready := True
        goto(idle)
      }
    }

    readLookup.whenIsActive {
      val lineTag = line(74 downto 64)
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
        io.events.readMiss := True
        // Miss
        io.mainBus.ready := False
        // Start SDRAM only after the lookup has established a miss. Selection
        // is registered too, so the tag comparator does not cross domains.
        ramSelected := True
        goto(readDramWait)
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

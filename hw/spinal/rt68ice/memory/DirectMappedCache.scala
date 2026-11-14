package rt68ice.memory

import rt68ice.core.M68KDeviceBus
import spinal.core._
import spinal.lib._
import spinal.lib.fsm._

import scala.language.postfixOps

/*
| Entry / slot | Valid | Stored tag | Cached data | SDRAM word address represented |
|-------------:|:-----:|-----------:|------------:|-------------------------------:|
|            0 |     1 |      $0002 |       $4E71 |                        $001000 |
|            1 |     1 |      $0002 |       $7000 |                        $001002 |
|            2 |     1 |      $0003 |       $1234 |                        $001804 |
|            3 |     0 | Irrelevant |  Irrelevant |                           None |
|            … |     … |          … |           … |                              … |
|         1023 |     1 |      $0002 |       $ABCD |                        $0017FE |

For a read at $001004:
- Slot = 2, requested tag = $0002.
- Entry 2 contains tag $0003, so it’s a miss.
- Read $001004 from SDRAM.
- Replace entry 2’s tag with $0002 and its data with the returned word; set valid to 1.

Each entry stores 30 bits: 16 data + 13 tag + 1 valid. Thus, 2 KiB of cached data needs 3.75 KiB of logical storage, before FPGA memory packing.
*/

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class DirectMappedCache() extends Component {
  val io = new Bundle {
    val mainBus = slave(M68KDeviceBus())
    val ramBus = master(M68KDeviceBus())
  }

  val active = io.mainBus.sel && (io.mainBus.uds || io.mainBus.lds)
  val isWrite = active && io.mainBus.wr
  val isRead = active && !io.mainBus.wr
  val cache = Mem(Bits(29 bits), 1024)
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
  val responseData = Reg(Bits(16 bits)) init 0
  when(ramSelected && io.ramBus.ready) {
    responseData := io.ramBus.dataIn
  }
  val tag = requestAddress(23 downto 11)
  val requestSlot = requestAddress(10 downto 1).asUInt
  val isValid = Reg(Bool())
  val readEnable = Bool()
  val writeEnable = Bool()

  val slot = io.mainBus.address(10 downto 1).asUInt
  val line = cache.readSync(slot, readEnable)
  cache.write(requestSlot, tag ## responseData, writeEnable)

  io.mainBus.irq := False
  io.mainBus.dataIn := 0
  io.mainBus.ready := True
  io.ramBus.sel := ramSelected
  io.ramBus.address := requestAddress
  io.ramBus.dataOut := requestData
  io.ramBus.wr := ramSelected && requestWrite
  io.ramBus.uds := ramSelected && requestUds
  io.ramBus.lds := ramSelected && requestLds

  readEnable := False
  writeEnable := False

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
        // Read misses fill a complete word even for CPU byte reads.
        requestUds := io.mainBus.uds || isRead
        requestLds := io.mainBus.lds || isRead
      }
      when(isRead) {
        // Read
        io.mainBus.ready := False
        isValid := valid(slot)
        readEnable := True // Read from cache sync
        goto(readLookup)
      } elsewhen isWrite {
        // Write
        io.mainBus.ready := False
        ramSelected := True
        goto(writeDramWait)
      }
    }

    writeDramWait.whenIsActive {
      io.mainBus.ready := False
      when(responseReady) {
        ramSelected := False
        valid(requestSlot) := False
        io.mainBus.ready := True
        goto(idle)
      }
    }

    readLookup.whenIsActive {
      val lineTag = line(28 downto 16)
      val lineData = line(15 downto 0)

      when(isValid && lineTag === tag) {
        // Hit
        io.mainBus.dataIn := lineData
        goto(idle)
      } otherwise {
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
      when(responseReady) {
        ramSelected := False
        io.mainBus.ready := True
        writeEnable := True
        valid(requestSlot) := True
        io.mainBus.dataIn := responseData
        goto(idle)
      }
    }
  }
}

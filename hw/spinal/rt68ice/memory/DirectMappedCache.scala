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
  val tag = Reg(Bits(13 bits))
  val isValid = Reg(Bool())
  val readEnable = Bool()
  val writeEnable = Bool()

  val slot = io.mainBus.address(10 downto 1).asUInt
  val line = cache.readSync(slot, readEnable)
  cache.write(slot, tag ## io.ramBus.dataIn, writeEnable)

  io.mainBus.irq := False
  io.mainBus.dataIn := 0
  io.mainBus.ready := True
  io.ramBus.sel := False
  io.ramBus.address := io.mainBus.address
  io.ramBus.dataOut := 0
  io.ramBus.wr := False
  io.ramBus.uds := False
  io.ramBus.lds := False

  readEnable := False
  writeEnable := False

  new StateMachine {
    val idle = new State with EntryPoint
    val readLookup = new State
    val readDramWait = new State
    val writeDramWait = new State

    idle.whenIsActive {
      when(isRead) {
        // Read
        io.mainBus.ready := False
        tag := io.mainBus.address(23 downto 11)
        isValid := valid(slot)
        readEnable := True // Read from cache sync
        goto(readLookup)
      } elsewhen isWrite {
        // Write
        io.mainBus.ready := False
        io.ramBus.sel := True
        io.ramBus.wr := True
        io.ramBus.lds := io.mainBus.lds
        io.ramBus.uds := io.mainBus.uds
        io.ramBus.dataOut := io.mainBus.dataOut
        goto(writeDramWait)
      }
    }

    writeDramWait.whenIsActive {
      io.mainBus.ready := False
      io.ramBus.sel := True
      io.ramBus.wr := True
      io.ramBus.lds := io.mainBus.lds
      io.ramBus.uds := io.mainBus.uds
      io.ramBus.dataOut := io.mainBus.dataOut

      when(io.ramBus.ready) {
        valid(slot) := False
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
        io.ramBus.sel := True
        io.ramBus.lds := True
        io.ramBus.uds := True
        goto(readDramWait)
      }
    }

    readDramWait.whenIsActive {
      io.mainBus.ready := False
      io.ramBus.sel := True
      io.ramBus.lds := True
      io.ramBus.uds := True
      when(io.ramBus.ready) {
        io.mainBus.ready := True
        writeEnable := True
        valid(slot) := True
        io.mainBus.dataIn := io.ramBus.dataIn
        goto(idle)
      }
    }
  }
}

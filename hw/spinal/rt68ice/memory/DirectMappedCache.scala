package rt68ice.memory

import rt68ice.core.M68KDeviceBus
import spinal.core._
import spinal.lib._
import scala.language.postfixOps

/*
| Entry / slot | Valid | Stored tag | Cached data | SDRAM word address represented |
|---:|:---:|---:|---:|---:|
| 0 | 1 | $0002 | $4E71 | $001000 |
| 1 | 1 | $0002 | $7000 | $001002 |
| 2 | 1 | $0003 | $1234 | $001804 |
| 3 | 0 | Irrelevant | Irrelevant | None |
| … | … | … | … | … |
| 1023 | 1 | $0002 | $ABCD | $0017FE |

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

  val cache = Mem(Bits(30 bits), 1024)

  val active = io.mainBus.sel && (io.mainBus.uds || io.mainBus.lds)
  val isWrite = active && io.mainBus.wr
  val isRead = active && !io.mainBus.wr

  io.mainBus <> io.ramBus
}

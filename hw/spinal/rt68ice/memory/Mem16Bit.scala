package rt68ice.memory

import rt68ice.core.M68KBus
import rt68ice.core.M68KBus.DATA_WIDTH
import spinal.core._
import spinal.lib._

import scala.io.Source
import scala.language.postfixOps
import scala.util.Using

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class Mem16Bit(sizeInWords: Int, initFile: Option[String] = None, readOnly: Boolean = false) extends Component {
  val io = new Bundle {
    val bus     = slave(M68KBus())
    val sel     = in Bool()
  }

  val mem = Mem(Bits(DATA_WIDTH bits), sizeInWords)
  initFile.foreach { filename => mem.init(readContentFromFile(filename)) }

  io.bus.dataIn := mem.readWriteSync(
    address = io.bus.address(log2Up(sizeInWords) downto 1).asUInt,
    data = io.bus.dataOut,
    enable = io.sel,
    write = io.bus.wr && !Bool(readOnly),
    mask = io.bus.uds ## io.bus.lds,
  )

  private def readContentFromFile(initFile: String) = {
    val romFile = getClass.getResourceAsStream(initFile)
    val romContent = Using.resource(romFile) { stream =>
      val source = Source.fromInputStream(stream)
      try {
        source.getLines()
          .map { line => B(java.lang.Long.parseLong(line.trim, 16), 16 bits) }
          .toSeq
      } finally source.close()
    }
    assert(romContent.size <= sizeInWords, s"ROM content file greater than $sizeInWords")
    romContent ++ Seq.fill(sizeInWords - romContent.size)(B(0, 16 bits))
  }
}

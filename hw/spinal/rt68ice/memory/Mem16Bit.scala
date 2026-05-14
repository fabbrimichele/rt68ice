package rt68ice.memory

import spinal.core._

import scala.io.Source
import scala.language.postfixOps
import scala.util.Using

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class Mem16Bit(sizeInWords: Int, initFile: Option[String] = None, readOnly: Boolean = false) extends Component {
  val io = new Bundle {
    val sel     = in Bool()
    val wr      = in Bool()
    val address = in Bits(32 bits)
    val dataOut = out Bits(16 bits)
    val dataIn  = in Bits(16 bits)
    val uds     = in Bool()
    val lds     = in Bool()
  }

  val mem = Mem(Bits(16 bits), sizeInWords)
  initFile.foreach { filename => mem.init(readContentFromFile(filename)) }

  io.dataOut := mem.readWriteSync(
    address = io.address(log2Up(sizeInWords) downto 1).asUInt,
    data = io.dataIn,
    enable = io.sel,
    write = io.wr && !Bool(readOnly),
    mask = io.uds ## io.lds,
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

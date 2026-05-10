package rt68ice.memory

import spinal.core._

import scala.io.Source
import scala.language.postfixOps
import scala.util.Using

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class Mem16Bit(size: Int, initFile: Option[String] = None) extends Component {
  val io = new Bundle {
    val sel     = in Bool()
    val address = in Bits(32 bits)
    val dataOut = out Bits(16 bits)
  }

  val mem = Mem(Bits(16 bits), size)
  initFile.foreach { filename => mem.init(readContentFromFile(filename)) }

  io.dataOut := mem.readSync(
    address = io.address(log2Up(size) downto 1).asUInt,
    enable = io.sel
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
    assert(romContent.size <= size, s"ROM content file greater than $size")
    romContent ++ Seq.fill(size - romContent.size)(B(0, 16 bits))
  }
}

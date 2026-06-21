package rt68ice.timer

import rt68ice.core.M68KBus
import spinal.core._
import spinal.lib._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class Counter() extends Component {
  val io = new Bundle {
    val bus = slave(M68KBus())
    val sel = in Bool()
  }

  // Free running 32-bit counter
  val counter = Reg(UInt(32 bits)) init 0
  counter := counter + 1

  // Latch to prevent tearing across a 16-bit bus read
  val counterLatch = Reg(Bits(16 bits)) init 0

  io.bus.dataIn := 0

  when(io.sel && !io.bus.wr) {
    val addrWord = io.bus.address(2 downto 1) // We only care about word alignment

    switch(addrWord) {
      is(0) {
        // 1. CPU reads the HIGH word.
        // We output the top 16 bits and instantly latch the bottom 16 bits.
        io.bus.dataIn := counter(31 downto 16).asBits
        counterLatch  := counter(15 downto 0).asBits
      }
      is(1) {
        // 2. CPU reads the LOW word.
        // We output the latched value so it aligns perfectly with the HIGH word.
        io.bus.dataIn := counterLatch
      }
    }
  }
}
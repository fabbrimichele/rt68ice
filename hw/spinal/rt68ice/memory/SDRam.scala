package rt68ice.memory

import rt68ice.core.M68KBus
import spinal.core.{Bundle, Component, in}
import spinal.lib.slave

case class SDRam() extends Component {
  val io = new Bundle {
    val bus     = slave(M68KBus())
    val sel     = in Bool()
  }

  spinal.lib.memory.sdram.sdr.SdramCtrl
}

package rt68ice.io

import rt68ice.core.M68KBus
import spinal.core.{Bool, Bundle, Component, IntToBuilder, in, out}
import spinal.lib.{master, slave}

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class Ds1302Device() extends Component {
  val io = new Bundle {
    val bus = slave(M68KBus())
    val sel = in Bool()
    val cpuClkEn = out Bool()
    val ds1302 = master(Ds1302())
  }

  // TODO:
  //  - sue busy signal, convert it to cpuEnable?
  //    or should it just busy also in the other peripherals?
  val ds1302 = new Ds1302BB()

  // External interface
  io.ds1302.ce := ds1302.io.CE
  io.ds1302.sclk := ds1302.io.SCLK
  io.ds1302.io <> ds1302.io.IO

  // 68000 bus interface
  ds1302.io.nCE := !io.sel
  ds1302.io.nRD := io.bus.wr
  ds1302.io.nWR := !io.bus.wr
  io.cpuClkEn := !ds1302.io.BUSY
  ds1302.io.A := io.bus.address.resized
  // Data bus mapping (access by byte, i.e. move.b)
  ds1302.io.DI := io.bus.dataOut(15 downto 8)
  io.bus.dataIn(15 downto 8) := ds1302.io.DO
  io.bus.dataIn(7 downto 0) := 0
}

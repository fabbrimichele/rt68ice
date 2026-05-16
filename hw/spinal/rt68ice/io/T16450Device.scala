package rt68ice.io

import rt68ice.core.M68KBus
import spinal.core._
import spinal.lib.com.uart.Uart
import spinal.lib._

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class T16450Device() extends Component {
  val io = new Bundle {
    val bus = slave(M68KBus())
    val sel = in Bool() // chip select from decoder
    val int = out Bool() // UART interrupt
    val uart = master(Uart())
  }

  val uart = new T16450BB()

  // Default Settings (Copied from MG68)
  uart.io.RClk := uart.io.BaudOut
  uart.io.CS_n := !io.sel
  uart.io.Rd_n := io.bus.wr
  uart.io.Wr_n := !io.bus.wr
  uart.io.A := io.bus.address(3 downto 1)
  uart.io.D_In := io.bus.dataOut(7 downto 0)
  io.bus.dataIn := uart.io.D_Out.resized
  uart.io.SIn := io.uart.rxd
  uart.io.CTS_n := False
  uart.io.DSR_n := False
  uart.io.RI_n := False
  uart.io.DCD_n := False
  io.uart.txd := uart.io.SOut
  io.int := uart.io.Intr
}

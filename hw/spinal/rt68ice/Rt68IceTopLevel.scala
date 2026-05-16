package rt68ice

import rt68ice.core._
import rt68ice.io.{LedDevice, T16450Device}
import rt68ice.memory.Mem16Bit
import spinal.core._
import spinal.lib.com.uart.Uart
import spinal.lib.master

import scala.annotation.unused
import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class Rt68IceTopLevel(romFile: String) extends Component {
  val io = new Bundle {
    val led = out Bits(3 bits)
    val uart = master(Uart()) // Expose UART pins (txd, rxd), must be defined in the constraints file
  }

  val clockCtrl = ClockCtrl()

  // Area with reset
  @unused
  val coreArea = new ClockingArea(clockCtrl.clk20Domain) {
    // Bus Controller
    val bus = new BusController

    // CPU
    val cpu = new M68K
    cpu.io.ipl := B"111"
    cpu.io.busErr := False
    cpu.io.clockEn := bus.io.clockEn
    bus.io.busState := cpu.io.busState
    bus.io.cpuBus <> cpu.io.bus

    // ROM
    val rom = Mem16Bit(sizeInWords = 8192, initFile = Some(romFile), readOnly = true)
    rom.io.sel := bus.io.romSel
    bus.io.romBus  <> rom.io.bus

    // RAM
    val ram = Mem16Bit(sizeInWords = 8192)
    ram.io.sel := bus.io.ramSel
    bus.io.ramBus <> ram.io.bus

    // LED Device
    val ledDevice = LedDevice()
    ledDevice.io.sel := bus.io.ledSel
    io.led := ledDevice.io.led
    bus.io.ledBus <> ledDevice.io.bus

    // UART Device
    val uartDevice = T16450Device()
    uartDevice.io.uart <> io.uart
    uartDevice.io.sel := bus.io.uartSel
    bus.io.uartBus <> uartDevice.io.bus
  }

  // Remove io_ prefix
  noIoPrefix()
}

object Rt68IceTopLevelVerilog extends App {
  private val report = Config.spinal.generateVerilog(Rt68IceTopLevel(romFile = "serial_echo.hex"))
  report.mergeRTLSource("mergeRTL") // Merge all rtl sources into mergeRTL.vhd and mergeRTL.v files
}


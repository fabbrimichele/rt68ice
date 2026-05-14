package rt68ice

import rt68ice.core._
import rt68ice.io.LedDevice
import rt68ice.memory.Mem16Bit
import spinal.core._

import scala.annotation.unused
import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class Rt68IceTopLevel(romFile: String) extends Component {
  val io = new Bundle {
    val led = out Bits(3 bits)
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
    bus.io.cpuBus <> cpu.io.bus
    bus.io.busState := cpu.io.busState

    // ROM
    val rom = Mem16Bit(sizeInWords = 1024, initFile = Some(romFile), readOnly = true)
    rom.io.sel := bus.io.romSel
    bus.io.romBus  <> rom.io.bus

    // RAM
    val ram = Mem16Bit(sizeInWords = 1024)
    ram.io.sel := bus.io.ramSel
    bus.io.ramBus <> ram.io.bus

    // LED Device
    val ledDevice = LedDevice()
    ledDevice.io.sel := bus.io.ledSel
    io.led := ledDevice.io.led
    bus.io.ledBus <> ledDevice.io.bus
  }
}

object Rt68IceTopLevelVerilog extends App {
  private val report = Config.spinal.generateVerilog(Rt68IceTopLevel(romFile = "mem_test.hex"))
  report.mergeRTLSource("mergeRTL") // Merge all rtl sources into mergeRTL.vhd and mergeRTL.v files
}


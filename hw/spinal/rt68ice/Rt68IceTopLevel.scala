package rt68ice

import rt68ice.core._
import rt68ice.io.{LedDevice, T16450Device}
import rt68ice.memory.Mem16Bit
import rt68ice.video.{Gpdi, StreamedVgaDevice, VgaDevice}
import spinal.core._
import spinal.lib.com.uart.Uart
import spinal.lib.graphic.hdmi.VgaToHdmiEcp5
import spinal.lib.master

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class Rt68IceTopLevel(romFile: String) extends Component {
  val io = new Bundle {
    val led = out Bits(3 bits)
    val uart = master(Uart()) // Expose UART pins (txd, rxd), must be defined in the constraints file
    val gpdi = master(Gpdi())
  }

  val clockCtrl = ClockCtrl()

  clockCtrl.cpuCd{
    // Bus Controller
    val bus = new BusController

    // CPU
    val cpu = new M68K
    cpu.io.ipl := B"111"
    cpu.io.clockEn := bus.io.clockEn
    cpu.io.busErr := bus.io.busErr
    bus.io.busState := cpu.io.busState
    bus.io.cpuBus <> cpu.io.bus

    // ROM
    val rom = Mem16Bit(sizeInWords = 4096, initFile = Some(romFile), readOnly = true)
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

    // Video Device
    val vgaDevice = VgaDevice(vgaCd = clockCtrl.vgaCd)
    vgaDevice.io.fbSel := bus.io.vidFbSel
    vgaDevice.io.palSel := bus.io.vidPalSel
    vgaDevice.io.ctrlSel := bus.io.vidCtrlSel
    vgaDevice.io.bus <> bus.io.videoBus

    // VGA-HDMI Bridge
    val hdmiBridge = VgaToHdmiEcp5(vgaCd = clockCtrl.vgaCd, hdmiCd = clockCtrl.hdmiCd)
    hdmiBridge.TMDS_red.addTag(crossClockDomain)
    hdmiBridge.TMDS_green.addTag(crossClockDomain)
    hdmiBridge.TMDS_blue.addTag(crossClockDomain)

    hdmiBridge.io.vga <> vgaDevice.io.vga
    io.gpdi.dp := hdmiBridge.io.gpdi_dp
    io.gpdi.dn := hdmiBridge.io.gpdi_dn
  }

  // Remove io_ prefix
  noIoPrefix()
}

object Rt68IceTopLevelVerilog extends App {
  private val report = Config.spinal.generateVerilog(Rt68IceTopLevel(romFile = "monitor.hex"))
  report.mergeRTLSource("mergeRTL") // Merge all rtl sources into mergeRTL.vhd and mergeRTL.v files
}


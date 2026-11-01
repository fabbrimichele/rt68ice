package rt68ice

import rt68ice.audio.{Audio, Ym2149Device}
import rt68ice.core._
import rt68ice.io._
import rt68ice.memory.{Mem16Bit, SdRam, SdRamDevice}
import rt68ice.timer.{Counter, Timer}
import rt68ice.video.{Gpdi, VgaDevice}
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
    val leds = out Bits(16 bits)
    val uart = master(Uart()) // Expose UART pins (txd, rxd), must be defined in the constraints file
    val gpdi = master(Gpdi())
    val sdram = master(SdRam())
    val usb1 = master(Usb())
    val usb2 = master(Usb())
    val usb3 = master(Usb())
    val usb4 = master(Usb())
    val sd = master(Spi())
    val flash = master(SpiFlash())
    val audio = Audio()
    val ds1302 = master(Ds1302())
  }

  val clockCtrl = ClockCtrl()

  clockCtrl.systemCd {
    // Bus Controller
    val bus = new BusController

    // SDRAM
    val sdRamDevice = SdRamDevice(clockCtrl.sdRamCd)
    sdRamDevice.io.sdRam <> io.sdram
    bus.io.sdRamBus <> sdRamDevice.io.bus

    // PSG (Ym2149) devices
    val psg = Ym2149Device()
    io.audio.left := psg.io.pwmAudio
    io.audio.right := psg.io.pwmAudio
    bus.io.psgBus <> psg.io.bus

    // DS1302
    val ds1302 = Ds1302Device()
    io.ds1302 <> ds1302.io.ds1302
    bus.io.ds1302Bus <> ds1302.io.bus

    // CPU
    val cpu = new M68K
    cpu.io.ipl := bus.io.ipl
    cpu.io.clockEn := bus.io.clockEn
    cpu.io.busErr := bus.io.busErr
    bus.io.busState := cpu.io.busState
    bus.io.cpuBus <> cpu.io.bus

    // ROM
    val rom = Mem16Bit(sizeInWords = 2048, initFile = Some(romFile), readOnly = true)
    bus.io.romBus <> rom.io.bus

    // RAM
    val ram = Mem16Bit(sizeInWords = 512)
    bus.io.ramBus <> ram.io.bus

    // Counter
    val counter = Counter()
    bus.io.counterBus <> counter.io.bus

    // Programmable timer
    val timer = Timer()
    bus.io.timerBus <> timer.io.bus

    // LED Device
    val ledDevice = LedDevice()
    io.led := ledDevice.io.led
    bus.io.ledBus <> ledDevice.io.bus

    // LED Array Device
    val ledsDevice = LedArrayDevice()
    io.leds := ledsDevice.io.leds
    bus.io.ledsBus <> ledsDevice.io.bus

    // UART Device
    val uartDevice = T16450Device()
    uartDevice.io.uart <> io.uart
    bus.io.uartBus <> uartDevice.io.bus

    // Video Device
    val vgaDevice = VgaDevice(vgaCd = clockCtrl.vgaCd)
    vgaDevice.io.bus <> bus.io.videoBus

    // VGA-HDMI Bridge
    val hdmiBridge = VgaToHdmiEcp5(vgaCd = clockCtrl.vgaCd, hdmiCd = clockCtrl.hdmiCd)
    hdmiBridge.TMDS_red.addTag(crossClockDomain)
    hdmiBridge.TMDS_green.addTag(crossClockDomain)
    hdmiBridge.TMDS_blue.addTag(crossClockDomain)

    hdmiBridge.io.vga <> vgaDevice.io.vga
    io.gpdi.dp := hdmiBridge.io.gpdi_dp
    io.gpdi.dn := hdmiBridge.io.gpdi_dn

    // USB HID Host
    val usbDevice = UsbDevice(usbCd = clockCtrl.usbCd)
    usbDevice.io.usb1 <> io.usb1
    usbDevice.io.usb2 <> io.usb2
    usbDevice.io.usb3 <> io.usb3
    usbDevice.io.usb4 <> io.usb4
    bus.io.usbBus <> usbDevice.io.bus

    // SPI Device (SD card and Flash Mem)
    val spiMaster = SpiMasterDevice(SpiMasterConfig(portCount = 2))
    io.sd <> spiMaster.io.spis(0)
    val flashSpi = spiMaster.io.spis(1)
    io.flash.mosi := flashSpi.mosi
    io.flash.cs := flashSpi.cs
    flashSpi.miso := io.flash.miso

    val flashClock = new Ecp5UsrMclk
    flashClock.io.USRMCLKI := flashSpi.clk
    // Release the dedicated clock pin whenever flash CS is inactive.
    flashClock.io.USRMCLKTS := flashSpi.cs
    bus.io.spiBus <> spiMaster.io.bus
  }

  // Remove io_ prefix
  noIoPrefix()
}

object Rt68IceTopLevelVerilog extends App {
  private val report = Config.spinal.generateVerilog(Rt68IceTopLevel(romFile = "monitor.hex"))
  report.mergeRTLSource("mergeRTL") // Merge all rtl sources into mergeRTL.vhd and mergeRTL.v files
}

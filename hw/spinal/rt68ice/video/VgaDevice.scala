package rt68ice.video

import rt68ice.core.M68KBus
import rt68ice.video.VgaDevice.rgbConfig
import spinal.core._
import spinal.lib._
import spinal.lib.experimental.chisel.Bundle
import spinal.lib.graphic.RgbConfig
import spinal.lib.graphic.vga._

import scala.language.postfixOps

object VgaDevice {
  val rgbConfig = RgbConfig(8, 8, 8)
}

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class VgaDevice(vgaCd : ClockDomain) extends Component {
  val io = new Bundle {
    val bus   = slave(M68KBus())
    val sel   = in Bool()
    val vga = master(Vga(VgaDevice.rgbConfig))
  }

  val framebuffer = Mem(Bits(16 bits), 32768) // 64 KB
  framebuffer.init(
    Array.tabulate(32768) {
      case 0 => B"16'xF81F" // Word 0: Magenta
      case 1 => B"16'x07E0" // Word 1: Green
      case 2 => B"16'xFFFF" // Word 2: White
      case _ => B"16'x0000" // Rest of the screen: Black
    }
  )

  // --- 68000 bus side ---
  io.bus.dataIn := framebuffer.readWriteSync(
    address = io.bus.address(15 downto 1).asUInt,
    data = io.bus.dataOut,
    enable = io.sel,
    write = io.bus.wr,
    mask = io.bus.uds ## io.bus.lds,
    clockCrossing = true,
  )

  // ------ VGA side ------
  // vgaCd { ... } equivalent to new ClockingArea(vgaCd) { ... }
  val vgaArea = new ClockingArea(vgaCd) {
    // VGA Controller
    val ctrl = VgaCtrl(rgbConfig)
    ctrl.io.softReset := False
    ctrl.io.timings.setAs_h640_v480_r60

    val addressCounter = Reg(UInt(15 bits)) init 0
    val memReadStream = Stream(Bits(16 bits))
    val canRead = memReadStream.ready

    val rawMemData = framebuffer.readSync(
      address = addressCounter,
      enable  = canRead,
      clockCrossing = true,
    )

    // Pipeline the 'valid' signal by 1 cycle to match the memory latency
    memReadStream.valid := RegNext(canRead) init False
    memReadStream.payload := rawMemData

    // Increment address when a read is successfully initiated
    when(canRead) {
      addressCounter := addressCounter + 1
    }

    // Reset address counter at the beginning of every video frame
    when(ctrl.io.frameStart) {
      addressCounter := 0
    }

    // Adapt the raw bitstream to Rgb configuration through a FIFO
    // A small depth (e.g., 16 or 32) absorbs the synchronous memory latency easily
    val pixelFifo = StreamFifo(Bits(16 bits), depth = 32)
    pixelFifo.io.push << memReadStream

    // Unpack 16-bit (e.g., RGB 565) framebuffer data to the VgaCtrl 24-bit (RGB 888) stream
    // (Adjust bit-slicing depending on your exact 16-bit color packing format)
    ctrl.io.pixels.valid := pixelFifo.io.pop.valid
    pixelFifo.io.pop.ready := ctrl.io.pixels.ready

    ctrl.io.pixels.r := (pixelFifo.io.pop.payload(15 downto 11) ## B"000").asUInt
    ctrl.io.pixels.g := (pixelFifo.io.pop.payload(10 downto  5) ## B"00").asUInt
    ctrl.io.pixels.b := (pixelFifo.io.pop.payload(4  downto  0) ## B"000").asUInt

    //io.vga <> ctrl.io.vga
    io.vga.color := ctrl.io.vga.color

    // Delay to compensate for the StreamFifo
    val delay = 29
    io.vga.hSync := History(ctrl.io.vga.hSync, delay).last
    io.vga.vSync := History(ctrl.io.vga.vSync, delay).last
    io.vga.colorEn := History(ctrl.io.vga.colorEn, delay).last
  }
}
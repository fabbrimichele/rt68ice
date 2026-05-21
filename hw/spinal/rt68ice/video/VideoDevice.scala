package rt68ice.video

import rt68ice.core.M68KBus
import rt68ice.video.VgaDevice.rgbConfig
import spinal.core._
import spinal.lib.experimental.chisel.Bundle
import spinal.lib.graphic.RgbConfig
import spinal.lib.graphic.vga._
import spinal.lib.graphic.hdmi._
import spinal.lib._

import scala.language.postfixOps

object VgaDevice {
  val rgbConfig = RgbConfig(8, 8, 8)
}

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class VideoDevice(vgaCd : ClockDomain, hdmiCd : ClockDomain) extends Component {
  val io = new Bundle {
    val bus   = slave(M68KBus())
    val sel   = in Bool()
    val gpdi  = master(Gpdi())
  }

  val framebuffer = Mem(Bits(16 bits), 32768) // 64 KB

  // --- 68000 bus side ---
  io.bus.dataIn := framebuffer.readWriteSync(
    address = io.bus.address(15 downto 1).asUInt,
    data = io.bus.dataOut,
    enable = io.sel,
    write = io.bus.wr,
    mask = io.bus.uds ## io.bus.lds,
  )

  // ------ VGA side ------
  // vgaCd { ... } equivalent to new ClockingArea(vgaCd) { ... }
  vgaCd {
    // Blinker
    val counter = Reg(UInt(rgbConfig.gWidth bits))
    val ctrl = VgaCtrl(rgbConfig)
    ctrl.io.softReset := False
    ctrl.io.timings.setAs_h640_v480_r60
    ctrl.io.pixels.valid := True
    ctrl.io.pixels.r := 0
    ctrl.io.pixels.g := counter
    ctrl.io.pixels.b := 0

    when(ctrl.io.frameStart) {
      counter := counter + 1
    }

    val hdmiBridge = VgaToHdmiEcp5(vgaCd, hdmiCd)
    hdmiBridge.TMDS_red.addTag(crossClockDomain)
    hdmiBridge.TMDS_green.addTag(crossClockDomain)
    hdmiBridge.TMDS_blue.addTag(crossClockDomain)

    hdmiBridge.io.vga <> ctrl.io.vga
    io.gpdi.dp := hdmiBridge.io.gpdi_dp
    io.gpdi.dn := hdmiBridge.io.gpdi_dn
  }
}
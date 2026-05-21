package rt68ice.video

import rt68ice.core.M68KBus
import rt68ice.video.VgaDevice.rgbConfig
import spinal.core.{Area, ClockDomain, ClockingArea, Component, False, IntToBuilder, Reg, True, UInt, crossClockDomain, in, out, when}
import spinal.lib.experimental.chisel.Bundle
import spinal.lib.graphic.RgbConfig
import spinal.lib.graphic.vga._
import spinal.lib.graphic.hdmi._
import spinal.lib._

import scala.annotation.unused
import scala.language.postfixOps

object VgaDevice {
  val rgbConfig = RgbConfig(8, 8, 8)
}

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class VideoDevice(vgaCd : ClockDomain, hdmiCd : ClockDomain) extends Component {
  val io = new Bundle {
    val gpdi_dp, gpdi_dn = out Bits (4 bits)
  }



  // new ClockingArea(vgaCd) { ... } => vgaCd { ... }
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
    io.gpdi_dp := hdmiBridge.io.gpdi_dp
    io.gpdi_dn := hdmiBridge.io.gpdi_dn
  }
}
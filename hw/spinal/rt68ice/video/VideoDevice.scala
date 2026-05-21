package rt68ice.video

import rt68ice.core.M68KBus
import rt68ice.video.VgaDevice.rgbConfig
import spinal.core.{ClockDomain, ClockingArea, Component, False, IntToBuilder, Reg, True, UInt, crossClockDomain, in, out, when}
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

  val bridge = VgaToHdmiEcp5(vgaCd, hdmiCd)
  bridge.TMDS_red.addTag(crossClockDomain)
  bridge.TMDS_green.addTag(crossClockDomain)
  bridge.TMDS_blue.addTag(crossClockDomain)

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

    bridge.io.vga <> ctrl.io.vga
    io.gpdi_dp := bridge.io.gpdi_dp
    io.gpdi_dn := bridge.io.gpdi_dn
  }
}
package rt68ice.video

import spinal.core._
import spinal.lib.graphic.vga._
import spinal.lib.graphic._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class VgaCounter(rgbConfig: RgbConfig, timingsWidth: Int = 12) extends Component {
  val io = new Bundle {
    val softReset   = in Bool() default(False)
    val timings     = in(VgaTimings(timingsWidth))
    val hCounter    = out UInt(timingsWidth bit)
    val vCounter    = out UInt(timingsWidth bit)
    val colorEn     = out Bool()
    val frameStart  = out Bool()
    val hSync       = out Bool()
    val vSync       = out Bool()
  }

  case class HVArea(timingsHV: VgaTimingsHV, enable: Bool) extends Area {
    val counter = Reg(UInt(timingsWidth bit)) init(0)

    val syncStart = counter === timingsHV.syncStart
    val syncEnd = counter === timingsHV.syncEnd
    val colorStart = counter === timingsHV.colorStart
    val colorEnd = counter === timingsHV.colorEnd
    val polarity = timingsHV.polarity

    when(enable) {
      counter := counter + 1
      when(syncEnd) {
        counter := 0
      }
    }

    val sync    = RegInit(False) setWhen(syncStart) clearWhen(syncEnd)
    val colorEn = RegInit(False) setWhen(colorStart) clearWhen(colorEnd)

    when(io.softReset) {
      counter := 0
      sync := False
      colorEn := False
    }
  }

  val h = HVArea(io.timings.h, True)
  val v = HVArea(io.timings.v, h.syncEnd) // h.colorEnd
  val colorEn = h.colorEn && v.colorEn

  io.hCounter := h.counter
  io.vCounter := v.counter

  io.frameStart := v.syncStart && h.syncStart

  io.hSync := h.sync ^ h.polarity
  io.vSync := v.sync ^ v.polarity
  io.colorEn := colorEn
}

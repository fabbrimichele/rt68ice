package rt68ice

import rt68ice.core._
import rt68ice.memory.Mem16Bit
import spinal.core._

import scala.annotation.unused
import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class Rt68IceTopLevel() extends Component {
  val io = new Bundle {
    val led = out Bits(3 bits)
  }

  val clockCtrl = ClockCtrl()

  // Area with reset
  @unused
  val coreArea = new ClockingArea(clockCtrl.clk20Domain) {
    // CPU
    val cpu = new M68KSync
    cpu.io.ipl := B"111"
    cpu.io.busErr := False

    // Mapping - TODO: move to a separate component
    val sectionAddress = cpu.io.address(31 downto 11).asUInt // 2KB each memory section
    val romSel = Bool()
    val ledSel = Bool()

    romSel := False
    ledSel := False
    when (sectionAddress === 0) {       //    0 - 2048
      romSel := True
    } elsewhen(sectionAddress === 1) {  // 2048 - 4096
      ledSel := True
    }

    val rom = Mem16Bit(1024, Some("blink.hex")) // 2 KB
    rom.io.address := cpu.io.address
    cpu.io.dataIn := rom.io.dataOut
    rom.io.sel := romSel

    // LED Device
    val ledReg = Reg(Bits(16 bits)) init 0
    when(ledSel) { ledReg := cpu.io.dataOut }
    io.led := ledReg(2 downto 0)
  }
}

object Rt68IceTopLevelVerilog extends App {
  private val report = Config.spinal.generateVerilog(Rt68IceTopLevel())
  report.mergeRTLSource("mergeRTL") // Merge all rtl sources into mergeRTL.vhd and mergeRTL.v files
}


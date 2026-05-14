package rt68ice

import rt68ice.core._
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
    // CPU
    val cpu = new M68KSync
    cpu.io.ipl := B"111"
    cpu.io.busErr := False

    // Mapping - TODO: move to a separate component
    val sectionAddress = cpu.io.address(31 downto 11).asUInt // 2KB each memory section
    val romSel = Bool()
    val ledSel = Bool()
    val ramSel = Bool()

    romSel := False
    ledSel := False
    ramSel := False
    when (sectionAddress === 0) {       //    0 - 2048
      romSel := True
    } elsewhen(sectionAddress === 1) {  // 2048 - 4096
      ledSel := True
    } elsewhen(sectionAddress === 2) {  // 4096 - 6144
      ramSel := True
    }

    // ROM
    val rom = Mem16Bit(sizeInWords = 1024, initFile = Some(romFile), readOnly = true)
    rom.io.sel := romSel
    rom.io.wr := cpu.io.wr
    rom.io.uds := cpu.io.uds
    rom.io.lds := cpu.io.lds
    rom.io.address := cpu.io.address
    rom.io.dataIn := cpu.io.dataOut

    // RAM
    val ram = Mem16Bit(sizeInWords = 1024)
    ram.io.sel := ramSel
    ram.io.wr := cpu.io.wr
    ram.io.uds := cpu.io.uds
    ram.io.lds := cpu.io.lds
    ram.io.address := cpu.io.address
    ram.io.dataIn := cpu.io.dataOut

    // LED Device
    val ledReg = Reg(Bits(16 bits)) init 0
    when(ledSel && cpu.io.wr) { ledReg := cpu.io.dataOut }
    io.led := ledReg(2 downto 0)

    cpu.io.dataIn := 0
    when(romSel) {
      cpu.io.dataIn := rom.io.dataOut
    } elsewhen(ramSel) {
      cpu.io.dataIn := ram.io.dataOut
    }
  }
}

object Rt68IceTopLevelVerilog extends App {
  private val report = Config.spinal.generateVerilog(Rt68IceTopLevel(romFile = "mem_test.hex"))
  report.mergeRTLSource("mergeRTL") // Merge all rtl sources into mergeRTL.vhd and mergeRTL.v files
}


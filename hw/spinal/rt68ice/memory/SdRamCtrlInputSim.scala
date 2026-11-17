package rt68ice.memory

import spinal.core._
import scala.sys.process._

/** Pin-level burst alignment and byte-write regression for the SDRAM input stage. */
object SdRamCtrlInputSim extends App {
  for (burst <- Seq(1, 4, 8); cas <- Seq(2, 3)) {
    val directory = new java.io.File(s"simWorkspace/SdRamCtrlInput_b${burst}_cl${cas}")
    directory.mkdirs()
    val report = SpinalConfig(targetDirectory = directory.getPath).generateVerilog(
      SdRamCtrl(SdRamCtrlConfig(clockSpeedMhz = 50, burstLength = burst,
        p0BurstLength = burst, casLatency = cas)))
    val executable = new java.io.File(directory, "input-stage.sim").getPath
    val verilog = new java.io.File(directory, s"${report.toplevelName}.v").getPath
    val compile = Seq("iverilog", "-g2012", "-s", "SdRamCtrlInputTb",
      s"-PSdRamCtrlInputTb.BURST_LENGTH=$burst", s"-PSdRamCtrlInputTb.CAS_LATENCY=$cas",
      "-o", executable, verilog, "hw/sim/SdRamCtrlInputTb.v")
    require(compile.! == 0, s"Icarus compilation failed for burst=$burst CAS=$cas")
    require(Seq("vvp", executable).! == 0, s"SDRAM simulation failed for burst=$burst CAS=$cas")
  }
}

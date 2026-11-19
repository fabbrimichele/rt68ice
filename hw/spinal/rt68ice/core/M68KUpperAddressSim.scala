package rt68ice.core

import spinal.core._

import java.io.{File, PrintWriter}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.collection.mutable.ArrayBuffer
import scala.sys.process._

/** Real TG68K VHDL core + production SpinalHDL wrapper, in 68000 mode. */
object M68KUpperAddressSim extends App {
  val directory = new File("simWorkspace/M68KUpperAddress")
  directory.mkdirs()
  def path(name: String): String = new File(directory, name).getPath

  def run(command: Seq[String], logName: String): Unit = {
    val log = new PrintWriter(path(logName))
    val status = try command.!(ProcessLogger(line => log.println(line), line => log.println(line)))
                 finally log.close()
    require(status == 0, s"${command.head} failed; see ${path(logName)}")
  }

  val report = SpinalConfig(targetDirectory = directory.getPath).generateVerilog(
    M68K(cpuType = M68KType.M68000))
  report.mergeRTLSource("core")

  // Use the same GHDL/Yosys frontend as the FPGA build. The core is not stubbed.
  run(Seq("yosys", "-m", "ghdl", "-p",
    s"ghdl -C --std=08 -C -fsynopsys -C --latches ${path("core.vhd")} -e TG68KdotC_Kernel; " +
      s"proc; opt; write_verilog -noattr ${path("kernel.v")}"), "core-translation.log")

  run(Seq("m68k-atari-mint-as", "-m68000", "--register-prefix-optional",
    "-o", path("program.o"), "hw/sim/M68KUpperAddressProgram.S"), "assembler.log")
  run(Seq("m68k-atari-mint-ld", "--oformat", "binary", "-Ttext=0", "-e", "_start",
    "-o", path("program.bin"), path("program.o")), "linker.log")
  val program = Files.readAllBytes(new File(path("program.bin")).toPath)
  require(program.length <= 4096 && program.length % 2 == 0, "Invalid CPU test ROM size")
  val words = program.padTo(4096, 0.toByte).grouped(2).map { pair =>
    f"${((pair(0) & 0xff) << 8) | (pair(1) & 0xff)}%04x"
  }.mkString("\n")
  Files.write(new File(path("program.hex")).toPath, words.getBytes(StandardCharsets.US_ASCII))

  run(Seq("iverilog", "-g2012", "-s", "M68KUpperAddressTb", "-o", path("cpu.sim"),
    path(s"${report.toplevelName}.v"), path("kernel.v"), "hw/sim/M68KUpperAddressTb.v"),
    "iverilog.log")
  for (waitCycles <- Seq(0, 3)) {
    require(Seq("vvp", path("cpu.sim"), s"+wait_cycles=$waitCycles").! == 0,
      s"CPU upper-address test failed with wait_cycles=$waitCycles")
  }

  val negativeOutput = ArrayBuffer.empty[String]
  val negativeStatus = Seq("vvp", path("cpu.sim"), "+truncate_address").!(
    ProcessLogger(line => negativeOutput += line, line => negativeOutput += line))
  Files.write(new File(path("negative-control.log")).toPath,
    negativeOutput.mkString("\n").getBytes(StandardCharsets.US_ASCII))
  require(negativeStatus != 0 && negativeOutput.exists(_.contains("CPU test program reported failure")),
    s"Negative control must detect 24-bit memory aliasing; see ${path("negative-control.log")}")
  println("PASS: negative control detects memory aliasing when address bit 24 is discarded")
}

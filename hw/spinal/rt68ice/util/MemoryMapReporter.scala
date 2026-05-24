package rt68ice.util

import spinal.lib.bus.misc.{AddressMapping, MaskMapping, SizeMapping}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

object MemoryMapReporter {

  def saveMemoryLayout(path: String, devices: (String, AddressMapping)*): Unit = {
    val report = buildReport(devices)
    println(report)
    save(path, report)
  }

  private def save(path: String, report: String): Unit = {
    try {
      Files.write(Paths.get(path), report.getBytes(StandardCharsets.UTF_8))
    } catch {
      case e: Exception =>
        // We log a minimal error message just in case the path directory doesn't exist
        System.err.println(s"[error] Failed to export memory map: ${e.getMessage}")
    }
  }

  private def buildReport(devices: Seq[(String, AddressMapping)]): String = {
    val sb = new StringBuilder()
    sb.append("## System Memory Layout\n")
    sb.append("| Device | Start Address | End Address | Size |\n")
    sb.append("| :--- | :---: | :---: | ---: |\n")
    devices.foreach { dev => sb.append(buildReportEntry(dev)) }
    sb.toString()
  }

  private def buildReportEntry(device: (String, AddressMapping)): String = {
    val (name, mapping) = device
    mapping match {
      case sm: SizeMapping =>
        val endAddress = sm.base + sm.size - 1
        f"| $name%-15s | 0x${sm.base}%08X | 0x$endAddress%08X | ${sm.size / 1024}%4d KB |\n"
      case mm: MaskMapping =>
        // Calculate max range bound from mask inversion
        val rangeLength = ~mm.mask.toLong & 0xFFFFFFFFL
        val endAddress = mm.base.toLong + rangeLength
        val detailStr = if (rangeLength < 1024) s"${rangeLength + 1} Bytes" else s"${(rangeLength + 1) / 1024} KB"
        f"| $name%-15s | 0x${mm.base}%08X | 0x$endAddress%08X | $detailStr |\n"
    }
  }
}

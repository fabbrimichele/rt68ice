package rt68ice.util

import spinal.lib.bus.misc.{AddressMapping, MaskMapping, SizeMapping}

object MemoryMapReporter {
  def printMemoryLayout(devices: (String, AddressMapping)*): Unit = {
    println("===========================================================")
    println("                  SYSTEM MEMORY MAP                       ")
    println("===========================================================")
    devices.foreach(printMapping)
    println("===========================================================")
  }

  private def printMapping(device: (String, AddressMapping)): Unit = {
    val (name, mapping) = device
    mapping match {
      case sm: SizeMapping =>
        val endAddress = sm.base + sm.size - 1
        println(f"$name%-15s : 0x${sm.base}%08X -> 0x$endAddress%08X (${sm.size / 1024}%4d KB) [Size]")
      case mm: MaskMapping =>
        // For MaskMapping, we show the base and the mask pattern
        println(f"$name%-15s : Base: 0x${mm.base}%08X Mask: 0x${mm.mask}%08X  [Mask]")
    }
  }
}

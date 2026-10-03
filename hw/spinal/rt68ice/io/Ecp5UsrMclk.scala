package rt68ice.io

import spinal.core._

// Dedicated ECP5 configuration-clock pin access after FPGA configuration.
// There is no fabric output: USRMCLK drives the physical MCLK pin internally.
class Ecp5UsrMclk extends BlackBox {
  val io = new Bundle {
    val USRMCLKI  = in Bool()
    val USRMCLKTS = in Bool() // High releases MCLK; low drives USRMCLKI.
  }

  setDefinitionName("USRMCLK")
  noIoPrefix()
}

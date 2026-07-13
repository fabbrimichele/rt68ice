package rt68ice.io

import spinal.core._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
class UsbHidHostBB extends BlackBox {
  // Define IO
  val io = new Bundle {
    // USB interface
    val usbclk = in Bool()                      // 12MHz clock
    val usbrst_n = in Bool()                    // reset
    val usb_dm, usb_dp = inout(Analog(Bool()))  // USB D- and D+

    // Host interface
    // Common
    val typ = out Bits(2 bits)  // device type. 0: no device, 1: keyboard, 2: mouse, 3: gamepad
    val report = out Bool()     // pulse after report received from device.
                                // key_*, mouse_*, game_* valid depending on typ
    val conerr = out Bool()     // connection or protocol error

    // Keyboard
    val key_modifiers = out Bits(8 bits)
    val key1, key2, key3, key4 = out Bits(8 bits)

    // Mouse
    val mouse_btn = out Bits(8 bits)  // {5'bx, middle, right, left}
    val mouse_dx = out Bits(8 bits)   // signed 8-bit, cleared after `report` pulse
    val mouse_dy = out Bits(8 bits)   // signed 8-bit, cleared after `report` pulse

    val game_l,  game_r, game_u, game_d = out  Bool()                     // left right up down
    val game_a, game_b, game_x, game_y, game_sel, game_sta  = out  Bool() // buttons
  }

  // Map the clock domain
  // Mapped in the wrapper
  mapClockDomain(clock = io.usbclk, reset = io.usbrst_n, resetActiveLevel = LOW)

  setDefinitionName("usb_hid_host") // This tells SpinalHDL which Verilog module to instantiate
  addRTLPath("hw/verilog/usb_hid_host.v") // Merge the file to the generated 'mergeRTL.v' file
  addRTLPath("hw/verilog/usb_hid_host_rom.hex") // Merge the file to the generated 'mergeRTL.v' file
  addRTLPath("hw/verilog/usb_hid_host_rom.v") // Merge the file to the generated 'mergeRTL.v' file
  noIoPrefix() // Remove io_ prefix
}


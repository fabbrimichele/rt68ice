// diamond 3.7 accepts this PLL
// diamond 3.8-3.9 is untested
// diamond 3.10 or higher is likely to abort with error about unable to use feedback signal
// cause of this could be from wrong CPHASE/FPHASE parameters

/* Commands:
```
ecppll -i 25 --clkin_name clkin_25 --clkout0 125 --clkout0_name clk_hdmi --clkout1 25 --clkout1_name clk_vga -f hw/verilog/pll1.v
ecppll -i 25 --clkin_name clkin_25 --clkout0 25 --clkout0_name clk_cpu --clkout1 50 --clkout1_name clk_sdram --phase3 180 --clkout2 12 --clkout2_name clk_usb -f hw/verilog/pll2.v
```
*/

module pll
(
    input clkin_25,     // 25 MHz, 0 deg
    output clk_hdmi,    // 125 MHz, 0 deg
    output clk_vga,     // 25 MHz, 0 deg
    output clk_cpu,     // 25 MHz, 0 deg
    output clk_sdram,   // 50 MHz, 180 deg
    output clk_usb,     // 12 MHz, 0 deg
    output locked
);

wire locked0;

(* FREQUENCY_PIN_CLKI="25" *)
(* FREQUENCY_PIN_CLKOP="125" *)
(* FREQUENCY_PIN_CLKOS="25" *)
(* ICP_CURRENT="12" *) (* LPF_RESISTOR="8" *) (* MFG_ENABLE_FILTEROPAMP="1" *) (* MFG_GMCREF_SEL="2" *)
EHXPLLL #(
        .PLLRST_ENA("DISABLED"),
        .INTFB_WAKE("DISABLED"),
        .STDBY_ENABLE("DISABLED"),
        .DPHASE_SOURCE("DISABLED"),
        .OUTDIVIDER_MUXA("DIVA"),
        .OUTDIVIDER_MUXB("DIVB"),
        .OUTDIVIDER_MUXC("DIVC"),
        .OUTDIVIDER_MUXD("DIVD"),
        .CLKI_DIV(1),
        .CLKOP_ENABLE("ENABLED"),
        .CLKOP_DIV(5),
        .CLKOP_CPHASE(2),
        .CLKOP_FPHASE(0),
        .CLKOS_ENABLE("ENABLED"),
        .CLKOS_DIV(25),
        .CLKOS_CPHASE(2),
        .CLKOS_FPHASE(0),
        .FEEDBK_PATH("CLKOP"),
        .CLKFB_DIV(5)
    ) pll_0 (
        .RST(1'b0),
        .STDBY(1'b0),
        .CLKI(clkin_25),
        .CLKOP(clk_hdmi),
        .CLKOS(clk_vga),
        .CLKFB(clk_hdmi),
        .CLKINTFB(),
        .PHASESEL0(1'b0),
        .PHASESEL1(1'b0),
        .PHASEDIR(1'b1),
        .PHASESTEP(1'b1),
        .PHASELOADREG(1'b1),
        .PLLWAKESYNC(1'b0),
        .ENCLKOP(1'b0),
        .LOCK(locked0)
	);

(* FREQUENCY_PIN_CLKI="25" *)
(* FREQUENCY_PIN_CLKOP="25" *)
(* FREQUENCY_PIN_CLKOS="50" *)
(* FREQUENCY_PIN_CLKOS2="12" *)
(* ICP_CURRENT="12" *) (* LPF_RESISTOR="8" *) (* MFG_ENABLE_FILTEROPAMP="1" *) (* MFG_GMCREF_SEL="2" *)
EHXPLLL #(
        .PLLRST_ENA("DISABLED"),
        .INTFB_WAKE("DISABLED"),
        .STDBY_ENABLE("DISABLED"),
        .DPHASE_SOURCE("DISABLED"),
        .OUTDIVIDER_MUXA("DIVA"),
        .OUTDIVIDER_MUXB("DIVB"),
        .OUTDIVIDER_MUXC("DIVC"),
        .OUTDIVIDER_MUXD("DIVD"),
        .CLKI_DIV(1),
        .CLKOP_ENABLE("ENABLED"),
        .CLKOP_DIV(24),
        .CLKOP_CPHASE(11),
        .CLKOP_FPHASE(0),
        .CLKOS_ENABLE("ENABLED"),
        .CLKOS_DIV(12),
        .CLKOS_CPHASE(11),
        .CLKOS_FPHASE(0),
        .CLKOS2_ENABLE("ENABLED"),
        .CLKOS2_DIV(50),
        .CLKOS2_CPHASE(11),
        .CLKOS2_FPHASE(0),
        .FEEDBK_PATH("CLKOP"),
        .CLKFB_DIV(1)
    ) pll_1 (
        .RST(1'b0),
        .STDBY(1'b0),
        .CLKI(clkin_25),
        .CLKOP(clk_cpu),
        .CLKOS(clk_sdram),
        .CLKOS2(clk_usb),
        .CLKFB(clk_cpu),
        .CLKINTFB(),
        .PHASESEL0(1'b0),
        .PHASESEL1(1'b0),
        .PHASEDIR(1'b1),
        .PHASESTEP(1'b1),
        .PHASELOADREG(1'b1),
        .PLLWAKESYNC(1'b0),
        .ENCLKOP(1'b0),
        .LOCK(locked)
    );
endmodule

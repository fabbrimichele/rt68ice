### Resources
- [AtariST_MiSTer/rtl/sdram.v](https://github.com/MiSTer-devel/AtariST_MiSTer/blob/master/rtl/sdram.v)
- [Higher level SDRAM controller - hides refresh](https://github.com/agg23/sdram-controller/blob/master/sdram.sv)
- [icesugar-pro - sdram](https://github.com/wuxx/icesugar-pro#sdram)

### Performance
Tested with `sdram_test.asm`, test name `Benchmark`:

| Configuration     | Clock Cycles |
|-------------------|-------------:|
| 28.4091 MHz SDRAM |         5910 |
| 56.8182 MHz SDRAM |         4739 |
| FPGA RAM          |         3076 |

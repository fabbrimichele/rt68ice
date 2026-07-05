### Resources
- [AtariST_MiSTer/rtl/sdram.v](https://github.com/MiSTer-devel/AtariST_MiSTer/blob/master/rtl/sdram.v)
- [Higher level SDRAM controller - hides refresh](https://github.com/agg23/sdram-controller/blob/master/sdram.sv)
- [icesugar-pro - sdram](https://github.com/wuxx/icesugar-pro#sdram)

### Performance
Tested with `sdram_test.asm`, test name `Benchmark`:

| RAM (MHz)        | CPU (MHz) | Clock Cycles |
|------------------|-----------|-------------:|
| 28.4091 SDRAM    | 28.4091   |         5910 |
| 56.8182 SDRAM    | 28.4091   |         4739 |
| 28.4091 FPGA RAM | 28.4091   |         3076 |

I tried the following configuration but the SDRAM doesn't work:
- SDRAM clock: 78.1250 
- CPU clock: 26.0417


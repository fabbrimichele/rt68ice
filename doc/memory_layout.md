## System Memory Layout
| Device | Start Address | End Address | Size |
| :--- | :---: | :---: | ---: |
| BOOT VECTORS    | 0x00000000 | 0x00000007 | 8 Bytes |
| FAST RAM        | 0x00000000 | 0x00003FFF |   16 KB |
| SDRAM           | 0x00000000 | 0x007FFFFF | 8192 KB |
| VIDEO FB        | 0x00E00000 | 0x00E1FFFF |  128 KB |
| LED PERIPH      | 0x00F00000 | 0x00F03FFF |   16 KB |
| UART PERIPH     | 0x00F04000 | 0x00F07FFF |   16 KB |
| VIDEO PALETTE   | 0x00F08000 | 0x00F0BFFF |   16 KB |
| VIDEO CONTROL   | 0x00F0C000 | 0x00F0FFFF |   16 KB |
| COUNTER         | 0x00F10000 | 0x00F13FFF |   16 KB |
| LED_ARRAY       | 0x00F14000 | 0x00F17FFF |   16 KB |
| USB HID HOST    | 0x00F18000 | 0x00F1BFFF |   16 KB |
| MAIN ROM        | 0x00FC0000 | 0x00FC3FFF |   16 KB |

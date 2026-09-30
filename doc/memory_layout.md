## System Memory Layout
| Device | Start Address | End Address | Size |
| :--- | :---: | :---: | ---: |
| BOOT VECTORS    | 0x00000000 | 0x00000007 | 8 Bytes |
| FAST RAM        | 0x00000000 | 0x000003FF |    1 KB |
| SDRAM 8MB       | 0x00000000 | 0x007FFFFF | 8192 KB |
| SDRAM 4MB       | 0x00800000 | 0x00BFFFFF | 4096 KB |
| SDRAM 2MB       | 0x00C00000 | 0x00DFFFFF | 2048 KB |
| VIDEO FB        | 0x00E00000 | 0x00E1FFFF |  128 KB |
| LED PERIPH      | 0x00F00000 | 0x00F03FFF |   16 KB |
| UART PERIPH     | 0x00F04000 | 0x00F07FFF |   16 KB |
| VIDEO PALETTE   | 0x00F08000 | 0x00F0BFFF |   16 KB |
| VIDEO CONTROL   | 0x00F0C000 | 0x00F0FFFF |   16 KB |
| COUNTER         | 0x00F10000 | 0x00F13FFF |   16 KB |
| LED_ARRAY       | 0x00F14000 | 0x00F17FFF |   16 KB |
| USB HID HOST    | 0x00F18000 | 0x00F1BFFF |   16 KB |
| TIMER           | 0x00F1C000 | 0x00F1FFFF |   16 KB |
| SPI             | 0x00F20000 | 0x00F23FFF |   16 KB |
| MAIN ROM        | 0x00FC0000 | 0x00FC3FFF |   16 KB |

VIDEO_CTRL          equ $00F0C000 ; Resolution control
VIDEO_IRQ_STATUS    equ $00F0C002 ; Bit 0 VBL pending; read to acknowledge
VIDEO_IRQ_ENABLE    equ $00F0C004 ; Bit 0 VBL interrupt enable
VIDEO_PLTE          equ $00F08000 ; Video Palette Registers

VIDEO_IRQ_VBL       equ $0001

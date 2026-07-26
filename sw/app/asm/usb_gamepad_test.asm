    section .text, code

; ===========================
; Program code
; ===========================
start:
    lea     LEDS,a0         ;
    lea     USB1_GAMEPAD,a1 ;

.loop:
    move.w  (a1),(a0)       ; Write d1 into LED register
    move.l  #DLY_VAL,d0     ;
.dly_loop:
    subq.l  #1,d0           ; 4 cycles
    bne     .dly_loop       ; 10 cycles when taken
    jmp     .loop           ; Infinite loop


; ===========================
; Value Constants
; ===========================
DLY_VAL     equ     312500   ;

; ===========================
; Include files
; ===========================
    include '../../lib/asm/mem_map_leds.asm'
    include '../../lib/asm/mem_map_usb.asm'

; ===========================
; Data Constants
; Must be after code to avoid alignment issues
; ===========================
; Add here data costants, e.g. `msg_hello dc.b    "Type something:",CR,LF,NUL`

; ===========================
; RAM Data Section
; ===========================
    section .bss
; Add here variables and buffers, e.g. `buffer ds.b 80`


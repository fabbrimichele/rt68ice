    section .text, code

; ===========================
; 68000 Vector Table, only initial PC and SP
; Each vector is 32 bits (long)
; ===========================
    dc.l   _stack_top   ; 0: Initial Stack Pointer (SP)
    dc.l   start        ; 1: Reset vector (PC start address)

; ===========================
; Program code
; ===========================
start:
    move.w  #1,d0
    move.b  d0,LED          ; Red -> NOT OK
    bsr     set_green
    move.b  d0,LED          ; Green -> OK
.loop:
    bra     .loop

set_green:
    move.w  #2,d0
    rts

; ===========================
; Value Constants
; ===========================

; ===========================
; Include files
; ===========================
    include '../../lib/asm/mem_map_led.asm'

; ===========================
; Data Constants
; Must be after code to avoid alignment issues
; ===========================
; Add here data costants, e.g. `msg_hello dc.b    "Type something:",CR,LF,NUL`

; ===========================
; RAM Data Section (bootloader mem)
; ===========================
    section .bss
; Add here variables and buffers, e.g. `buffer ds.b 80`

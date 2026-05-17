    ORG     $4000            ; Start of ROM

; ===========================
; 68000 Vector Table, only initial PC and SP
; Each vector is 32 bits (long)
; ===========================
    DC.L   RAM_END      ; 0: Initial Stack Pointer (SP)
    DC.L   start        ; 1: Reset vector (PC start address)

; ===========================
; Program code
; ===========================
start:
    move.w  #1,D0
    move.b  D0,LED          ; Red -> NOT OK
    bsr     set_green
    move.b  D0,LED          ; Green -> OK
.loop:
    bra     .loop

set_green:
    move.w  #2,D0
    rts

; ===========================
; Value Constants
; ===========================
RAM_END     EQU     $00004000   ; End of RAM address (+1)

; ===========================
; Include files
; ===========================
    INCLUDE '../../lib/asm/mem_map_led.asm'

; ===========================
; Data Constants
; Must be after code to avoid alignment issues
; ===========================

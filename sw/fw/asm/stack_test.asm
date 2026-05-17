    ORG     $4000            ; Start of ROM

; ===========================
; 68000 Vector Table, only initial PC and SP
; Each vector is 32 bits (long)
; ===========================
    dc.l   RAM_END      ; 0: Initial Stack Pointer (SP)
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
RAM_END     EQU     $00004000   ; End of RAM address (+1)

; ===========================
; Include files
; ===========================
    include '../../lib/asm/mem_map_led.asm'

; ===========================
; Data Constants
; Must be after code to avoid alignment issues
; ===========================

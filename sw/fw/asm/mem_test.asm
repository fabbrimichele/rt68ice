    ORG     $4000            ; Start of ROM

; ===========================
; 68000 Vector Table, only initial PC and SP
; Each vector is 32 bits (long)
; ===========================
    DC.L   RAM_END      ; 0: Initial Stack Pointer (SP)
    DC.L   START        ; 1: Reset vector (PC start address)

; ===========================
; Program code
; ===========================
START:
    move.w  #$55,RAM_START
    move.w  RAM_START,D0
    cmp.w   #$55,D0
    beq     OK
NOT_OK:
    move.b  #1,LED          ; memory mismatch, LED red
    bra     LOOP
OK:
    move.b  #2,LED          ; memory match, LED green
LOOP:
    bra     LOOP

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

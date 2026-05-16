    ORG     $0800            ; Start of ROM

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
    move.w  #1,LED          ; memory mismatch, LED red
    bra     LOOP
OK:
    move.w  #2,LED          ; memory match, LED green
LOOP:
    bra     LOOP

; ===========================
; Constants
; ===========================
RAM_START   EQU     $00000400
RAM_END     EQU     $00000800   ; End of RAM address (+1)
LED         EQU     $00001000   ; LED-mapped register base address

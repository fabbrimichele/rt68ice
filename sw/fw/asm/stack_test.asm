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
    move.w  #1,D0
    move.w  D0,LED          ; Red -> NOT OK
    bsr     SET_GREEN
    move.w  D0,LED          ; Green -> OK
LOOP:
    bra     LOOP

SET_GREEN:
    move.w  #2,D0
    rts

    ; ===========================
    ; Constants
    ; ===========================
RAM_END     EQU     $00000800   ; End of RAM address (+1)
LED         EQU     $00001000   ; LED-mapped register base address

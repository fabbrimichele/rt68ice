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
    lea     LED,A0          ; Load LED register address into A0
    move.w  #1,D1

LOOP:
    move.w  D1,(A0)         ; Write D1 into LED register
    addq.w  #1,D1           ; Increment register
DELAY:
    move.l  #DLY_VAL,D0     ;
DLY_LOOP:
    subq.l  #1,D0           ; 4 cycles
    bne     DLY_LOOP        ; 10 cycles when taken
    jmp     LOOP            ; Infinite loop


    ; ===========================
    ; Constants
    ; ===========================

DLY_VAL     EQU     3125000     ; Delay iterations, 0.5 sec at 25 MHz
RAM_END     EQU     $00000800   ; End of RAM address (+1)
LED         EQU     $00001000   ; LED-mapped register base address



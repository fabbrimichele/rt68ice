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
    lea     LED,a0          ; Load LED register address into a0
    move.b  #1,d1

.loop:
    move.b  d1,(a0)         ; Write d1 into LED register
    addq.b  #1,d1           ; Increment register
    move.l  #DLY_VAL,d0     ;
.dly_loop:
    subq.l  #1,d0           ; 4 cycles
    bne     .dly_loop        ; 10 cycles when taken
    jmp     .loop            ; Infinite loop


; ===========================
; Value Constants
; ===========================
DLY_VAL     EQU     3125000     ; Delay iterations, 0.5 sec at 25 MHz
RAM_END     EQU     $00004000   ; End of RAM address (+1)

; ===========================
; Include files
; ===========================
    include '../../lib/asm/mem_map_led.asm'

; ===========================
; Data Constants
; Must be after code to avoid alignment issues
; ===========================

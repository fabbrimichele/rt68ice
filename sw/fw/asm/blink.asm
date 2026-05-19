    section .text, code

; ===========================
; 68000 Vector Table, only initial PC and SP
; Each vector is 32 bits (long)
; ===========================
    dc.l   _stack_top       ; 0: Initial Stack Pointer (SP)
    dc.l   start            ; 1: Reset vector (PC start address)

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
DLY_VAL     equ     3125000     ; Delay iterations, 0.5 sec at 25 MHz

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


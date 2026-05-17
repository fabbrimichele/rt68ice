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
    move.w  #$55,buffer
    move.w  buffer,D0
    cmp.w   #$55,D0
    beq     .ok
.not_ok:
    move.b  #1,LED          ; memory mismatch, LED red
    bra     .loop
.ok:
    move.b  #2,LED          ; memory match, LED green
.loop:
    bra     .loop

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
buffer  ds.w 1

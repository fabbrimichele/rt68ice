    section .text, code

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
    bra     .end
.ok:
    move.b  #2,LED          ; memory match, LED green
.end:
    trap    #14

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

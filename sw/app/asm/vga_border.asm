    section .text, code

; ===========================
; Program code
; ===========================
start:
    lea     _fb_start,a0
    bsr     hline
    lea     (_fb_start+19160*2),a0 ; Addresses are in bytes not words
    bsr     hline
    lea     _fb_start,a0
    move.w  #$8000,d1
    bsr     lvline
    lea     (_fb_start+39*2),a0    ; Last column
    move.w  #$1,d1
    bsr     lvline
.end:
    trap    #14

; Draw a full horizontal line
; Input: a0 starting address
hline:
    move.w  #39,d0          ; 40 - 1 for dbra (640px/16bits = 39 words)
.loop:
    move.w  #$FFFF,(a0)+    ; Draw 16 white pixels, advance by 2 bytes
    dbra    d0,.loop
    rts

; Draw a vertical line
; Input: a0   starting address
;        d1.w pattern
lvline:
    move.w  #479,d0         ; 470 - 1 for dbra
.loop:
    or.w  d1,(a0)
    add.w   #80,a0
    dbra    d0,.loop
    rts


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

    section .text, code

; ===========================
; Program code
; ===========================
start:
    bsr     clr_screen
    ; Horizontal lines
    lea     _fb_start,a0
    bsr     hline
    lea     (_fb_start+(479*40*4)),a0   ; Line 479 * 40 blocks * 4 bytes per block
    bsr     hline
    ; Vertical lines
    lea     _fb_start,a0
    move.l  #$80008000,d1
    bsr     lvline
    lea     (_fb_start+39*4),a0         ; Last column
    move.l  #$00010001,d1
    bsr     lvline
.end:
    trap    #14

; Clear screen
clr_screen:
    lea     _fb_start,a0            ; Framebuffer pointer
    move.w  #(_fb_len_words-1),d0   ; Framebuffer size in words - 1 (dbra)
.loop:
    move.w  #0,(a0)+                ; Clear FB
    dbra    d0,.loop                ; Decrement and loop
    rts

; Draw a full horizontal line
; Input: a0 starting address
hline:
    move.w  #39,d0                  ; 40 - 1 for dbra (640px/16bits = 39 words)
.loop:
    move.l  #$FFFFFFFF,(a0)+        ; Draw 16 white pixels (2 interleaved bitplanes -> 32 bits)
    dbra    d0,.loop
    rts

; Draw a full vertical line
; Input: a0   starting address
;        d1.w pattern
lvline:
    move.w  #479,d0                 ; 470 - 1 for dbra
.loop:
    or.l    d1,(a0)
    add.w   #160,a0
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
;   buffer  ds.w 1

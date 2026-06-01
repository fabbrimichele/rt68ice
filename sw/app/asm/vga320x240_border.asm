    section .text, code

; ===========================
; Program code
; ===========================
start:
    move.w  #0,VIDEO_CTRL                       ; Set low-res (320*240px 8bpp)
    bsr     clr_screen
    bsr     draw_bands
    bsr     draw_border
    trap    #14

; Draw bands
draw_bands:
    lea     (_fb_start+(LINE_WIDTH_B*90)),a0    ; starts at line 90

    ; Green band
    move.l  #$0000FFFF,d1                       ; Color 2 -> green
    move.l  #$00000000,d2                       ;
    move.l  #$00000000,d3                       ;
    move.l  #$00000000,d4                       ;
    move.w  #29,d5                              ; 30 horizonatl lines (-1 for dbra)
.green_loop:
    bsr     hline
    dbra    d5,.green_loop

    ; Red band
    move.l  #$00000000,d1                       ; Color 4 -> red
    move.l  #$FFFF0000,d2                       ;
    move.l  #$00000000,d3                       ;
    move.l  #$00000000,d4                       ;
    move.w  #29,d5                              ; 30 horizonatl lines (-1 for dbra)
.red_loop:
    bsr     hline
    dbra    d5,.red_loop

    rts

; Draw white border
draw_border:
    ; Horizontal lines
    move.l  #$FFFFFFFF,d1                       ; Color 15 -> white
    move.l  #$FFFFFFFF,d2                       ; and all pixels on
    move.l  #$00000000,d3                       ;
    move.l  #$00000000,d4                       ;
    lea     _fb_start,a0                        ;
    bsr     hline
    lea     (_fb_start+(239*LINE_WIDTH_B)),a0   ; Last line (in bytes)
    bsr     hline
    ; Vertical lines
    move.l  #$80008000,d1                       ; Color 15 -> white
    move.l  #$80008000,d2                       ; most left pixel
    move.l  #$00000000,d3                       ;
    move.l  #$00000000,d4                       ;
    lea     _fb_start,a0
    bsr     vline
    move.l  #$00010001,d1                       ; Color 15 -> white
    move.l  #$00010001,d2                       ; most right pixel
    move.l  #$00000000,d3                       ;
    move.l  #$00000000,d4                       ;
    ; TODO: the vertical line is not drawn/visible
    lea     (_fb_start+LINE_WIDTH_B-16),a0
    bsr     vline
    rts

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
;        d1.l planes 0 and 1
;        d2.l planes 2 and 3
;        d3.l planes 4 and 5
;        d4.l planes 6 and 7
hline:
    move.w  #19,d0
.loop:
    move.l  d1,(a0)+                ; Draw 16 white pixels (8 interleaved planes)
    move.l  d2,(a0)+                ;
    move.l  d3,(a0)+                ;
    move.l  d4,(a0)+                ;
    dbra    d0,.loop
    rts

; Draw a full vertical line
; Input: a0   starting address
;        d1.l planes 0 and 1
;        d2.l planes 2 and 3
vline:
    move.w  #239,d0                 ; 240 - 1 for dbra
.loop:
    or.l    d1,(a0)
    or.l    d2,4(a0)
    or.l    d3,8(a0)
    or.l    d4,12(a0)
    add.l   #LINE_WIDTH_B,a0
    dbra    d0,.loop
    rts


; ===========================
; Value Constants
; ===========================
; Line width in bytes
; (640 pixels) / (16 pixels per word) = 40 => 40 * (8 planes per pixel)
LINE_WIDTH_B    equ     40*8

; ===========================
; Include files
; ===========================
    include '../../lib/asm/mem_map_led.asm'
    include '../../lib/asm/mem_map_video.asm'

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

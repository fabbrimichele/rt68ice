    section .text, code

; Poll Host 2 with CPU interrupts masked. This diagnostic prints the parsed
; per-report mouse deltas alongside the first four unparsed HID report bytes.
start:
    or.w    #$0700,SR               ; Keep USB interrupts disabled

    lea     msg_title,a0
    bsr     put_str

.wait_report:
    move.w  USB_IRQ_STATUS,d1

    ; Host 1 shares the pending register. Acknowledge it so it cannot leave
    ; the shared interrupt source permanently asserted during this test.
    btst    #0,d1
    beq     .check_host2
    move.w  USB1_STATUS,d0

.check_host2:
    btst    #1,d1
    beq     .wait_report

    move.w  USB2_STATUS,d0          ; Acknowledge and read Host 2 type
    andi.w  #$0003,d0
    cmpi.w  #2,d0                   ; Device type 2 is a mouse
    bne     .wait_report

    ; USB2_MOUSE_RAW packs the parser outputs as DY:DX.
    move.w  USB2_MOUSE_RAW,d0
    moveq   #0,d2
    move.b  d0,d2
    ext.w   d2
    ext.l   d2                      ; D2.L = signed parsed DX

    lsr.w   #8,d0
    moveq   #0,d3
    move.b  d0,d3
    ext.w   d3
    ext.l   d3                      ; D3.L = signed parsed DY

    ; Preserve report words while the decimal conversion routines run.
    move.w  USB2_HID_REPORT_01,d4   ; D4.W = bytes 1:0
    move.w  USB2_HID_REPORT_23,d5   ; D5.W = bytes 3:2

    lea     msg_dx,a0
    bsr     put_str
    move.l  d2,d0
    bsr     bin_to_dec_signed

    lea     msg_dy,a0
    bsr     put_str
    move.l  d3,d0
    bsr     bin_to_dec_signed

    lea     msg_hid,a0
    bsr     put_str
    move.w  d5,d0
    bsr     bin_to_hex_w
    move.w  d4,d0
    bsr     bin_to_hex_w

    lea     msg_newline,a0
    bsr     put_str
    bra     .wait_report

; ===========================
; Include files
; ===========================
    include '../../lib/asm/mem_map_usb.asm'
    include '../../lib/asm/console_io_uart.asm'
    include '../../lib/asm/conv_dec.asm'
    include '../../lib/asm/conv_hex.asm'

; ===========================
; Data constants
; ===========================
msg_title:
    dc.b    CR,LF,"USB Host 2 raw mouse reports",CR,LF,NUL

msg_dx:
    dc.b    "DX=",NUL

msg_dy:
    dc.b    " DY=",NUL

msg_hid:
    dc.b    " HID32=",NUL

msg_newline:
    dc.b    CR,LF,NUL

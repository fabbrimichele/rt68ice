    section .text, code

; USB boot-keyboard smoke test.
; Keyboard reports from any USB host are translated to ASCII and echoed to the
; monitor serial terminal. The test suppresses repeated identical reports, so
; each physical press is printed once. It is deliberately not a full keyboard
; driver: typematic repeat, Caps Lock, and the HID-to-EmuTOS key map belong in
; the future EmuTOS keyboard driver.

start:
    or.w    #$0700,SR               ; Mask interrupts during setup
    move.l  #usb_isr,VT_INT_6       ; USB uses autovector level 6

    clr.b   keyboard_updated
    clr.b   last_host
    clr.b   last_key1
    clr.b   last_key2
    clr.b   last_key3
    clr.b   last_key4

    move.w  #$000F,USB_IRQ_ENABLE  ; Enable Hosts 1 through 4

    lea     msg_title,a0
    bsr     put_str

    and.w   #$F8FF,SR               ; Enable all interrupt levels

.wait:
    tst.b   keyboard_updated
    beq     .wait

    ; Copy the ISR report atomically. A later report remains pending and is
    ; handled after interrupts are re-enabled.
    or.w    #$0700,SR
    move.b  keyboard_host,d0
    move.b  d0,current_host
    move.b  keyboard_mods,d0
    move.b  d0,current_mods
    move.b  keyboard_key1,d0
    move.b  d0,current_key1
    move.b  keyboard_key2,d0
    move.b  d0,current_key2
    move.b  keyboard_key3,d0
    move.b  d0,current_key3
    move.b  keyboard_key4,d0
    move.b  d0,current_key4
    clr.b   keyboard_updated
    and.w   #$F8FF,SR

    ; Do not compare a keyboard report from one host with a previous report
    ; from another host.
    move.b  current_host,d0
    cmp.b   last_host,d0
    beq     .same_host
    move.b  d0,last_host
    clr.b   last_key1
    clr.b   last_key2
    clr.b   last_key3
    clr.b   last_key4

.same_host:
    ; Print every newly pressed usage ID. A key can move between boot-report
    ; slots, so each current key is compared with all previous slots.
    moveq   #0,d0
    move.b  current_key1,d0
    bsr     emit_if_new
    moveq   #0,d0
    move.b  current_key2,d0
    bsr     emit_if_new
    moveq   #0,d0
    move.b  current_key3,d0
    bsr     emit_if_new
    moveq   #0,d0
    move.b  current_key4,d0
    bsr     emit_if_new

    move.b  current_key1,last_key1
    move.b  current_key2,last_key2
    move.b  current_key3,last_key3
    move.b  current_key4,last_key4
    bra     .wait

; ---------------------------------------------------------------------------
; USB interrupt handler
; Reads each pending host's status to acknowledge it. A keyboard report is
; copied into one stable RAM snapshot; the foreground serializes and prints it.
; ---------------------------------------------------------------------------
usb_isr:
    movem.l d0-d1,-(sp)
    move.w  USB_IRQ_STATUS,d1

    btst    #0,d1
    beq     .host2
    move.w  USB1_STATUS,d0          ; Acknowledge Host 1
    andi.w  #$0003,d0
    cmpi.w  #1,d0                   ; Device type 1 = keyboard
    bne     .host2
    move.w  USB1_KEY_MODS,d0
    move.b  d0,keyboard_mods
    move.w  USB1_KEY1,d0
    move.b  d0,keyboard_key1
    move.w  USB1_KEY2,d0
    move.b  d0,keyboard_key2
    move.w  USB1_KEY3,d0
    move.b  d0,keyboard_key3
    move.w  USB1_KEY4,d0
    move.b  d0,keyboard_key4
    move.b  #1,keyboard_host
    move.b  #1,keyboard_updated

.host2:
    btst    #1,d1
    beq     .host3
    move.w  USB2_STATUS,d0          ; Acknowledge Host 2
    andi.w  #$0003,d0
    cmpi.w  #1,d0
    bne     .host3
    move.w  USB2_KEY_MODS,d0
    move.b  d0,keyboard_mods
    move.w  USB2_KEY1,d0
    move.b  d0,keyboard_key1
    move.w  USB2_KEY2,d0
    move.b  d0,keyboard_key2
    move.w  USB2_KEY3,d0
    move.b  d0,keyboard_key3
    move.w  USB2_KEY4,d0
    move.b  d0,keyboard_key4
    move.b  #2,keyboard_host
    move.b  #1,keyboard_updated

.host3:
    btst    #2,d1
    beq     .host4
    move.w  USB3_STATUS,d0          ; Acknowledge Host 3
    andi.w  #$0003,d0
    cmpi.w  #1,d0
    bne     .host4
    move.w  USB3_KEY_MODS,d0
    move.b  d0,keyboard_mods
    move.w  USB3_KEY1,d0
    move.b  d0,keyboard_key1
    move.w  USB3_KEY2,d0
    move.b  d0,keyboard_key2
    move.w  USB3_KEY3,d0
    move.b  d0,keyboard_key3
    move.w  USB3_KEY4,d0
    move.b  d0,keyboard_key4
    move.b  #3,keyboard_host
    move.b  #1,keyboard_updated

.host4:
    btst    #3,d1
    beq     .done
    move.w  USB4_STATUS,d0          ; Acknowledge Host 4
    andi.w  #$0003,d0
    cmpi.w  #1,d0
    bne     .done
    move.w  USB4_KEY_MODS,d0
    move.b  d0,keyboard_mods
    move.w  USB4_KEY1,d0
    move.b  d0,keyboard_key1
    move.w  USB4_KEY2,d0
    move.b  d0,keyboard_key2
    move.w  USB4_KEY3,d0
    move.b  d0,keyboard_key3
    move.w  USB4_KEY4,d0
    move.b  d0,keyboard_key4
    move.b  #4,keyboard_host
    move.b  #1,keyboard_updated

.done:
    movem.l (sp)+,d0-d1
    rte

; ---------------------------------------------------------------------------
; emit_if_new
; Input: D0.B = USB HID usage ID from the current report.
; Prints it only if it did not appear in the previous report.
; ---------------------------------------------------------------------------
emit_if_new:
    tst.b   d0
    beq     .done
    cmp.b   last_key1,d0
    beq     .done
    cmp.b   last_key2,d0
    beq     .done
    cmp.b   last_key3,d0
    beq     .done
    cmp.b   last_key4,d0
    beq     .done
    moveq   #0,d1
    move.b  current_mods,d1
    bsr     print_usage
.done:
    rts

; ---------------------------------------------------------------------------
; print_usage
; Input: D0.B = USB HID usage ID, D1.B = modifier bitmap.
; Unknown usage IDs are shown as '?'.
; ---------------------------------------------------------------------------
print_usage:
    bsr     hid_usage_to_ascii
    tst.b   d0
    beq     .unknown
    cmpi.b  #CR,d0
    bne     .character
    bsr     put_chr
    moveq   #LF,d0
    bsr     put_chr
    rts
.character:
    bsr     put_chr
    rts
.unknown:
    move.b  #'?',d0
    bsr     put_chr
    rts

; ---------------------------------------------------------------------------
; hid_usage_to_ascii
; Input:  D0.B = USB HID boot-keyboard usage ID, D1.B = modifiers.
; Output: D0.B = ASCII character, or zero if unsupported.
; Clobbers D2/A0.
; ---------------------------------------------------------------------------
hid_usage_to_ascii:
    moveq   #0,d2
    move.b  d0,d2

    ; Usage $04-$1D: A-Z.
    cmpi.b  #$04,d2
    blt     .not_letter
    cmpi.b  #$1D,d2
    bgt     .not_letter
    subi.b  #$04,d2
    btst    #1,d1                    ; Left Shift
    bne     .upper_letter
    btst    #5,d1                    ; Right Shift
    bne     .upper_letter
    addi.b  #'a',d2
    bra     .result
.upper_letter:
    addi.b  #'A',d2
    bra     .result

.not_letter:
    ; Usage $1E-$27: 1-0.
    cmpi.b  #$1E,d2
    blt     .controls
    cmpi.b  #$27,d2
    bgt     .controls
    subi.b  #$1E,d2
    btst    #1,d1
    bne     .shift_digit
    btst    #5,d1
    bne     .shift_digit
    cmpi.b  #9,d2
    beq     .digit_zero
    addi.b  #'1',d2
    bra     .result
.digit_zero:
    moveq   #'0',d2
    bra     .result
.shift_digit:
    lea     shifted_digits,a0
    move.b  0(a0,d2.w),d2
    bra     .result

.controls:
    cmpi.b  #$28,d2
    beq     .enter
    cmpi.b  #$2A,d2
    beq     .backspace
    cmpi.b  #$2B,d2
    beq     .tab
    cmpi.b  #$2C,d2
    beq     .space
    cmpi.b  #$2D,d2
    blt     .unsupported
    cmpi.b  #$38,d2
    bgt     .unsupported
    subi.b  #$2D,d2
    btst    #1,d1
    bne     .shift_symbol
    btst    #5,d1
    bne     .shift_symbol
    lea     symbols,a0
    move.b  0(a0,d2.w),d2
    bra     .result
.shift_symbol:
    lea     shifted_symbols,a0
    move.b  0(a0,d2.w),d2
    bra     .result
.enter:
    moveq   #CR,d2
    bra     .result
.backspace:
    moveq   #BS,d2
    bra     .result
.tab:
    moveq   #$09,d2
    bra     .result
.space:
    moveq   #SPACE,d2
    bra     .result
.unsupported:
    clr.b   d2

.result:
    move.b  d2,d0
    rts

; ===========================
; Includes
; ===========================
    include '../../lib/asm/mem_map_usb.asm'
    include '../../lib/asm/console_io_uart.asm'
    include '../../lib/asm/isr_vector.asm'

; ===========================
; Data
; ===========================
shifted_digits:
    dc.b    "!@#$%^&*()"

; Usage IDs $2D-$38. Usage $32 is the non-US #/~ key and is unsupported by
; this compact test, so its table entry is zero.
symbols:
    dc.b    $2D,$3D,$5B,$5D,$5C,$00,$3B,$27,$60,$2C,$2E,$2F
shifted_symbols:
    dc.b    $5F,$2B,$7B,$7D,$7C,$00,$3A,$22,$7E,$3C,$3E,$3F

msg_title:
    dc.b    CR,LF,"USB keyboard test (Hosts 1-4)",CR,LF
    dc.b    "Type on a boot-protocol keyboard: ",NUL

; ===========================
; RAM data
; ===========================
    section .bss
keyboard_updated:
    ds.b    1
keyboard_host:
    ds.b    1
keyboard_mods:
    ds.b    1
keyboard_key1:
    ds.b    1
keyboard_key2:
    ds.b    1
keyboard_key3:
    ds.b    1
keyboard_key4:
    ds.b    1

current_host:
    ds.b    1
current_mods:
    ds.b    1
current_key1:
    ds.b    1
current_key2:
    ds.b    1
current_key3:
    ds.b    1
current_key4:
    ds.b    1

last_host:
    ds.b    1
last_key1:
    ds.b    1
last_key2:
    ds.b    1
last_key3:
    ds.b    1
last_key4:
    ds.b    1

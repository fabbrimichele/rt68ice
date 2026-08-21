    section .text, code

; ===========================
; Program code
; ===========================
start:
    or.w    #$0700,SR           ; Mask interrupts during setup
    move.l  #usb_isr,VT_INT_6   ; Set USB interrupt handler
    clr.w   gamepad_state_1
    clr.w   gamepad_state_2
    clr.w   gamepad_state_3
    clr.w   gamepad_state_4
    move.w  #$000F,USB_IRQ_ENABLE ; Enable Host 1 through Host 4 USB interrupts
    and.w   #$F8FF,SR           ; Enable all interrupts on 68000 (Clear mask bits)

; Stay resident so USB reports can invoke usb_isr.  trap #14 returns to the
; monitor, which would otherwise end this program immediately.
.wait:
    bra     .wait

usb_isr:
    movem.l d0-d1,-(sp)
    move.w  USB_IRQ_STATUS,d1   ; Snapshot pending hosts without clearing them

    btst    #0,d1
    beq     .host2
    move.w  USB1_STATUS,d0      ; Acknowledge USB host 1
    andi.w  #$0003,d0           ; Update LEDs only for a gamepad
    cmpi.w  #3,d0
    bne     .host2
    move.w  USB1_GAMEPAD,d0
    move.w  d0,gamepad_state_1

.host2:
    btst    #1,d1
    beq     .host3
    move.w  USB2_STATUS,d0      ; Acknowledge USB host 2
    andi.w  #$0003,d0           ; A mouse report must not blank the LEDs
    cmpi.w  #3,d0
    bne     .host3
    move.w  USB2_GAMEPAD,d0
    move.w  d0,gamepad_state_2

.host3:
    btst    #2,d1
    beq     .host4
    move.w  USB3_STATUS,d0      ; Acknowledge USB host 3
    andi.w  #$0003,d0           ; A mouse report must not blank the LEDs
    cmpi.w  #3,d0
    bne     .host4
    move.w  USB3_GAMEPAD,d0
    move.w  d0,gamepad_state_3

.host4:
    btst    #3,d1
    beq     .done
    move.w  USB4_STATUS,d0      ; Acknowledge USB host 4
    andi.w  #$0003,d0           ; A mouse report must not blank the LEDs
    cmpi.w  #3,d0
    bne     .done
    move.w  USB4_GAMEPAD,d0
    move.w  d0,gamepad_state_4

.done:
    ; HID hosts report periodically even when idle.  Combine the last state
    ; from every gamepad so an idle report from one host cannot erase another
    ; host's currently pressed buttons from the LED display.
    move.w  gamepad_state_1,d0
    or.w    gamepad_state_2,d0
    or.w    gamepad_state_3,d0
    or.w    gamepad_state_4,d0
    move.w  d0,LEDS
    movem.l (sp)+,d0-d1
    rte

; ===========================
; Value Constants
; ===========================
DLY_VAL     equ     312500   ;

; ===========================
; Include files
; ===========================
    include '../../lib/asm/mem_map_leds.asm'
    include '../../lib/asm/mem_map_usb.asm'
    include '../../lib/asm/isr_vector.asm'

; ===========================
; Data Constants
; Must be after code to avoid alignment issues
; ===========================
; Add here data costants, e.g. `msg_hello dc.b    "Type something:",CR,LF,NUL`

; ===========================
; RAM Data Section
; ===========================
    section .bss
gamepad_state_1:
    ds.w    1
gamepad_state_2:
    ds.w    1
gamepad_state_3:
    ds.w    1
gamepad_state_4:
    ds.w    1

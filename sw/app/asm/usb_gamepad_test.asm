    section .text, code

; ===========================
; Program code
; ===========================
start:
    or.w    #$0700,SR           ; Mask interrupts during setup
    move.l  #usb_isr,VT_INT_6   ; Set USB interrupt handler
    move.w  #$0003,USB_IRQ_ENABLE ; Enable Host 1 and Host 2 USB interrupts
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
    move.w  d0,LEDS

.host2:
    btst    #1,d1
    beq     .done
    move.w  USB2_STATUS,d0      ; Acknowledge USB host 2
    andi.w  #$0003,d0           ; A mouse report must not blank the LEDs
    cmpi.w  #3,d0
    bne     .done
    move.w  USB2_GAMEPAD,d0
    move.w  d0,LEDS

.done:
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
; Add here variables and buffers, e.g. `buffer ds.b 80`

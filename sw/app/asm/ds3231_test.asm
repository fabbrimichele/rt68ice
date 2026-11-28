; Standalone DS3231 diagnostic for I2cDevice. Run from the ROM monitor.
; Read-only: no RTC control/time writes and no EEPROM access.
; Build: make target/app/ds3231_test.bin
    section .text, code
start:
    move.w  sr,-(sp)
    ori.w   #$0700,sr
    clr.w   last_command
    clr.w   last_status
    lea     msg_title,a0
    bsr     put_str
menu:
    lea     msg_menu,a0
    bsr     put_str
    bsr     get_chr
    cmpi.b  #'e',d0
    beq     finish
    cmpi.b  #ESC,d0
    beq     finish
    cmpi.b  #'1',d0
    beq     show_time
    cmpi.b  #'2',d0
    beq     show_temperature
    cmpi.b  #'3',d0
    beq     show_registers
    bra     menu
finish:
    move.w  (sp)+,sr
    trap    #14

show_time:
    bsr     ds3231_snapshot
    tst.l   d0
    bne     io_error
    lea     datetime,a1
    bsr     ds3231_decode
    tst.l   d0
    bne     invalid_time
    bsr     rtc_print_datetime
    bsr     show_flags
    bra     menu
invalid_time:
    lea     msg_invalid,a0
    bsr     put_str
    bsr     show_flags
    bra     menu

show_temperature:
    bsr     ds3231_snapshot
    tst.l   d0
    bne     io_error
    lea     msg_temperature,a0
    bsr     put_str
    ; Signed two's-complement quarter-degrees, including -0.25 C.
    moveq   #0,d0
    move.b  rtc_raw+$11,d0
    lsl.w   #8,d0
    move.b  rtc_raw+$12,d0
    asr.w   #6,d0
    ext.l   d0
    tst.l   d0
    bpl     .positive
    neg.l   d0
    move.l  d0,-(sp)
    moveq   #'-',d0
    bsr     put_chr
    move.l  (sp)+,d0
.positive:
    move.w  d0,d5
    andi.w  #3,d5
    lsr.l   #2,d0
    bsr     bin_to_dec
    moveq   #'.',d0
    bsr     put_chr
    move.w  d5,d0
    mulu.w  #25,d0
    bsr     rtc_put_two_digits
    lea     msg_celsius,a0
    bsr     put_str
    bra     menu

show_registers:
    bsr     ds3231_snapshot
    tst.l   d0
    bne     io_error
    lea     msg_registers,a0
    bsr     put_str
    lea     rtc_raw,a2
    moveq   #0,d5
.loop:
    move.w  d5,d0
    bsr     print_hex_byte
    moveq   #'=',d0
    bsr     put_chr
    moveq   #0,d0
    move.b  (a2)+,d0
    bsr     print_hex_byte
    moveq   #' ',d0
    bsr     put_chr
    addq.w  #1,d5
    cmpi.w  #19,d5
    bne     .loop
    lea     msg_newline,a0
    bsr     put_str
    bsr     show_flags
    bra     menu

show_flags:
    btst    #7,rtc_raw+$0f
    beq     .oscillator
    lea     msg_osf,a0
    bsr     put_str
.oscillator:
    btst    #7,rtc_raw+$0e
    beq     .return
    lea     msg_eosc,a0
    bsr     put_str
.return:
    rts

io_error:
    cmpi.l  #1,d0
    bne     .timeout
    lea     msg_nack,a0
    bra     .print
.timeout:
    lea     msg_timeout,a0
.print:
    bsr     put_str
    lea     msg_command,a0
    bsr     put_str
    move.w  last_command,d0
    bsr     bin_to_hex_w
    lea     msg_status,a0
    bsr     put_str
    move.w  last_status,d0
    bsr     bin_to_hex_w
    lea     msg_reset,a0
    bsr     put_str
    bra     finish

; D0.B -> two hexadecimal digits. Preserves D0-D2.
print_hex_byte:
    movem.l d0-d2,-(sp)
    move.w  d0,d2
    lsr.w   #4,d0
    bsr     .nibble
    move.w  d2,d0
    bsr     .nibble
    movem.l (sp)+,d0-d2
    rts
.nibble:
    andi.w  #$0f,d0
    addi.b  #'0',d0
    cmpi.b  #'9',d0
    bls     .emit
    addq.b  #7,d0
.emit:
    bra     put_chr

    include '../../lib/asm/ds3231_rtc.asm'
    include '../../lib/asm/console_io_uart.asm'
    include '../../lib/asm/conv_hex.asm'
    include '../../lib/asm/conv_dec.asm'

msg_title:
    dc.b CR,LF,'** DS3231 / I2C test (read-only) **',CR,LF
    dc.b 'RTC address $68. Generic I2C registers at $02128000.',CR,LF,NUL
msg_menu:
    dc.b CR,LF,'1. Read date/time',CR,LF,'2. Read temperature',CR,LF
    dc.b '3. Dump registers $00-$12',CR,LF,'e. Exit to monitor',CR,LF,NUL
msg_invalid:
    dc.b 'Invalid calendar or century outside 2000-2099. Run ds3231_set.',CR,LF,NUL
msg_temperature:
    dc.b 'Temperature: ',NUL
msg_celsius:
    dc.b ' C (latest stored sample)',CR,LF,NUL
msg_registers:
    dc.b 'DS3231 registers (hex):',CR,LF,NUL
msg_osf:
    dc.b 'WARNING: OSF=1; clock has stopped, time may be invalid. Run ds3231_set.',CR,LF,NUL
msg_eosc:
    dc.b 'WARNING: EOSC=1; oscillator disabled on battery. Run ds3231_set.',CR,LF,NUL
msg_nack:
    dc.b 'ERROR: I2C NACK (address or transmitted byte not acknowledged).',CR,LF,NUL
msg_timeout:
    dc.b 'ERROR: I2C BUSY did not assert or finish before poll limit.',CR,LF,NUL
msg_command:
    dc.b 'Last command=$',NUL
msg_status:
    dc.b ' status=$',NUL
msg_reset:
    dc.b CR,LF,'Reset FPGA before retry; transaction may be unfinished.',CR,LF,NUL
msg_newline:
    dc.b CR,LF,NUL

    section .bss
    even
last_command: ds.w 1
last_status:  ds.w 1
rtc_raw:      ds.b 19
datetime:     ds.b DT_SIZE

; Ask for a decimal date/time, set the RTC, and return to the ROM monitor.
; Input: YYYY-MM-DD HH:MM:SS, years 2000-2099, 24-hour clock; ESC cancels.
; Uses generic I2cDevice, not DS1302. DS3231 stores BCD.
; Enables the backup oscillator and clears OSF only after a validated time write.
; Build: make target/app/ds3231_set.bin
    section .text, code

start:
    move.w  sr,-(sp)
    ori.w   #$0700,sr
    clr.w   last_command
    clr.w   last_status
.prompt:
    lea     msg_prompt,a0
    bsr     put_str
    lea     input,a2
    moveq   #0,d4
.input:
    bsr     get_chr
    cmpi.b  #ESC,d0
    beq     .cancel
    cmpi.b  #CR,d0
    beq     .enter
    cmpi.b  #LF,d0
    bne     .not_lf
    tst.w   d4                     ; Ignore a leftover LF from CR/LF input.
    beq     .input
    bra     .enter
.not_lf:
    cmpi.b  #BS,d0
    beq     .backspace
    cmpi.b  #DEL,d0
    beq     .backspace
    cmpi.b  #' ',d0
    blo     .input
    cmpi.b  #126,d0
    bhi     .input
    cmpi.w  #19,d4
    beq     .input
    move.b  d0,0(a2,d4.w)
    addq.w  #1,d4
    bsr     put_chr
    bra     .input
.backspace:
    tst.w   d4
    beq     .input
    subq.w  #1,d4
    moveq   #BS,d0
    bsr     put_chr
    moveq   #' ',d0
    bsr     put_chr
    moveq   #BS,d0
    bsr     put_chr
    bra     .input
.enter:
    moveq   #CR,d0
    bsr     put_chr
    moveq   #LF,d0
    bsr     put_chr
    cmpi.w  #19,d4
    bne     .invalid
    cmpi.b  #'2',0(a2)
    bne     .invalid
    cmpi.b  #'0',1(a2)
    bne     .invalid
    cmpi.b  #'-',4(a2)
    bne     .invalid
    cmpi.b  #'-',7(a2)
    bne     .invalid
    cmpi.b  #' ',10(a2)
    bne     .invalid
    cmpi.b  #':',13(a2)
    bne     .invalid
    cmpi.b  #':',16(a2)
    bne     .invalid

    lea     datetime,a1
    lea     input_fields(pc),a3
    moveq   #5,d4
.parse:
    moveq   #0,d2
    move.b  (a3)+,d2
    lea     0(a2,d2.w),a0
    bsr     read_two_digits
    tst.w   d1
    bne     .invalid
    moveq   #0,d3
    move.b  (a3)+,d3
    move.b  d0,0(a1,d3.w)
    dbra    d4,.parse
    bsr     rtc_validate_datetime
    tst.l   d0
    bne     .invalid
    bsr     rtc_set_weekday

    ; Do not touch the RTC until the complete input has been validated.
    bsr     ds3231_set_datetime
    tst.l   d0
    bne     .io_error
    bsr     ds3231_snapshot
    tst.l   d0
    bne     .io_error
    bsr     ds3231_decode
    tst.l   d0
    bne     .read_error
    lea     msg_set,a0
    bsr     put_str
    bsr     rtc_print_datetime
    bra     .done
.invalid:
    lea     msg_invalid,a0
    bsr     put_str
    bra     .prompt
.cancel:
    lea     msg_cancel,a0
    bsr     put_str
    bra     .done
.io_error:
    lea     msg_io_error,a0
    bsr     put_str
    bra     .done
.read_error:
    lea     msg_read_error,a0
    bsr     put_str
.done:
    move.w  (sp)+,sr
    trap    #14

; Read exactly two ASCII decimal digits at A0. D0=value, D1=0 if valid.
; Clobbers D0-D2/A0. Does not change the RTC.
read_two_digits:
    moveq   #0,d0
    move.b  (a0)+,d0
    subi.b  #'0',d0
    cmpi.b  #9,d0
    bhi     .invalid
    mulu.w  #10,d0
    moveq   #0,d2
    move.b  (a0)+,d2
    subi.b  #'0',d2
    cmpi.b  #9,d2
    bhi     .invalid
    add.w   d2,d0
    moveq   #0,d1
    rts
.invalid:
    moveq   #1,d1
    rts

    include '../../lib/asm/ds3231_rtc.asm'
    include '../../lib/asm/console_io_uart.asm'

input_fields:
    dc.b    2,DT_YEAR,5,DT_MONTH,8,DT_DATE
    dc.b    11,DT_HOURS,14,DT_MINUTES,17,DT_SECONDS
msg_prompt:
    dc.b    'Enter YYYY-MM-DD HH:MM:SS (2000-2099, 24-hour; ESC cancels): ',NUL
msg_invalid:
    dc.b    'Invalid date/time. Use decimal YYYY-MM-DD HH:MM:SS.',CR,LF,NUL
msg_cancel:
    dc.b    CR,LF,'Cancelled; RTC unchanged.',CR,LF,NUL
msg_set:
    dc.b    'RTC set: ',NUL
msg_io_error:
    dc.b    'I2C NACK/timeout. RTC may be partially written. Reset FPGA before retry.',CR,LF,NUL
msg_read_error:
    dc.b    'RTC written, but readback is invalid or unstable.',CR,LF,NUL

    section .bss
    even
last_command: ds.w 1
last_status: ds.w 1
rtc_raw: ds.b 19
input:
    ds.b    19
datetime:
    ds.b    DT_SIZE

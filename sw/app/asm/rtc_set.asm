; Ask for a decimal date/time, set the RTC, and return to the ROM monitor.
; Input: YYYY-MM-DD HH:MM:SS, years 2000-2099, 24-hour clock; ESC cancels.
; The VHDL converts binary to BCD. Do not convert the values again here.
; Build: make target/app/rtc_set.bin
    section .text, code

start:
    move.w  sr,-(sp)
    ori.w   #$0700,sr
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
    move.b  RTC_CONTROL,d6
    move.b  #0,RTC_CONTROL
    move.b  #RTC_HALT_ZERO,RTC_SECONDS
    move.b  DT_MINUTES(a1),RTC_MINUTES
    move.b  DT_HOURS(a1),RTC_HOURS
    move.b  DT_DATE(a1),RTC_DATE
    move.b  DT_MONTH(a1),RTC_MONTH
    move.b  DT_WEEKDAY(a1),RTC_WEEKDAY
    move.b  DT_YEAR(a1),RTC_YEAR
    move.b  DT_SECONDS(a1),RTC_SECONDS ; Clear CH and start at the entered time.
    move.b  d6,RTC_CONTROL           ; Restore the previous write protection.

    bsr     rtc_read_datetime
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

    include '../../lib/asm/rtc_datetime.asm'
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
msg_read_error:
    dc.b    'RTC written, but readback is invalid or unstable.',CR,LF,NUL

    section .bss
input:
    ds.b    19
datetime:
    ds.b    DT_SIZE

; Upper-byte registers: use MOVE.B at these even addresses.
; Clock/calendar values are binary on the CPU side (the VHDL converts BCD).
RTC_SECONDS     equ     $00F28000
RTC_MINUTES     equ     $00F28002
RTC_HOURS       equ     $00F28004
RTC_DATE        equ     $00F28006
RTC_MONTH       equ     $00F28008
RTC_WEEKDAY     equ     $00F2800A
RTC_YEAR        equ     $00F2800C
RTC_CONTROL     equ     $00F2800E

; CPU decimal 80 is converted to native $80: CH=1, seconds=00.
RTC_HALT_ZERO   equ     80

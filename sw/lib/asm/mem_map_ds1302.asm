; Upper-byte registers: use MOVE.B at these even addresses.
; Clock/calendar values are binary on the CPU side (the VHDL converts BCD).
RTC_SECONDS     equ     $02128000
RTC_MINUTES     equ     $02128002
RTC_HOURS       equ     $02128004
RTC_DATE        equ     $02128006
RTC_MONTH       equ     $02128008
RTC_WEEKDAY     equ     $0212800A
RTC_YEAR        equ     $0212800C
RTC_CONTROL     equ     $0212800E

; CPU decimal 80 is converted to native $80: CH=1, seconds=00.
RTC_HALT_ZERO   equ     80

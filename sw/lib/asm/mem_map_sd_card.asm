;------------------------------------------------------------------------------------
; SPI FLASH
;------------------------------------------------------------------------------------
SD_BASE      EQU $00F20000
SD_CTRL      EQU SD_BASE+$1           ; Lower byte status and control reg
SD_DATA      EQU SD_BASE+$3           ; Lower byte tx and rx data

; SD Card commands
FL_CMD_RD   EQU 1           ; Read command

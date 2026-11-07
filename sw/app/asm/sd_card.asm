; Legacy SD sector-read example adapted to the word-data spi_master interface.
; Uses 8-bit SPI transfers at the conservative initialization clock.
PRINT MACRO
    LEA     \1,A0
    BSR     put_str
    ENDM

PCHAR MACRO
    MOVE.B  \1,D0
    BSR     put_chr
    ENDM

    section .text, code
; ===========================
; Program code
; ===========================

START:
    MOVE.W  #$00,LEDS

    ; Init
    PRINT   MSG_INIT
    BSR     SD_INIT
    CMP.B   #0,D2
    BNE     .ERR
    PRINT   MSG_OK

    ; Read Sector
    PRINT   MSG_RD_SEC
    MOVE.L  #0,D1           ; Sector 0
    LEA     SECT_BUFF,A1    ; Buffer pointer
    BSR     SD_READ_SEC
    CMP.B   #0,D2
    BNE     .ERR
    ; Check the MBR signature (last 2 bytes)
    CMP.B   #$55,510(A1)
    BNE     .ERR
    CMP.B   #$AA,511(A1)
    BNE     .ERR
    PRINT   MSG_OK

    ; Print Buffer address
    PRINT   MSG_BUF_AD
    LEA     SECT_BUFF,A0
    MOVE.L  A0,D0
    BSR     bin_to_hex

    BRA     .END
.ERR:
    PRINT   MSG_ERR
    PRINT   MSG_BYTE
    BSR     bin_to_hex_w  ; Print last byte read
    PCHAR   #LF
    PRINT   MSG_CMD
    MOVE.B  D1,D0
    BSR     bin_to_hex_w  ; Print last command
    PCHAR   #LF
.END:
    TRAP    #14

; -----------------------------------------
; PRINT_SEC: Print buf sector
; Inputs     A1   = Dest buffer (512 bytes)
; -----------------------------------------
PRINT_SEC:
    RTS

; -----------------------------------------
; SD_INIT: Initialize SD card
; Outputs: D0 - last byte read
;          D1 - last command executed ($FF no card)
;          D2 - 0 OK, 1 error
; -----------------------------------------
SD_INIT:
    MOVEM.L D4/A0,-(SP)
    MOVE.B  #$FF,D0
    MOVE.B  #$FF,D1

    ; This SPI core has no card-detect flag; initialization tests card presence.
    move.b  #$0E,SD_CONFIG  ; 8-bit SPI, clk/128 (195.3125 kHz)

    ; Reset
    BSR     SD_RESET

    ; CMD0
    MOVE.B  #$00,D1
    LEA     CMD0,A0
    BSR     SD_CMD
    CMP.B   #$01,D0
    BNE     .ERR

    ; CMD8
    MOVE.B  #$08,D1
    BSR     SD_CMD8
    CMP.B   #$AA,D0
    BNE     .ERR

.INIT_LOOP:
    ; CMD55
    MOVE.B  #$55,D1
    LEA     CMD55,A0
    BSR     SD_CMD
    CMP.B   #$01,D0
    BNE     .ERR

    ; ACMD41
    MOVE.B  #$41,D1
    LEA     ACMD41,A0
    BSR     SD_CMD
    CMP.B   #$01,D0
    BEQ     .INIT_LOOP  ; Card is still busy, repeat init loop
    CMP.B   #$00,D0
    BNE     .ERR

    ; Card initialized
    MOVE.B  #$0,D2      ; D2 = OK
    BRA     .END
.ERR:
    MOVE.B  #$1,D2      ; D2 = ERR
.END:
    MOVEM.L (SP)+,D4/A0
    RTS

; -----------------------------------------
; SD_READ_SEC: Sends CMD17 (Read block)
; Inputs: D1.L = Sector address (4 bytes)
;         A1   = Dest buffer (512 bytes)
; Output: D2   = result, if 0 OK else ERROR
; -----------------------------------------
SD_READ_SEC:
    MOVEM.L D0-D1/A0-A1,-(SP)
    BSR     SD_SELECT      ; Assert SD chip select

    ; CMD17 is created dinamically because of the sector address
    LEA     CMD_BUFF,A0
    MOVE.B  #$51,0(A0)      ; CMD17 Header
    MOVE.L  D1,D2           ; CMD17 Sector address
    MOVE.B  D2,4(A0)        ; Copy byte 0
    LSR.L   #8,D2
    MOVE.B  D2,3(A0)        ; Copy byte 1
    LSR.L   #8,D2
    MOVE.B  D2,2(A0)        ; Copy byte 1
    LSR.L   #8,D2
    MOVE.B  D2,1(A0)        ; Copy byte 1
    MOVE.B  #$FF,5(A0)      ; CMD17 CRC (dummy)

    ; Send CMD17 command
    BSR     SD_SEND_CMD
    BSR     WAIT_R1
    CMP.B   #$00,D0         ; Is it $00?
    BNE     .EXIT           ; If not $00 error

    ; Wait start token $FE
    MOVE.B  #$FF,D0         ; Dummy write data
.WAIT_START:
    BSR     SD_WRITE        ; Clock SPI while wait
    move.b  SD_DATA+1,D1      ; Wait until different from $FF
    CMP.B   #$FE,D1
    BNE     .WAIT_START     ; TODO: Implement timeout

    ; Read sector data
    MOVE.B  #$FF,D0         ; Dummy write data
    MOVE.W  #511,D2         ; Read 512 bytes (-1 for DBRA)
.READ_DATA:
    MOVE.B  #$FF,D0         ; Keep MOSI high while receiving
    BSR     SD_WRITE        ; Clock SPI while wait
    move.b  SD_DATA+1,D0      ; Read data
    MOVE.B  D0,(A1)+        ; Copy to buffer
    DBRA    D2,.READ_DATA

    ; Read CRC
    MOVE.B  #$FF,D0
    BSR     SD_WRITE        ; Clock SPI while wait
    move.b  SD_DATA+1,D1      ; Read CRC (1st byte)
    BSR     SD_WRITE        ; Clock SPI while wait
    move.b  SD_DATA+1,D2      ; Read CRC (2nd byte)
    ;MOVE.W  #$01,LEDS
    ; TODO: calculate CRC
    MOVE.B  #0,D1           ; Read successful

.EXIT:
    MOVE.B  D1,D2
    BSR     SD_DESELECT
    MOVEM.L (SP)+,D0-D1/A0-A1
    RTS

; -----------------------------------------
; SD_RESET: Resets SD card
; -----------------------------------------
SD_RESET:
    MOVEM.L D0/D1,-(SP)
    BSR     SD_DESELECT    ; Release SD chip select
    MOVE.B  #9,D1           ; Repeat 10-1 times (DBRA)
    MOVE.B  #$FF,D0
.LOOP:
    BSR     SD_WRITE
    DBRA    D1,.LOOP
    MOVEM.L (SP)+,D0/D1
    RTS

; -----------------------------------------
; SD_CMD: Sends CMD
; Inputs: A0 command pointer
; Output: D0 result
; -----------------------------------------
SD_CMD:
    MOVEM.L A0,-(SP)
    BSR     SD_SELECT      ; Assert SD chip select
    BSR     SD_SEND_CMD
    BSR     WAIT_R1
    BSR     SD_DESELECT    ; Release SD chip select
    MOVEM.L (SP)+,A0
    RTS

; -----------------------------------------
; SD_CMD8: Sends CMD8
; Output: D0 result, if $01 OK else ERROR
; -----------------------------------------
SD_CMD8:
    MOVEM.L A0,-(SP)
    BSR     SD_SELECT      ; Assert SD chip select
    LEA     CMD8,A0
    BSR     SD_SEND_CMD
    BSR     WAIT_R1
    CMP.B   #$01,D0         ; Is it $01?
    BNE     .EXIT           ; If not $01, card rejected CMD8
    ; Read remaining bytes
    MOVE.B  #$FF,D0         ; Send dummy to get clock
.LOOP:
    JSR     SD_WRITE
    JSR     SD_WRITE
    JSR     SD_WRITE
    JSR     SD_WRITE
    move.b  SD_DATA+1,D0      ; Wait until different from $FF
.EXIT:
    BSR     SD_DESELECT    ; Release SD chip select
    MOVEM.L (SP)+,A0
    RTS

; -----------------------------------------
; SD_SEND_CMD: Sends the 6-byte from memory
; Inputs: A0 - packet address
; -----------------------------------------
SD_SEND_CMD:
    MOVEM.L D0/D1/A0,-(SP)
    MOVE.W  #5,D1               ; Send 6 bytes
.LOOP:
    MOVE.B  (A0)+,D0
    JSR     SD_WRITE            ; Write byte
    DBRA    D1,.LOOP
    MOVEM.L (SP)+,D0/D1/A0
    RTS

; ---------------------------------
; WAIT_R1
; Outputs: D0 response byte
; ---------------------------------
; TODO: timeout if returns always $FF
WAIT_R1:
    MOVEM.L D1,-(SP)
    MOVE.B  #$FF,D0         ; Dummy write data
.WAIT:
    BSR     SD_WRITE        ; Clock SPI while wait
    move.b  SD_DATA+1,D1      ; Wait until different from $FF
    CMP.B   #$FF,D1
    BEQ     .WAIT
.EXIT:
    MOVE.B  D1,D0
    MOVEM.L (SP)+,D1
    RTS

; -----------------------------------------
; SD_WRITE: Writes 1 byte to SD card
; Inputs: D0 - byte to be written
; -----------------------------------------
SD_WRITE:
    MOVEM.L D1,-(SP)
.WAIT:
    BTST    #0,SPI_STATUS ; BUSY is the only transfer-status flag
    BNE     .WAIT
    MOVE.W  D0,SD_DATA      ; Only the low 8 bits are shifted in byte mode
    MOVE.W  SD_CS_STATE,D1
    ORI.W   #1,D1           ; START while preserving chip-select state
    move.b  D1,SD_CTRL
.DONE_WAIT:
    BTST    #0,SPI_STATUS
    BNE     .DONE_WAIT
    MOVEM.L (SP)+,D1
    RTS

SD_SELECT:
    MOVE.W  #$02,SD_CS_STATE
    move.b  #$02,SD_CTRL
    RTS

SD_DESELECT:
    MOVE.W  #$00,SD_CS_STATE
    move.b  #$00,SD_CTRL
    RTS

; ---------------------------------
; DELAY: Delay
; ---------------------------------
DELAY:
    MOVEM.L D3,-(SP)
    MOVE.L  #DLY_VAL,D3     ;
DLY_LOOP:
    SUBQ.L  #1,D3           ; 4 cycles
    BNE     DLY_LOOP        ; 10 cycles when taken
    MOVEM.L (SP)+,D3
    RTS

; ===========================
; Constants
; ===========================
DLY_VAL     EQU     1000

; SD card commands
CMD0        DC.B  $40, $00, $00, $00, $00, $95
CMD8        DC.B  $48, $00, $00, $01, $AA, $87
CMD55       DC.B  $77, $00, $00, $00, $00, $FF
ACMD41      DC.B  $69, $40, $00, $00, $00, $FF

; Messages
MSG_INIT    DC.B 'Init SD Card... ',NUL
MSG_RD_SEC  DC.B 'Read 1st Sector... ',NUL
MSG_BUF_AD  DC.B 'Buffer Address: ',NUL
MSG_OK      DC.B 'OK',LF,NUL
MSG_ERR     DC.B 'ERR',LF,NUL
MSG_BYTE    DC.B 'BYTE: ',NUL
MSG_CMD     DC.B 'CMD: ',NUL

; ===========================
; Include files (Macro at top)
; ===========================
    INCLUDE '../../lib/asm/mem_map_sd_card.asm'
    INCLUDE '../../lib/asm/mem_map_leds.asm'
    INCLUDE '../../lib/asm/console_io_uart.asm'
    INCLUDE '../../lib/asm/conv_hex.asm'

; ===========================
; RAM Data Section
; ===========================
    section .bss
SD_CS_STATE DS.W 1
CMD_BUFF    DS.B 6
    ALIGN 2
SECT_BUFF   DS.B 512

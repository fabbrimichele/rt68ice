; ------------------------------
; ROM Monitor (ROM version)
; ------------------------------
    section .text, code

IMAGE_MAGIC equ     $52543638   ; ASCII "RT68"
FLASH_IMAGE_OFFSET equ $00100000
FLASH_IMAGE_MAX equ $02000000-FLASH_IMAGE_OFFSET-16
SPI_DATA equ $00F20001
SPI_STATUS equ $00F20005
SPI_CONFIG equ $00F20007

; ------------------------------
; Initial Reset sp and PC in Vector Table
; ------------------------------
    dc.l _stack_top         ; Reset Stack Pointer (sp, sp move downward far from SO_RAM)
    dc.l start              ; Reset Program counter (PC) (point to the beginning of code)

; ------------------------------
; Program code
; ------------------------------
start:
    jsr     init_vector_table
    jsr     init_palette
    jsr     uart_init
    lea     msg_title,a0
    bsr     put_str

mon_entry:
new_cmd:
    lea     IN_BUF,a5       ; a5 = current buffer position
    move.b  #CR,d0
    bsr     put_chr
    move.b  #LF,d0
    bsr     put_chr
    move.b  #'>',d0
    bsr     put_chr
loop:
    bsr     get_chr

    cmp.b   #CR,d0          ; Check for Enter
    beq     process_cmd     ; then process command
    cmp.b   #BS,d0          ; Check for Backspace
    beq     bs_handler
    cmp.b   #DEL,d0          ; Check for Backspace
    beq     bs_handler

    cmp.l   #IN_BUF_END,a5  ; Check if buffer is full
    beq     buffer_full

    bsr     put_chr         ; print character
    move.b  d0,(a5)+        ; Store d0 into buffer, then increment a5
    bra     loop

; --------------------------------------
; Backspace Handler
; --------------------------------------
bs_handler:
    ; 1. Check if the buffer is empty
    cmp.l   #IN_BUF,a5          ; Compare current pointer (a5) to start of buffer
    beq     loop                ; If a5 == IN_BUF, buffer is empty (do nothing)

    ; 2. Correct the buffer pointer
    subq.l  #1,a5               ; Decrement a5: move pointer back one position

    ; 3. Correct the terminal display (echo the standard sequence)
    ; Send BS (0x08) to move cursor left
    move.b  #BS,d0
    bsr     put_chr

    ; Send Space (0x20) to erase the character
    move.b  #' ',d0
    bsr     put_chr

    ; Send BS (0x08) again to move cursor back to erased position
    move.b  #BS,d0
    bsr     put_chr

    bra     loop                ; Continue input loop

; --------------------------------------
; Buffer Full Handler
; --------------------------------------
buffer_full:
    ; Send BEL (7) once to alert the user that the buffer is full
    move.b  #BEL,d0
    bsr     put_chr

    bsr     get_chr             ; Get the next character
    ; Check 1: Enter pressed (CR)
    cmp.b   #CR,d0
    beq     process_cmd         ; Yes, go process the command
    ; Check 2: Backspace or Delete
    cmp.b   #BS,d0
    beq     bs_handler

    ; Discard all other input
    bra     buffer_full

process_cmd:
    move.b  #0,(a5)         ; Null-terminate the string in the buffer
    move.b  #CR,d0
    bsr     put_chr
    move.b  #LF,d0
    bsr     put_chr

    ; Parse DUMP
    bsr     parse_dump
    btst    #0,d0
    bne     dump_cmd        ; d0.0 = 1 execute DUMP

    ; Parse WRITE
    bsr     parse_write
    btst    #0,d0
    bne     write_cmd       ; d0.0 = 1 execute WRITE

    ; Parse HELP
    bsr     parse_help
    btst    #0,d0
    bne     help_cmd        ; d0.0 = 1 execute HELP

    lea     loadflash_str,a1
    bsr     parse_no_args
    btst    #0,d0
    bne     loadflash_cmd

    lea     boot_str,a1
    bsr     parse_no_args
    btst    #0,d0
    bne     boot_cmd

    ; Parse LOAD
    bsr     parse_load
    btst    #0,d0
    bne     load_cmd        ; d0.0 = 1 execute LOAD

    ; Parse RUN
    bsr     parse_run
    btst    #0,d0
    bne     run_cmd         ; d0.0 = 1 execute RUN

    ; Parse FBCLR
    bsr     parse_fbclr
    btst    #0,d0
    bne     fbclr_cmd       ; d0.0 = 1 execute FBCLR

unknown_cmd:
    ; Print error message
    lea     msg_unknown,a0
    bsr     put_str
    bra     new_cmd

; a1 - Dump address
dump_cmd:
    move.w  #(8-1),d1       ; Print 8 lines
dump_line:
    move.l  a1,d0
    bsr     bin_to_hex        ; Print address
    move.b  #':',d0
    bsr     put_chr
    move.w  #(8-1),d2       ; Print 8 cells
dump_cell:
    move.b  #' ',d0
    bsr     put_chr
    move.w  (a1)+,d0
    bsr     bin_to_hex_w      ; Print mem value
    dbra    d2,dump_cell    ; Decrement d1, branch if d1 is NOT -1

    move.b  #CR,d0
    bsr     put_chr
    move.b  #LF,d0
    bsr     put_chr
    dbra    d1,dump_line    ; Decrement d1, branch if d1 is NOT -1
    bra     new_cmd

; d1 - Value to be written
; a1 - Write address
write_cmd:
    move.w  d1,(a1)       ; Move only 16 bits (argument is 32 bit long)
    bra     new_cmd

help_cmd:
    lea     msg_help,a0
    bsr     put_str
    bra     new_cmd

; -------------------------------------------------------------------------
; Load from UART a binary content to memory.
;
; PROTOCOL: Magic- and CRC-32-protected binary (Big-Endian)
;
; HEADER (16 bytes, sent first):
; [32-bit Magic: 'RT68']
; [32-bit Load Address] (a0)
; [32-bit Content Length] (d1)
; [32-bit CRC-32/ISO-HDLC of body] (d3)
;
; BODY:
; [L bytes of raw binary content]
; -------------------------------------------------------------------------
load_cmd:
    moveq   #0,d7              ; UART load; no SPI cleanup or automatic jump.
    lea     image_uart_byte,a6
    bra     image_load

loadflash_cmd:
    moveq   #1,d7              ; Flash load only.
    bra     flash_load
boot_cmd:
    moveq   #2,d7              ; Flash load then execute ONLY after valid CRC.
flash_load:
    lea     image_flash_byte,a6
    bsr     flash_wait_ready
    tst.b   d6
    bne     image_timeout
    move.b  #$00,SPI_STATUS
    move.b  #$0e,SPI_CONFIG    ; 8-bit, clock/128; same as flash smoke test.
    move.b  #$12,SPI_STATUS
    moveq   #$05,d0            ; Read status; never interrupt an erase/program.
    bsr     flash_transfer
    tst.b   d6
    bne     image_timeout
    bsr     image_flash_byte
    tst.b   d6
    bne     image_timeout
    move.b  #$10,SPI_STATUS
    btst    #0,d0
    bne     image_flash_busy
    move.b  #$12,SPI_STATUS
    lea     flash_read_command,a0
    moveq   #4,d2
.command:
    move.b  (a0)+,d0
    bsr     flash_transfer
    tst.b   d6
    bne     image_timeout
    dbra    d2,.command

; Shared reader: a6 returns byte in d0 and error flag in d6.
; d7 = source/action, a4 = validated load/entry address.
image_load:
    lea     msg_loading,a0
    bsr     put_str

    ; Reject streams which do not use this monitor image format before
    ; interpreting any received value as an address or a length.
    jsr     read_32bit_word
    tst.b   d6
    bne     image_timeout
    cmpi.l  #IMAGE_MAGIC,d1
    bne     loa_cmd_bad_magic

    ; Read header start address (32 bits)
    jsr     read_32bit_word     ; Result in d1.L
    tst.b   d6
    bne     image_timeout
    move.l  d1,a0               ; a0 start address
    move.l  d1,a4
    ; Read content length
    jsr     read_32bit_word     ; Result in d1.L
    tst.b   d6
    bne     image_timeout
                                ; d1 content length
    move.l  d1,d5               ; Preserve length while reading CRC field.
    ; Read expected CRC-32/ISO-HDLC of the body.
    jsr     read_32bit_word
    tst.b   d6
    bne     image_timeout
    move.l  d1,d3
    move.l  d5,d1               ; Restore length for the receive loop.

    ; Images may only target application SDRAM, not vectors, monitor RAM,
    ; framebuffer, peripherals or ROM. Reject wraparound and odd entry PCs.
    move.l  a4,d0
    btst    #0,d0
    bne     image_bad_range
    cmpi.l  #$00010000,d0
    blo     image_bad_range
    tst.l   d1
    beq     image_bad_range
    add.l   d1,d0
    bcs     image_bad_range
    cmpi.l  #$00e00000,d0
    bhi     image_bad_range
    tst.b   d7
    beq     .range_ok
    cmpi.l  #FLASH_IMAGE_MAX,d1
    bhi     image_bad_range
.range_ok:

    ; CRC-32/ISO-HDLC: initial value $FFFFFFFF, reflected polynomial
    ; $EDB88320, and final XOR $FFFFFFFF.
    moveq   #-1,d4
    cmp.l   #0,d1
    beq     loa_cmd_crc_check

    ; Read content
loa_cmd_loop:
    jsr     (a6)
    tst.b   d6
    bne     image_timeout
    move.b  d0,(a0)+            ; Copy read byte to memory
    eor.b   d0,d4               ; XOR the byte into the CRC accumulator
    moveq   #7,d2
loa_cmd_crc_bit:
    lsr.l   #1,d4
    bcc     loa_cmd_crc_next_bit
    eori.l  #$EDB88320,d4
loa_cmd_crc_next_bit:
    dbra    d2,loa_cmd_crc_bit
    subq.l  #1,d1               ; Decrement the FULL 32-bit counter
                                ; (dbra replaced to support long > 64KB)
    bne     loa_cmd_loop        ; If the counter hasn't reached 0, branch back

loa_cmd_crc_check:
    not.l   d4                  ; Apply CRC-32 final XOR.
    cmp.l   d3,d4
    bne     loa_cmd_bad_crc

    lea     msg_load_done,a0
    bsr     put_str
    bsr     image_close
    cmpi.b  #2,d7
    beq     .boot
    lea     msg_loaded_at,a0
    bsr     put_str
    move.l  a4,d0
    bsr     bin_to_hex
    lea     msg_newline,a0
    bsr     put_str
    bra     new_cmd
.boot:
    jmp     (a4)

loa_cmd_bad_magic:
    lea     msg_load_bad_magic,a0
    bra     image_error

loa_cmd_bad_crc:
    lea     msg_load_bad_crc,a0
    bra     image_error
image_bad_range:
    lea     msg_load_bad_range,a0
    bra     image_error
image_timeout:
    lea     msg_spi_timeout,a0
    bra     image_error
image_flash_busy:
    lea     msg_flash_busy,a0
image_error:
    bsr     image_close
    bsr     put_str
    bra     new_cmd

image_close:
    tst.b   d7
    beq     .done
    move.b  #$00,SPI_STATUS    ; Deselect both devices, release USRMCLK.
.done:
    rts

image_uart_byte:
    bsr     get_chr
    moveq   #0,d6
    rts
image_flash_byte:
    move.b  #$ff,d0
    bra     flash_transfer

; Returns d0 = received byte, d6 = 0 success / 1 timeout.
; All loader registers other than d0/d6 are preserved.
flash_transfer:
    move.l  d2,-(sp)
    move.b  d0,SPI_DATA
    move.b  #$13,SPI_STATUS
    move.w  #$ffff,d2
.started:
    btst    #0,SPI_STATUS
    bne     .wait
    dbra    d2,.started
    moveq   #1,d6
    bra     .done
.wait:
    bsr     flash_wait_ready
    tst.b   d6
    bne     .done
    move.b  SPI_DATA,d0
.done:
    move.l  (sp)+,d2
    rts

flash_wait_ready:
    move.l  d2,-(sp)
    move.w  #$ffff,d2
.wait:
    btst    #0,SPI_STATUS
    beq     .ready
    dbra    d2,.wait
    moveq   #1,d6
    bra     .done
.ready:
    moveq   #0,d6
.done:
    move.l  (sp)+,d2
    rts

run_cmd:
    ; JUMP to the specified address
    jmp     (a1)

fbclr_cmd:
    lea     _fb_start,a0            ; Framebuffer pointer
    move.w  #(_fb_len_words-1),d1   ; Framebuffer size in words - 1 (dbra)
    move.w  #0,d0
fbclr_cmd_loop:
    move.w  d0,(a0)+            ; Clear FB
    dbra    d1,fbclr_cmd_loop   ; Decrement d1, if != -1 loop
    bra     new_cmd             ; Done

; ------------------------------------------------------------
; parse_dump: Checks for 'DUMP' and extracts address argument.
; Output
; - d0.0: 1 if 'DUMP' found and address parsed, 0 otherwise.
; - a1: If successful, contains the 32-bit starting address.
; ------------------------------------------------------------
parse_dump:
    movem.l a0,-(sp)
    lea     dump_str,a1
    lea     IN_BUF,a0

    jsr     check_cmd           ; Chek expected command
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     prs_dmp_done        ; Exit on failure

    jsr     check_sep           ; Check for separator
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     prs_dmp_done        ; Exit on failure

    bsr    hex_to_bin            ; Parse 1st argument (32 bits)
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     prs_dmp_done        ; Exit on failure
    move.l  d1,a1               ; Move the parsed address from d0 into a1

    jsr     check_trail         ; Check for trailing junk
                                ; d0.0 returned with result flag
prs_dmp_done:
    movem.l (sp)+,a0
    rts

; ------------------------------------------------------------
; parse_write: Checks for 'WRITE' and extracts address arguments.
; Output
; - d0.0: 1 if 'DUMP' found and address parsed, 0 otherwise.
; - a1: If successful, contains the 32-bit address.
; - d1: If successful, contains the 32-bit value.
; ------------------------------------------------------------
parse_write:
    movem.l a0,-(sp)
    lea     write_str,a1
    lea     IN_BUF,a0

    jsr     check_cmd           ; Chek expected command
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     prs_wrt_done        ; Exit on failure

    jsr     check_sep           ; Check for separator
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     prs_wrt_done        ; Exit on failure

    bsr     hex_to_bin           ; Parse 1st argument (32 bits)
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     prs_wrt_done        ; Exit on failure
    move.l  d1,a1               ; Move the parsed address from d0 into a1

    jsr     check_sep           ; Check for separator
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     prs_wrt_done        ; Exit on failure

    bsr     hex_to_bin          ; Parse 2st argument (32 bits), d1 contains result
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     prs_wrt_done        ; Exit on failure

    jsr     check_trail         ; Check for trailing junk
                                ; d0.0 returned with result flag
prs_wrt_done:
    movem.l (sp)+,a0
    rts

; ------------------------------------------------------------
; parse_help: Checks for 'HELP', no arguments
; Output
; - d0.0: 1 if 'HELP' found and address parsed, 0 otherwise.
; ------------------------------------------------------------
parse_help:
    movem.l a0,-(sp)
    lea     help_str,a1
    lea     IN_BUF,a0

    jsr     check_cmd           ; Chek expected command
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     prs_hlp_done        ; Exit on failure

    jsr     check_trail         ; Check for trailing junk
                                ; d0.0 returned with result flag
prs_hlp_done:
    movem.l (sp)+,a0
    rts

; ------------------------------------------------------------
; parse_load: Checks for 'LOAD', no arguments
; Output
; - d0.0: 1 if 'LOAD' found and address parsed, 0 otherwise.
; ------------------------------------------------------------
parse_load:
    movem.l a0,-(sp)
    lea     load_str,a1
    lea     IN_BUF,a0

    jsr     check_cmd           ; Chek expected command
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     prs_loa_done        ; Exit on failure

    ;jsr     check_sep           ; Check for separator
    ;btst    #0,d0               ; d0.0 equals 0, failure
    ;beq     prs_loa_done        ; Exit on failure

    jsr     check_trail         ; Check for trailing junk
                                ; d0.0 returned with result flag
prs_loa_done:
    movem.l (sp)+,a0
    rts

; a1 = exact command name; accepts no arguments (trailing spaces allowed).
parse_no_args:
    move.l  a0,-(sp)
    lea     IN_BUF,a0
    bsr     check_cmd
    btst    #0,d0
    beq     .done
    bsr     check_trail
.done:
    move.l  (sp)+,a0
    rts

; ------------------------------------------------------------
; parse_run: Checks for 'RUN' and extracts address argument.
; Output
; - d0.0: 1 if 'RUN' found and address parsed, 0 otherwise.
; - a1: If successful, contains the 32-bit starting address.
; ------------------------------------------------------------
parse_run:
    movem.l a0,-(sp)
    lea     run_str,a1
    lea     IN_BUF,a0

    jsr     check_cmd           ; Chek expected command
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     psr_run_done        ; Exit on failure

    jsr     check_sep           ; Check for separator
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     psr_run_done        ; Exit on failure

    bsr     hex_to_bin          ; Parse 1st argument (32 bits)
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     psr_run_done        ; Exit on failure
    move.l  d1,a1               ; Move the parsed address from d0 into a1

    jsr     check_trail         ; Check for trailing junk
                                ; d0.0 returned with result flag
psr_run_done:
    movem.l (sp)+,a0
    rts

; ------------------------------------------------------------
; parse_fbclr: Checks for 'FBCLR', no arguments
; Output
; - d0.0: 1 if 'FBCLR' found and address parsed, 0 otherwise.
; ------------------------------------------------------------
parse_fbclr:
    movem.l a0,-(sp)
    lea     fbclr_str,a1
    lea     IN_BUF,a0

    jsr     check_cmd           ; Chek expected command
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     psr_fbclr_done      ; Exit on failure

    jsr     check_trail         ; Check for trailing junk

psr_fbclr_done:
    movem.l (sp)+,a0
    rts


; ------------------------------------------------------------
; check_cmd
; Input
; - a0: Points to the buffer.
; - a1: Points to the start of the command (NULL terminated)
;       to be compared to (e.g. dump_str).
; Output
; - d0.0: 1 if command found, 0 otherwise.
; - a0: Points to character in the buffer after the command.
; ------------------------------------------------------------
check_cmd:
    movem.l d1/d2/d3/a1,-(sp)
    move.l #1,d0

chk_cmd_loop:
    move.b  (a1)+,d3
    cmp.b   #NUL,d3
    beq     chk_cmd_done
    move.b  (a0)+,d2
    cmp.b   d3,d2
    bne     chk_cmd_fail
    bra     chk_cmd_loop

chk_cmd_fail:
    clr.l   d0

chk_cmd_done:
    movem.l (sp)+,d1/d2/d3/a1
    rts

; ------------------------------------------------------------
; check_sep
; Check for separator and skip whitespace to find argument.
; Input
; - a0: Points to character in the buffer after the command.
; Output
; - d0.0: 1 if separator found, 0 otherwise.
; - a0: Points to character in the buffer after the argument.
; ------------------------------------------------------------
check_sep:
    movem.l d2,-(sp)
    move.l #1,d0

    ; Check for separator (Space)
    move.b  (a0),d2             ; Peek at the next character
    cmp.b   #' ',d2             ; Must be a space
    bne     chk_sep_fail        ; Fail if not space

    ; Skip whitespace to find argument
chk_sep_skip_ws:
    cmp.b   #' ',(a0)+          ; Check for space, and advance a0
    beq     chk_sep_skip_ws     ; Loop while space
    subq.l  #1,a0               ; a0 advanced one too far, backtrack
    bra     chk_sep_done

chk_sep_fail:
    clr.l   d0

chk_sep_done:
    movem.l (sp)+,d2
    rts

; ------------------------------------------------------------
; check_trail
; Check for trailing junk (should be called after all arguments).
; Input
; - a0: Points to character in the buffer after the command.
; Output
; - d0.0: 1 if string clean, 0 otherwise.
; ------------------------------------------------------------
check_trail:
    movem.l d2,-(sp)
    move.l #1,d0

chk_trl_loop:
    move.b  (a0)+,d2            ; Peek at the character
    tst.b   d2                  ; Is it NULL?
    beq     chk_trl_done        ; End of line, SUCCESS
    cmp.b   #' ',d2             ; Is it a space?
    bne     chk_trl_fail        ; If it's *anything else* (like 'X' in 'C000X'), it's junk.
    bra     chk_trl_loop        ; continue until end of line

chk_trl_fail:
    clr.l   d0

chk_trl_done:
    movem.l (sp)+,d2
    rts

; ------------------------------
; TRAP handlers
; ------------------------------
; 02 - Bus Error
int_bs_handler:
    lea     msg_bus_err,a0
    bsr     put_str
    bsr     print_regs
    move.l  #_stack_top,sp
    bsr     clear_registers
    jmp     mon_entry

trap_14_handler:
    move.l  #_stack_top,sp
    bsr     clear_registers
    jmp     mon_entry

clear_registers:
    moveq   #0,d0
    moveq   #0,d1
    moveq   #0,d2
    moveq   #0,d3
    moveq   #0,d4
    moveq   #0,d5
    moveq   #0,d6
    moveq   #0,d7
    move.l  #0,a0
    move.l  #0,a1
    move.l  #0,a2
    move.l  #0,a3
    move.l  #0,a4
    move.l  #0,a5
    move.l  #0,a6
    rts

; ------------------------------
; Print registers
; ------------------------------
print_regs:
    movem.l d0/a0/a1,-(sp)    ; Save d0, a0, AND a1 to the stack

    lea     msg_d0,a0
    bsr     print_reg         ; Print D0 content

    lea     msg_d1,a0
    move.l  d1,d0
    bsr     print_reg

    lea     msg_d2,a0
    move.l  d2,d0
    bsr     print_reg

    lea     msg_d3,a0
    move.l  d3,d0
    bsr     print_reg

    lea     msg_d4,a0
    move.l  d4,d0
    bsr     print_reg

    lea     msg_d5,a0
    move.l  d5,d0
    bsr     print_reg

    lea     msg_d6,a0
    move.l  d6,d0
    bsr     print_reg

    lea     msg_d7,a0
    move.l  d7,d0
    bsr     print_reg

    lea     msg_a0,a0         ; Load A0 label
    move.l  4(sp),d0          ; Peek at A0 from the saved block on stack
    bsr     print_reg

    lea     msg_a1,a0
    move.l  a1,d0
    bsr     print_reg

    lea     msg_a2,a0
    move.l  a2,d0
    bsr     print_reg

    lea     msg_a3,a0
    move.l  a3,d0
    bsr     print_reg

    lea     msg_a4,a0
    move.l  a4,d0
    bsr     print_reg

    lea     msg_a5,a0
    move.l  a5,d0
    bsr     print_reg

    lea     msg_a6,a0
    move.l  a6,d0
    bsr     print_reg

    lea     msg_a7,a0
    move.l  a7,d0
    bsr     print_reg

    move.b  #CR,d0
    bsr     put_chr
    move.b  #LF,d0
    bsr     put_chr

    movem.l (sp)+,d0/a0/a1    ; Restore all three registers
    rts

; Call this by putting the message address in A0
; and the value to print in D0
print_reg:
    bsr     put_str           ; Print the label
    bsr     bin_to_hex        ; Call your established hex converter
    rts

; ------------------------------
; Libraries
; ------------------------------
    include '../../lib/asm/mem_map_video.asm'
    include '../../lib/asm/console_io_uart.asm'
    include '../../lib/asm/conv_hex.asm'
    include '../../lib/asm/isr_vector.asm'


; -------------------------------------------------------------
; read_32bit_word: Reads 4 bytes from the selected source into d1.L
; Input: None
; Output: d1.L = 32-bit value
; Output: d6 = source error flag; stops immediately on a source error.
; -------------------------------------------------------------
read_32bit_word:
    movem.l d0/d2,-(sp)     ; Save d0 (used for get_chr) and d2 (used for loop counter)

    moveq   #4-1,d2         ; d2 = 3 (loop 4 times for 4 bytes)
    clr.l   d1              ; d1 = Accumulator (cleared for the 32-bit result)

read_loop:
    jsr     (a6)
    tst.b   d6
    bne     read_done

    ; 1. Shift the current result (d1) left by 8 bits (makes room for the new byte)
    lsl.l   #8,d1

    ; 2. OR the new byte (d0.B) into the least significant position of d1
    or.b    d0,d1

    dbra    d2,read_loop    ; Loop 4 times total (d2 counts down from 3)

read_done:
    movem.l (sp)+,d0/d2      ; Restore registers
    rts


init_vector_table:
    move.l  #int_bs_handler,VT_INT_BE
    move.l  #trap_14_handler,VT_TRAP_14
    rts

init_palette:
    lea     VIDEO_PLTE,a0       ; Point to start of palette memory ($10000)
    lea     default_colors,a1   ; Point to our ROM data table
    move.w  #15,d0              ; 16 colors to process (-1 for dbra)
.loop:
    move.l  (a1)+,(a0)+         ; Copy 32-bit color data to palette register
    dbra    d0,.loop            ; Loop until all 16 are copied
    rts

; ------------------------------
; ROM Data Section
; ------------------------------
; Palette Data Table
; Formatted as 32-bit Longwords: 0x00RRGGBB
default_colors
    dc.l    $00000000           ; 0: Black
    dc.l    $000000AA           ; 1: Blue
    dc.l    $0000AA00           ; 2: Green
    dc.l    $0000AAAA           ; 3: Cyan
    dc.l    $00AA0000           ; 4: Red
    dc.l    $00AA00AA           ; 5: Magenta
    dc.l    $00AA5500           ; 6: Brown
    dc.l    $00AAAAAA           ; 7: Light Gray
    dc.l    $00555555           ; 8: Dark Gray
    dc.l    $005555FF           ; 9: Bright Blue
    dc.l    $0055FF55           ; 10: Bright Green
    dc.l    $0055FFFF           ; 11: Bright Cyan
    dc.l    $00FF5555           ; 12: Bright Red
    dc.l    $00FF55FF           ; 13: Bright Magenta
    dc.l    $00FFFF55           ; 14: Yellow
    dc.l    $00FFFFFF           ; 15: White

; Messages
msg_title       dc.b    'RT68F Monitor v0.1',CR,LF,NUL
msg_unknown     dc.b    'Error: Unknown command or syntax',CR,LF,NUL
msg_help        dc.b    'dump  <ADDR>       - Dump from ADDR (HEX)',CR,LF
                dc.b    'write <ADDR> <VAL> - Write to ADDR (HEX) the VALUE (HEX)',CR,LF
                dc.b    'load               - Load from UART to memory',CR,LF
                dc.b    'loadflash          - Load/verify flash image at $100000',CR,LF
                dc.b    'boot               - Load/verify flash image and run',CR,LF
                dc.b    'run   <ADDR>       - Run program at ADDR (HEX)',CR,LF
                dc.b    'fbclr              - Clear framebuffer',CR,LF
                dc.b    'help               - Print this list of commands',CR,LF
                dc.b    NUL
msg_loading     dc.b    'Loading...',CR,LF,NUL
msg_load_done   dc.b    'Done.',CR,LF,NUL
msg_load_bad_magic dc.b 'Error: Invalid image magic.',CR,LF,NUL
msg_load_bad_crc dc.b   'Error: CRC mismatch.',CR,LF,NUL
msg_load_bad_range dc.b 'Error: Invalid image address or length.',CR,LF,NUL
msg_spi_timeout dc.b 'Error: SPI timeout.',CR,LF,NUL
msg_flash_busy dc.b 'Error: Flash busy or not responding.',CR,LF,NUL
msg_loaded_at dc.b 'Loaded at ',NUL
msg_newline dc.b CR,LF,NUL
msg_bus_err     dc.b    'Bus Error!',CR,LF,NUL

; Registers names
msg_d0          dc.b    'd0: ',NUL
msg_d1          dc.b    '  d1: ',NUL
msg_d2          dc.b    '  d2: ',NUL
msg_d3          dc.b    '  d3: ',NUL
msg_d4          dc.b    CR,LF,'d4: ',NUL
msg_d5          dc.b    '  d5: ',NUL
msg_d6          dc.b    '  d6: ',NUL
msg_d7          dc.b    '  d7: ',NUL
msg_a0          dc.b    CR,LF,'a0: ',NUL
msg_a1          dc.b    '  a1: ',NUL
msg_a2          dc.b    '  a2: ',NUL
msg_a3          dc.b    '  a3: ',NUL
msg_a4          dc.b    CR,LF,'a4: ',NUL
msg_a5          dc.b    '  a5: ',NUL
msg_a6          dc.b    '  a6: ',NUL
msg_a7          dc.b    '  a7: ',NUL

; Commands
; They must be null terminated
dump_str        dc.b    'dump',NUL
write_str       dc.b    'write',NUL
help_str        dc.b    'help',NUL
load_str        dc.b    'load',NUL
loadflash_str   dc.b    'loadflash',NUL
boot_str        dc.b    'boot',NUL
run_str         dc.b    'run',NUL,NUL
fbclr_str       dc.b    'fbclr',NUL
    even
flash_read_command dc.b $13
    dc.b (FLASH_IMAGE_OFFSET>>24)&$ff,(FLASH_IMAGE_OFFSET>>16)&$ff
    dc.b (FLASH_IMAGE_OFFSET>>8)&$ff,FLASH_IMAGE_OFFSET&$ff
    even

; ===========================
; RAM Data Section (bootloader mem)
; ===========================
    section .bss
IN_BUF:
    ds.b    80
IN_BUF_END:

; ===========================
; Constants
; ===========================
; Program Constants
DLY_VAL         equ 1333333     ; Delay iterations, 1.33 million = 0.5 sec at 32MHz

; I2cDevice: word accesses only; payloads occupy the low bits.
I2C_CONTROL     equ $02128000       ; W: controls, R: status
I2C_ADDRESS     equ $02128002       ; 7-bit slave address
I2C_DATA        equ $02128004       ; W: TX byte, R: RX byte
I2C_READ        equ $0001
I2C_LAST        equ $0002
I2C_TRIGGER     equ $0004
I2C_RESTART     equ $0008
I2C_BUSY_BIT    equ 0
I2C_NACK_BIT    equ 1
DS3231_ADDRESS  equ $68
I2C_POLL_LIMIT  equ 100000          ; Bounded CPU polling, not a timed delay

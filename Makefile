# Hardware settings
TARGET = rt68ice
TOP = Rt68IceTopLevel
SCALA_PACKAGE = rt68ice
VERILOG_SOURCES = hw/gen/$(TOP).v
MERGED_VHDL = hw/gen/mergeRTL.vhd
MERGED_VERILOG = hw/gen/mergeRTL.v
#WAVE_FILE = simWorkspace/Blink/test/wave.fst
WAVE_FILE = simWorkspace/VgaDevice/test/wave.vcd
# ECP5 Specifics
DEVICE  = --25k
PACKAGE = --package CABGA256
LPF     = hw/constraints/$(TOP).lpf

# Software settings
# Firmware (FW)
ASM_SRC_DIR = sw/fw/asm
BIN_GEN_DIR = hw/gen
HEX_SPINAL_DIR = hw/spinal/rt68ice/memory
HEX_CLASS_DIR = target/scala-2.13/classes/rt68ice/memory
# Default linker script for firmware (fw) programs
LD_SCRIPT = $(ASM_SRC_DIR)/fw.ld
# Applications (App)
ASM_APP_DIR := sw/app/asm
LD_SCRIPT_APP = $(ASM_APP_DIR)/app.ld
TARGET_APP_DIR := target/app
# Where the board is connected
SERIAL_PORT = /dev/ttyACM0
SERIAL_BAUD = 19200


.PHONY: all clean rom prog prog-flash view-wave monitor

all: $(TARGET).bit

# 1. Generate Verilog from SpinalHDL (The bridge)
spinal: $(VERILOG_SOURCES) $(MERGED_VHDL)
$(VERILOG_SOURCES) $(MERGED_VHDL): hw/spinal/rt68ice/*.scala rom apps
	sbt "runMain $(SCALA_PACKAGE).$(TOP)Verilog"

# 2. Synthesis
$(TARGET).json: $(VERILOG_TOP) $(MERGED_VHDL)
	#yosys -p "synth_ecp5 -json $@" $(VERILOG_SOURCES)
	yosys -m ghdl -p "ghdl -C --std=08 -C -fsynopsys -C --latches $(MERGED_VHDL) -e TG68KdotC_Kernel; \
		ghdl -C --std=08 -C -fsynopsys -C --latches $(MERGED_VHDL) -e T16450; \
		read_verilog $(VERILOG_SOURCES) $(MERGED_VERILOG); \
		synth_ecp5 -top $(TOP) -json $@"

# 3. Place & Route (Using the LPF file!)
$(TARGET).config: $(TARGET).json
	nextpnr-ecp5 $(DEVICE) $(PACKAGE) --speed 6 --json $< --textcfg $@ --lpf $(LPF) --freq 30

# 4. Bitstream
$(TARGET).bit: $(TARGET).config
	ecppack $< $@

# 5. Load to FPGA
prog: # $(TARGET).bit
	#openFPGALoader -c cmsisdap --vid=0x1d50 --pid=0x602b $<
	openFPGALoader -c cmsisdap --vid=0x1d50 --pid=0x602b $(TARGET).bit

# 5. Load to FLASH (permanent)
prog-flash: $(TARGET).bit
	openFPGALoader -f -c cmsisdap --vid=0x1d50 --pid=0x602b $<

serial-open:
	picocom -b $(SERIAL_BAUD) $(SERIAL_PORT)

reset:
	openFPGALoader -c cmsisdap --vid=0x1d50 --pid=0x602b --reset

view-wave: $(WAVE_FILE)
	@if [ -f $(WAVE_FILE) ]; then \
		gtkwave $(WAVE_FILE) & \
	else \
		echo "Error: Waveform file not found. Run simulation first."; \
	fi

serial-load: apps
	@if [ -z "$(BIN)" ]; then \
		echo "Error: Please specify BIN (e.g., make serial-load BIN=blink.bin)"; \
		echo "Valid BIN files:"; \
		find target/app/ -maxdepth 1 -name "*.bin" ! -name "*_raw.bin" | xargs -n 1 basename; \
		exit 1; \
	fi
	# Test file existence
	test -f $(TARGET_APP_DIR)/$(BIN)
	# Send `load` command to prepare the device
	@echo "--- Loading $(BIN) to $(SERIAL_PORT) ---"
	printf "load\r" > $(SERIAL_PORT)
	sleep 0.5
	# Transfer the contents of the chosen binary file
	cat $(TARGET_APP_DIR)/$(BIN) > $(SERIAL_PORT)
	sleep 0.5
	# Send RUN command
	@PROGRAM_ADDRESS=$$(hexdump -vn 4 -e '4/1 "%02X"' $(TARGET_APP_DIR)/$(BIN)); \
	echo "--- Running application at 0x$$PROGRAM_ADDRESS ---"; \
	printf "run $$PROGRAM_ADDRESS\r" > $(SERIAL_PORT)

clean:
	rm -rf *.json *.config *.bit target hw/spinal/rt68ice/memory/*.hex
	rm -rf hw/gen/*.v hw/gen/*.vhd hw/gen/*.o hw/gen/*.bin hw/gen/*.sym hw/spinal/rt68ice/memory/*.hex


# =========================================================================
# SW PIPELINE 1: Firmware / ROM Initializers (.asm -> .hex)
# =========================================================================
ASM_SOURCES := $(wildcard $(ASM_SRC_DIR)/*.asm)
# Convert 'sw/fw/asm/filename.asm' targets into 'target/scala-2.13/classes/rt68ice/memory/filename.hex'
ROM_HEX_FILES := $(patsubst $(ASM_SRC_DIR)/%.asm,$(HEX_CLASS_DIR)/%.hex,$(ASM_SOURCES))

rom: $(ROM_HEX_FILES)

# When building the monitor hex file, temporarily replace the generic linker script
$(HEX_CLASS_DIR)/monitor.hex: LD_SCRIPT = $(ASM_SRC_DIR)/monitor.ld

$(HEX_CLASS_DIR)/%.hex: $(ASM_SRC_DIR)/%.asm
	@echo "----------------------------------------------"
	@echo "- Assembling and Converting '$*'"
	@echo "----------------------------------------------"
	# Assemble the 68000 code to an ELF object file
	vasmm68k_mot -Felf $< -o $(BIN_GEN_DIR)/$*.o
	# Link object file
	vlink -T $(LD_SCRIPT) -b rawbin1 -M$(BIN_GEN_DIR)/$*.sym -o $(BIN_GEN_DIR)/$*.bin $(BIN_GEN_DIR)/$*.o
	# Convert binary to a two-byte-per-line hex file, convert to uppercase
	xxd -p -c 2 $(BIN_GEN_DIR)/$*.bin | awk '{print toupper($$0)}' > $(HEX_SPINAL_DIR)/$*.hex
	# Ensure the destination directory exists
	mkdir -p $(HEX_CLASS_DIR)
	# Copy the hex file to the Scala classes path for resource loading
	cp $(HEX_SPINAL_DIR)/$*.hex $@

# =========================================================================
# SW PIPELINE 2: User Apps via UART (.asm -> Custom Headered .bin)
# =========================================================================
# Define the list of assembly files (e.g., if you have blinker.asm and main.asm)
ASM_APP_SOURCES := $(wildcard $(ASM_APP_DIR)/*.asm)
# Target: Create the final .bin files from the list of sources
BIN_APP_TARGETS := $(patsubst $(ASM_APP_DIR)/%.asm, $(TARGET_APP_DIR)/%.bin, $(ASM_APP_SOURCES))

apps: $(BIN_APP_TARGETS)

# We use target-specific assignment (= or :=) so $* is evaluated inside the rule context
$(TARGET_APP_DIR)/%.bin: RAW_FILE_NAME = $(TARGET_APP_DIR)/$*_raw.bin
$(TARGET_APP_DIR)/%.bin: $(ASM_APP_DIR)/%.asm
	@mkdir -p $(TARGET_APP_DIR)
	# Assemble to a raw binary image (*_raw.bin)
	vasmm68k_mot -Felf $< -o $(TARGET_APP_DIR)/$*.o
	# Link object file
	vlink -T $(LD_SCRIPT_APP) -b rawbin1 -M$(TARGET_APP_DIR)/$*.sym -o $(RAW_FILE_NAME) $(TARGET_APP_DIR)/$*.o
	# Calculate length and prepend the header. All steps in ONE shell session.
	SHELL_RAW_FILE="$(RAW_FILE_NAME)"; \
	SYM_FILE="$(TARGET_APP_DIR)/$*.sym"; \
	FILE_SIZE=$$(stat -c %s $$SHELL_RAW_FILE); \
	HEX_SIZE=$$(printf "%08X" "$$FILE_SIZE"); \
	DETECTED_ADDR=$$(awk '/^[[:space:]]*[0-9a-fA-F]{8}[[:space:]]+\.text/ {print $$1; exit}' "$$SYM_FILE"); \
	HEADER_HEX=$$DETECTED_ADDR$$HEX_SIZE; \
	echo "$$HEADER_HEX" | xxd -r -p | cat - $$SHELL_RAW_FILE > $@

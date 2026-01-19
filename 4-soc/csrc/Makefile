CROSS_COMPILE ?= $(HOME)/rv/toolchain/bin/riscv-none-elf-

ASFLAGS = -march=rv32i_zicsr -mabi=ilp32
# Optimization required: -O2 produces large stack frames that overflow in deep call chains
# RV32I lacks hardware multiply/divide, so -O2 significantly reduces code size
CFLAGS = -O2 -Wall -fno-builtin -march=rv32i_zicsr -mabi=ilp32
LDFLAGS = --oformat=elf32-littleriscv

AS := $(CROSS_COMPILE)as
CC := $(CROSS_COMPILE)gcc
LD := $(CROSS_COMPILE)ld
OBJCOPY := $(CROSS_COMPILE)objcopy
OBJDUMP := $(CROSS_COMPILE)objdump

# Benchmark parameters
FIB_N ?= 20
SIZE ?= 10

# Program targets (add new programs here)
PROGRAMS := nyancat uart shell fibonacci bubblesort dijkstra
BINARIES := $(PROGRAMS:%=%.asmbin)
DUMPS := $(PROGRAMS:%=%.dump)

%.asmbin: %.elf
	$(OBJCOPY) -O binary -j .text -j .data $< $@

%.dump: %.elf
	$(OBJDUMP) -D $< > $@

.DEFAULT_GOAL := all

all: $(BINARIES)

dump: $(DUMPS)

# All programs use proper init.S with ABI compliance and .bss clearing
# All programs use proper init.S with ABI compliance and .bss clearing
nyancat.elf: nyancat.c nyancat-data.h init.o link.lds
	$(CC) $(CFLAGS) -DNYANCAT_COMPRESSION_DELTA=$(NYANCAT_COMPRESSION_DELTA) -c -o nyancat.o nyancat.c
	$(CROSS_COMPILE)ld -o nyancat.elf -T link.lds $(LDFLAGS) nyancat.o init.o

uart.elf: uart.c mmio.h init.o link.lds
	$(CC) $(CFLAGS) -c -o uart.o uart.c
	$(CROSS_COMPILE)ld -o uart.elf -T link.lds $(LDFLAGS) uart.o init.o

shell.elf: shell.c mmio.h init.o link.lds
	$(CC) $(CFLAGS) -c -o shell.o shell.c
	$(CROSS_COMPILE)ld -o shell.elf -T link.lds $(LDFLAGS) shell.o init.o

fibonacci.elf: fibonacci.c mmio.h init.o link.lds
	$(CC) $(CFLAGS) -DFIB_N=$(FIB_N) -c -o fibonacci.o fibonacci.c
	$(CROSS_COMPILE)ld -o fibonacci.elf -T link.lds $(LDFLAGS) fibonacci.o init.o

dijkstra.elf: dijkstra.c mmio.h init.o link.lds
	$(CC) $(CFLAGS) -c -o dijkstra.o dijkstra.c
	$(CROSS_COMPILE)ld -o dijkstra.elf -T link.lds $(LDFLAGS) dijkstra.o init.o

bubblesort_data.h: bubblesort.dat
	python3 -c 'import sys; print("int data[] = {" + ",".join(sys.stdin.read().split()) + "};")' < $< > $@

bubblesort.o: bubblesort.c bubblesort_data.h
	$(CC) $(CFLAGS) -DSIZE=$(SIZE) -c -o bubblesort.o bubblesort.c

bubblesort.elf: bubblesort.o mmio.h init.o link.lds
	$(CROSS_COMPILE)ld -o bubblesort.elf -T link.lds $(LDFLAGS) bubblesort.o init.o

init.o: init.S
	$(AS) -R $(ASFLAGS) -o $@ $<

# Generate nyancat animation data from upstream source
# Configure compression mode via NYANCAT_COMPRESSION_DELTA (default: 1 for delta-RLE)
# - NYANCAT_COMPRESSION_DELTA=1: delta-RLE compression (91% reduction, 4.7KB)
# - NYANCAT_COMPRESSION_DELTA=0: baseline RLE (87% reduction, 6.7KB)
NYANCAT_COMPRESSION_DELTA ?= 1

nyancat-data.h: ../../scripts/gen-nyancat-data.py
ifeq ($(NYANCAT_COMPRESSION_DELTA),1)
	python3 $< --delta --output $@
else
	python3 $< --output $@
endif

# Nyancat build depends on generated data
nyancat.o: nyancat.c nyancat-data.h

update: $(BINARIES)
	cp -f $(BINARIES) ../src/main/resources
	@echo ""
	@echo "Updated $(BINARIES) to ../src/main/resources"

clean:
	$(RM) *.o *.elf *.dump *.asmbin init_minimal.S nyancat-data.h

# Convenience targets (prevent implicit rule interference)
$(PROGRAMS): %: %.asmbin

.PHONY: all update clean $(PROGRAMS)

# SPDX-License-Identifier: MIT
# MyCPU is freely redistributable under the MIT License. See the file
# "LICENSE" for information on usage and redistribution of this file.

# Include common build utilities
include ../common/build.mk

# Branch predictor model selection
# Usage: make sim MODEL=gshare|twolevel|per-t|per-b|btb|none
# Default: none (always predict not taken)
MODEL ?= none
FIB_N ?= 20
SIZE ?= 10
SIM_TIME ?= 1000000

ifeq ($(MODEL),per-t)
	SBT_ARGS := -DusePerceptronT=true -DusePerceptronB=false -DuseGShare=false -DuseTwoLevel=false
else ifeq ($(MODEL),per-b)
	SBT_ARGS := -DusePerceptronB=true -DusePerceptronT=false -DuseGShare=false -DuseTwoLevel=false
else ifeq ($(MODEL),twolevel)
	SBT_ARGS := -DuseTwoLevel=true -DuseGShare=false -DusePerceptronT=false -DusePerceptronB=false
else ifeq ($(MODEL),gshare)
	SBT_ARGS := -DuseGShare=true -DuseTwoLevel=false -DusePerceptronT=false -DusePerceptronB=false
else ifeq ($(MODEL),btb)
	SBT_ARGS := -DuseGShare=false -DuseTwoLevel=false -DusePerceptronT=false -DusePerceptronB=false -DuseNone=false
else ifeq ($(MODEL),none)
	SBT_ARGS := -DuseNone=true -DuseGShare=false -DuseTwoLevel=false -DusePerceptronT=false -DusePerceptronB=false
else
	$(error Unknown MODEL=$(MODEL). Valid options: gshare, twolevel, per-t, per-b, btb, none)
endif

test:
	cd .. && sbt $(SBT_ARGS) "project soc" test

verilator:
	@if java -version >/dev/null 2>&1; then \
		cd .. && PATH=$$HOME/.local/bin:$$PATH sbt $(SBT_ARGS) "project soc" "runMain board.verilator.VerilogGenerator"; \
	else \
		echo "⚠️  Java runtime not found; using existing generated Verilog in verilog/verilator"; \
		if [ ! -f verilog/verilator/Top.v ]; then \
			echo "❌ Top.v missing; install Java (set JAVA_HOME) to regenerate Verilog"; \
			exit 1; \
		fi; \
	fi
	cd verilog/verilator && verilator --trace --exe --cc sim.cpp Top.v \
		-CFLAGS "$$(sdl2-config --cflags)" \
		-LDFLAGS "$$(sdl2-config --libs)" && \
		make -C obj_dir -f VTop.mk

sim: verilator
	cd verilog/verilator/obj_dir && ./VTop $(if $(SIM_VCD),-vcd ../../../$(SIM_VCD)) -time $(SIM_TIME) $(subst src/main/resources/,../../../src/main/resources/,$(SIM_ARGS))


sim_bub:
	@$(MAKE) -C csrc clean
	@$(MAKE) -C csrc bubblesort.asmbin SIZE=$(SIZE)
	make sim SIM_ARGS="-instruction ../../../csrc/bubblesort.asmbin" SIM_VCD=$(SIM_VCD) SIM_TIME=$(SIM_TIME)

sim_dijkstra:
	@$(MAKE) -C csrc clean
	@$(MAKE) -C csrc dijkstra.asmbin
	make sim SIM_ARGS="-instruction ../../../csrc/dijkstra.asmbin" SIM_VCD=$(SIM_VCD) SIM_TIME=$(SIM_TIME)

sim_shellsort:
	@$(MAKE) -C csrc clean
	@$(MAKE) -C csrc shellsort.asmbin SIZE=$(SIZE)
	make sim SIM_ARGS="-instruction ../../../csrc/shellsort.asmbin" SIM_VCD=$(SIM_VCD) SIM_TIME=$(SIM_TIME)

sim_parser:
	@$(MAKE) -C csrc clean
	@$(MAKE) -C csrc parser.asmbin
	make sim SIM_ARGS="-instruction ../../../csrc/parser.asmbin" SIM_VCD=$(SIM_VCD) SIM_TIME=$(SIM_TIME)

sim_chess:
	@$(MAKE) -C csrc clean
	@$(MAKE) -C csrc chess.asmbin
	make sim SIM_ARGS="-instruction ../../../csrc/chess.asmbin" SIM_VCD=$(SIM_VCD) SIM_TIME=$(SIM_TIME)

sim_pattern:
	@$(MAKE) -C csrc clean
	@$(MAKE) -C csrc pattern.asmbin
	make sim SIM_ARGS="-instruction ../../../csrc/pattern.asmbin" SIM_VCD=$(SIM_VCD) SIM_TIME=$(SIM_TIME)

sim_branchstress:
	@$(MAKE) -C csrc clean
	@$(MAKE) -C csrc branchstress.asmbin
	make sim SIM_ARGS="-instruction ../../../csrc/branchstress.asmbin" SIM_VCD=$(SIM_VCD) SIM_TIME=$(SIM_TIME)

check-vga: verilator
	@echo "🔄 Building nyancat binary..."
	@$(MAKE) -C csrc nyancat.asmbin >/dev/null
	@echo "🖥️  Running VGA test (nyancat)..."
	@echo "   - Press ESC or close window to exit"
	@echo "   - VGA output: 640×480 @ 72Hz"
	@echo ""
	cd verilog/verilator/obj_dir && ./VTop -i ../../../csrc/nyancat.asmbin
	@echo ""
	@echo "✅ VGA test complete!"

shell: verilator
	@echo "🔄 Building MyCPU shell binary..."
	@$(MAKE) -C csrc shell.asmbin >/dev/null
	@echo "🐚 Starting MyCPU interactive shell..."
	@echo "   - Type 'help' for available commands"
	@echo "   - Line editing: arrows, Home/End, Ctrl-A/E/U/K/W"
	@echo "   - Press Ctrl-C to exit"
	@echo ""
	cd verilog/verilator/obj_dir && ./VTop -i ../../../csrc/shell.asmbin --terminal --headless

# UART tests: loopback self-test + interactive echo test
check-uart: verilator
	@echo "🔄 Building UART binaries..."
	@$(MAKE) -C csrc uart.asmbin shell.asmbin >/dev/null
	@echo ""
	@echo "📡 [1/2] Running UART loopback test..."
	cd verilog/verilator/obj_dir && ./VTop -i ../../../csrc/uart.asmbin
	@echo ""
	@echo "📡 [2/2] Running UART echo test (may take ~30 seconds)..."
	@cd verilog/verilator/obj_dir && \
		printf "Test\r" | timeout 30 ./VTop -i ../../../csrc/shell.asmbin --terminal --headless 2>&1 | \
		tee /tmp/uart_test_output.txt | tail -5
	@echo ""
	@if grep -q "Test" /tmp/uart_test_output.txt; then \
		echo "✅ UART tests PASSED"; \
	else \
		echo "❌ UART echo test FAILED - 'Test' was not found in output"; \
		exit 1; \
	fi

indent:
	find . -name '*.scala' | xargs scalafmt
	clang-format -i verilog/verilator/*.cpp verilog/verilator/*.h
	clang-format -i csrc/*.[ch]

compliance: check-riscof
	@echo "Running RISCOF compliance tests for 4-soc (RV32I + Zicsr)..."
	@cd ../tests && RISCOF_WORK=riscof_work_4soc ./run-compliance.sh 4-soc
	@echo ""
	@echo "Copying results to results/ directory..."
	@$(RM) -r results
	@mkdir -p results
	@cp -r ../tests/riscof_work_4soc/* results/
	@echo "Cleaning up auto-generated RISCOF test files..."
	@$(RM) -f src/test/scala/riscv/compliance/ComplianceTest.scala
	@$(RM) -f src/main/resources/test.asmbin
	@echo "✅ Compliance tests complete. Results in results/"
	@echo "📊 View report: results/report.html"

clean:
	cd .. && sbt "project soc" clean
	$(MAKE) -C csrc clean
	$(RM) -r test_run_dir
	$(RM) -r verilog/verilator/obj_dir
	$(RM) verilog/verilator/*.v
	$(RM) verilog/verilator/*.fir
	$(RM) verilog/verilator/*.anno.json

distclean: clean
	$(RM) -r results

.PHONY: verilator test indent sim check-vga check-uart shell compliance clean distclean

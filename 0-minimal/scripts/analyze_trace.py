#!/usr/bin/env python3
"""
VCD Trace Analyzer for 0-minimal RISC-V CPU

Analyzes VCD (Value Change Dump) files to verify the execution of
self-modifying code in the minimal RISC-V CPU design. This script
is designed to work with Python's standard library and has no external
dependencies.

Usage:
    python3 scripts/analyze_trace.py <vcd_file>

Author: MyCPU Project
License: MIT
"""

import argparse
import sys
from collections import defaultdict
from typing import Dict, DefaultDict

# Configuration

# Expected memory addresses from the jit.S assembly file.
JIT_CODE_BUFFER: int = 0x102c
JIT_INSTRUCTIONS: int = 0x1034
ENTRY_POINT: int = 0x1000

# VCD signal names to be analyzed.
PC_SIGNAL: str = 'io_instruction_address'
MEM_WRITE_ENABLE_SIGNAL: str = 'io_memory_bundle_write_enable'
REG_WRITE_ADDR_SIGNAL: str = 'regs_io_write_address'
REG_WRITE_DATA_SIGNAL: str = 'regs_io_write_data'

# Analysis constants
A0_REGISTER_INDEX: int = 10
MIN_CYCLES_IN_JIT_BUFFER: int = 10000
JIT_BUFFER_END: int = JIT_CODE_BUFFER + 0x10

# VCD Analyzer Class

class VCDAnalyzer:
    """Parses and analyzes VCD files to extract simulation statistics."""

    def __init__(self, vcd_file: str):
        self.vcd_file: str = vcd_file
        self.signals: Dict[str, str] = {}  # Maps symbol to signal name
        # Track signals of interest
        self.target_signals = {PC_SIGNAL, MEM_WRITE_ENABLE_SIGNAL,
                              REG_WRITE_ADDR_SIGNAL, REG_WRITE_DATA_SIGNAL}
        self.signal_symbols: Dict[str, str] = {}  # Maps signal name to symbol

    @staticmethod
    def binary_to_int(binary_str: str) -> int:
        """Converts a binary string to an integer, handling 'x' as 0."""
        if 'x' in binary_str:
            return 0
        return int(binary_str, 2)

    def parse_and_analyze(self) -> DefaultDict[str, int or bool]:
        """Parses VCD and analyzes in single pass for performance."""
        stats: DefaultDict[str, int or bool] = defaultdict(int)
        stats['a0_value_42'] = False

        try:
            with open(self.vcd_file, 'r') as f:
                in_definitions = True

                for line in f:
                    line = line.strip()
                    if not line:
                        continue

                    if in_definitions:
                        if line.startswith('$var'):
                            parts = line.split()
                            if len(parts) >= 5:
                                signal_name = parts[4]
                                symbol = parts[3]
                                if signal_name in self.target_signals:
                                    self.signals[symbol] = signal_name
                                    self.signal_symbols[signal_name] = symbol
                        elif line.startswith('$enddefinitions'):
                            in_definitions = False
                    else:
                        # Skip timestamp lines
                        if line.startswith('#'):
                            continue

                        # Parse binary value changes
                        if line.startswith('b'):
                            # Format: b[value] [symbol]
                            space_idx = line.find(' ')
                            if space_idx > 0:
                                value = line[1:space_idx]
                                symbol = line[space_idx+1:]
                                signal = self.signals.get(symbol)

                                if signal == PC_SIGNAL:
                                    pc = self.binary_to_int(value)
                                    if pc > 0:
                                        stats['pc_samples'] += 1
                                        if pc > stats['max_pc']:
                                            stats['max_pc'] = pc
                                        if JIT_CODE_BUFFER <= pc < JIT_BUFFER_END:
                                            stats['pc_at_buffer'] += 1

                                elif signal == REG_WRITE_ADDR_SIGNAL:
                                    stats['register_writes'] += 1
                                    if self.binary_to_int(value) == A0_REGISTER_INDEX:
                                        stats['a0_writes'] += 1

                                elif signal == REG_WRITE_DATA_SIGNAL:
                                    if self.binary_to_int(value) == 42:
                                        stats['a0_value_42'] = True

                        # Parse single-bit value changes
                        elif len(line) >= 2 and line[0] in '01xz':
                            value = line[0]
                            symbol = line[1:]
                            signal = self.signals.get(symbol)

                            if signal == MEM_WRITE_ENABLE_SIGNAL and value == '1':
                                stats['memory_writes'] += 1

        except FileNotFoundError:
            print(f"Error: VCD file not found at '{self.vcd_file}'")
            sys.exit(1)
        except Exception as e:
            print(f"Error parsing VCD file: {e}")
            sys.exit(1)

        return stats

    def print_report(self, stats: DefaultDict[str, int or bool]) -> bool:
        """Prints a formatted analysis report."""
        jit_executed = stats['pc_at_buffer'] > MIN_CYCLES_IN_JIT_BUFFER
        status = "[PASS]" if jit_executed else "[FAIL]"

        print("=" * 70)
        print(" VCD Trace Analysis Report - 0-minimal RISC-V CPU")
        print("=" * 70)
        print(f"\nOverall Status: {status}\n")

        print("Key Findings:")
        print(f"  - JIT Code Execution: {'OK' if jit_executed else 'FAIL'} ({stats['pc_at_buffer']} cycles at buffer)")
        print(f"  - Register a0 == 42: {'YES' if stats['a0_value_42'] else 'NO'}")
        print(f"  - Memory Writes Detected: {'YES' if stats['memory_writes'] > 0 else 'NO'} ({stats['memory_writes']} writes)\n")

        print("Detailed Statistics:")
        print(f"  - PC Samples: {stats['pc_samples']}")
        print(f"  - Max PC Address: 0x{stats['max_pc']:08x}")
        print(f"  - Register Writes: {stats['register_writes']}")
        print(f"  - Writes to a0 (x{A0_REGISTER_INDEX}): {stats['a0_writes']}\n")

        print("Expected Memory Layout:")
        print(f"  - Entry Point:       0x{ENTRY_POINT:08x}")
        print(f"  - JIT Code Buffer:   0x{JIT_CODE_BUFFER:08x}")
        print(f"  - JIT Instructions:  0x{JIT_INSTRUCTIONS:08x}\n")

        print("Interpretation:")
        if jit_executed:
            print("  - [OK] CPU successfully executed the JIT self-modifying code.")
            print("  - Note: ChiselTest validates a0=42 via a separate debug interface.")
        else:
            print("  - [FAIL] JIT execution did not occur as expected.")
            print("  - Check simulation time and VCD signal integrity.")
        print("\n" + "=" * 70)

        return jit_executed

# Main Execution

def main() -> None:
    """Main function to parse arguments and run the VCD analyzer."""
    parser = argparse.ArgumentParser(description='VCD Trace Analyzer for the minimal RISC-V CPU.')
    parser.add_argument('vcd_file', help='Path to the VCD trace file.')
    args = parser.parse_args()

    print(f"Analyzing '{args.vcd_file}'...")
    analyzer = VCDAnalyzer(args.vcd_file)
    stats = analyzer.parse_and_analyze()

    print(f"Parsed {len(analyzer.signals)} relevant signals.")
    success = analyzer.print_report(stats)

    sys.exit(0 if success else 1)

if __name__ == "__main__":
    main()

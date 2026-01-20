#!/usr/bin/env python3
import os
import re
import subprocess
import sys
import time

def parse_output(output):
    ipc_match = re.search(r"IPC:\s+([\d\.]+)", output)
    mr_match = re.search(r"Misprediction rate:\s+([\d\.]+)%", output)
    cycles_match = re.search(r"Cycles:\s+(\d+)", output)
    
    ipc = ipc_match.group(1) if ipc_match else "N/A"
    mr = mr_match.group(1) + "%" if mr_match else "N/A"
    cycles = cycles_match.group(1) if cycles_match else "N/A"
    return ipc, mr, cycles

def run_command(cmd, cwd=None):
    try:
        # Check if environment is sane
        env = os.environ.copy()
        result = subprocess.run(cmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, cwd=cwd, env=env)
        if result.returncode != 0:
            # Only print error if it's not a timeout (which returns non-zero usually 124, but here checks generic failure)
            # If make fails, return empty to signal failure
            return ""
        return result.stdout
    except Exception as e:
        print(f"Exception running command: {cmd}")
        print(e)
        return ""

import argparse

def main():
    parser = argparse.ArgumentParser(description="Run benchmarks for MyCPU")
    parser.add_argument("benchmark", nargs="?", help="Specific benchmark to run (partial name match)")
    parser.add_argument("--config", "-c", help="Specific config to run (e.g., BTB, Per-B)")
    args = parser.parse_args()

    # Determine base directory (one level up from this script)
    script_dir = os.path.dirname(os.path.abspath(__file__))
    base_dir = os.path.dirname(script_dir) # Go up to 4-soc/
    
    # Check if Makefile exists
    if not os.path.exists(os.path.join(base_dir, "Makefile")):
        print(f"Error: Makefile not found in {base_dir}")
        print("Please run this script from inside the 4-soc/scripts directory.")
        sys.exit(1)
    
    # Configurations
    # Format: (Name, ConfigString)
    all_configs = [
        ("BTB", "MODEL=btb"),
        ("GShare", "MODEL=gshare"),
        ("TwoLevel", "MODEL=twolevel"),
        ("Per-T", "MODEL=per-t"),
        ("Per-B", "MODEL=per-b")
    ]
    
    # Filter configs if requested
    if args.config:
        configs = [c for c in all_configs if args.config.lower() in c[0].lower()]
        if not configs:
            print(f"Error: Config '{args.config}' not found.")
            sys.exit(1)
    else:
        configs = all_configs

    # Benchmarks
    # Format: (Name, Target, Params)
    all_benchmarks = [
        ("bubblesort(50)", "sim_bub", "SIZE=50"),
        ("bubblesort(100)", "sim_bub", "SIZE=100"),
        ("shellsort(50)", "sim_shellsort", "SIZE=50"),
        ("shellsort(100)", "sim_shellsort", "SIZE=100"),
        ("dijkstra", "sim_dijkstra", ""),
        ("chess", "sim_chess", ""),
        ("branchstress", "sim_branchstress", ""),
    ]

    # Filter benchmarks if requested
    if args.benchmark:
        benchmarks = [b for b in all_benchmarks if args.benchmark.lower() in b[0].lower()]
        if not benchmarks:
            print(f"Error: Benchmark '{args.benchmark}' not found.")
            sys.exit(1)
    else:
        benchmarks = all_benchmarks
    
    results = {} # Key: (benchmark_name, config_name) -> (ipc, mr, cycles)
    
    print(f"Starting Benchmark Run for {len(benchmarks)} benchmarks across {len(configs)} configurations...")
    print(f"Base Directory: {base_dir}")

    # Run loop
    for config_name, config_args in configs:
        print(f"\n[{config_name}] Configuration ({config_args})")
        
        # 1. Clean and Build ONCE for this configuration
        print(f"  Building hardware model...")
        run_command("make clean", cwd=base_dir)
        # Force build of 'verilator' target to compile the model first
        build_cmd = f"make {config_args} verilator"
        run_command(build_cmd, cwd=base_dir)
        
        for bench_name, target, params in benchmarks:
            print(f"  Running {bench_name}...", end="", flush=True)
            
            # Use 'verilator' to run the sim using existing binary if possible, 
            # but standard 'make sim' might depend on Verilator target which triggers check.
            # Best way: 'make sim' is just: ./check ...
            # We can use the compiled binary directly or call 'make sim' which shouldn't recompile if deps unchanged.
            # However, 'make clean' killed obj_dir.
            # Let's rely on 'make sim' being smart enough if we don't clean inside loop.
            
            cmd = f"make {config_args} {params} {target}"
            # print(f"    Executing: {cmd}")
            
            # Using a simplified run wrapper
            start_time = time.time()
            output = run_command(cmd, cwd=base_dir)
            elapsed = time.time() - start_time
            
            if not output:
                print(f" FAILED (Command error)")
                results[(bench_name, config_name)] = ("ERR", "ERR", "ERR")
                continue
                
            ipc, mr, cycles = parse_output(output)
            
            if cycles == "N/A":
                print(f" FAILED (Parse error)")
                results[(bench_name, config_name)] = ("ERR", "ERR", "ERR")
            else:
                print(f" Done ({elapsed:.1f}s) -> Cycles={cycles}, IPC={ipc}, MR={mr}")
                results[(bench_name, config_name)] = (ipc, mr, cycles)

    # Print Markdown Table if we ran more than one thing, or just simple output
    print("\n\n# Benchmark Results")
    
    # Header 1: Configs
    # Table Structure: | Benchmark | Cycles (BTB) | MR (BTB) | Cycles (GShare) | MR (GShare) | ...
    
    headers = ["Benchmark"]
    for cfg, _ in configs:
        headers.append(f"Cyc {cfg}")
        headers.append(f"MR {cfg}")
    
    header_line = "| " + " | ".join(headers) + " |"
    sep_line = "| " + " | ".join(["---"] * len(headers)) + " |"
    
    print(header_line)
    print(sep_line)
    
    for bench_name, _, _ in benchmarks:
        row = [f"`{bench_name}`"]
        for config_name, _ in configs:
            ipc, mr, cycles = results.get((bench_name, config_name), ("-", "-", "-"))
            if cycles != "ERR" and cycles != "N/A" and cycles != "-":
                # Format cycles with commas
                try: 
                    cycles_fmt = f"{int(cycles):,}"
                except:
                    cycles_fmt = cycles
                row.append(cycles_fmt)
                row.append(mr)
            else:
                row.append("ERR")
                row.append("ERR")
        print("| " + " | ".join(row) + " |")

if __name__ == "__main__":
    main()

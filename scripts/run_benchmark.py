#!/usr/bin/env python3
import os
import subprocess
import re
import sys

# Configurations
PREDICTORS = {
    "BTB":          {"GSHARE": "0", "TWOLEVEL": "0", "PERCEPTRON_B": "0"},
    "GSHARE":       {"GSHARE": "1", "TWOLEVEL": "0", "PERCEPTRON_B": "0"},
    "TWOLEVEL":     {"GSHARE": "0", "TWOLEVEL": "1", "PERCEPTRON_B": "0"},
    "PERCEPTRON_B": {"GSHARE": "0", "TWOLEVEL": "0", "PERCEPTRON_B": "1"},
}

BENCHMARKS = [
    {"name": "sim_bub(50)",       "target": "sim_bub",       "args": "SIZE=50"},
    {"name": "sim_bub(100)",      "target": "sim_bub",       "args": "SIZE=100"},
    {"name": "sim_shellsort(50)", "target": "sim_shellsort", "args": "SIZE=50"},
    {"name": "sim_shellsort(100)","target": "sim_shellsort", "args": "SIZE=100"},
    {"name": "sim_dijkstra",      "target": "sim_dijkstra",  "args": ""},
    {"name": "sim_chess",         "target": "sim_chess",     "args": ""},
]

def run_command(cmd, cwd=None, env=None):
    """Run a shell command and return stdout/stderr."""
    # print(f"Running: {cmd}")
    try:
        result = subprocess.run(
            cmd,
            shell=True,
            cwd=cwd,
            env=env,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        return result.stdout
    except subprocess.CalledProcessError as e:
        print(f"Error running command: {cmd}")
        print(e.stderr)
        return None

def parse_results(output):
    """Parse IPC, Mispredictions, etc. from simulation output."""
    stats = {}
    
    # instructions
    m_inst = re.search(r"Instructions: (\d+)", output)
    if m_inst:
        stats["inst"] = int(m_inst.group(1))
    
    # cycles
    m_cyc = re.search(r"Cycles: (\d+)", output)
    if m_cyc:
        stats["cycles"] = int(m_cyc.group(1))
        
    # IPC
    m_ipc = re.search(r"IPC: ([\d\.]+)", output)
    if m_ipc:
        stats["ipc"] = float(m_ipc.group(1))
        
    # Branch Misprediction Rate
    m_rate = re.search(r"Misprediction rate: ([\d\.]+)%", output)
    if m_rate:
        stats["rate"] = float(m_rate.group(1))
        
    # Total branches
    m_branches = re.search(r"Total branches: (\d+)", output)
    if m_branches:
        stats["branches"] = int(m_branches.group(1))
    
    return stats

def main():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    # Assuming script is in 4-soc/, verify Makefile exists
    if not os.path.exists(os.path.join(base_dir, "Makefile")):
        print("Error: Makefile not found in script directory")
        sys.exit(1)

    results_table = {} # (predictor, benchmark) -> stats

    for pred_name, pred_env in PREDICTORS.items():
        print(f"\n=== Building Hardware for Predictor: {pred_name} ===")
        
        # 1. Clean previous verilog generation to force rebuild
        verilog_path = os.path.join(base_dir, "verilog/verilator/Top.v")
        if os.path.exists(verilog_path):
            os.remove(verilog_path)
            
        # 2. Build Simulator (make verilator)
        # Construct make command vars
        make_vars = " ".join([f"{k}={v}" for k, v in pred_env.items()])
        build_cmd = f"make verilator {make_vars}"
        
        out = run_command(build_cmd, cwd=base_dir)
        if out is None:
            print(f"Failed to build hardware for {pred_name}. Skipping...")
            continue
            
        # 3. Run Benchmarks
        for bench in BENCHMARKS:
            bench_name = bench["name"]
            print(f"  Running {bench_name}...")
            
            run_cmd = f"make {bench['target']} {bench['args']} {make_vars}" # Pass vars just in case, though mainly needed for verilator
            # Note: sim target runs ./VTop, it doesn't rebuild VTop unless dependency triggers.
            # But the dependency 'verilator' is satisfied by existing Top.v.
            # So we shouldn't need to force rebuild here, it uses the currently built one.
            
            # However, ensure csrc is rebuilt for SIZE changes (sim_bub, sim_shellsort)
            # The Makefile targets for these explicitly call $(MAKE) -C csrc ...
            
            sim_output = run_command(run_cmd, cwd=base_dir)
            if sim_output:
                stats = parse_results(sim_output)
                results_table[(pred_name, bench_name)] = stats
                print(f"    IPC: {stats.get('ipc', 'N/A')}, Rate: {stats.get('rate', 'N/A')}%")
            else:
                print(f"    Failed to run {bench_name}")

    # Print Summary Table
    print("\n" + "="*80)
    print(f"{'Predictor':<15} | {'Benchmark':<20} | {'IPC':<8} | {'Mispred %':<10} | {'Cycles':<10}")
    print("-" * 80)
    
    # Sort by Predictor then Benchmark
    sorted_keys = sorted(results_table.keys(), key=lambda x: (list(PREDICTORS.keys()).index(x[0]), BENCHMARKS.index([b for b in BENCHMARKS if b['name'] == x[1]][0])))
    
    for pred, bench in sorted_keys:
        stats = results_table[(pred, bench)]
        ipc = f"{stats.get('ipc', 0):.3f}"
        rate = f"{stats.get('rate', 0):.2f}"
        cycles = str(stats.get('cycles', 0))
        print(f"{pred:<15} | {bench:<20} | {ipc:<8} | {rate:<10} | {cycles:<10}")
    print("="*80)

if __name__ == "__main__":
    main()

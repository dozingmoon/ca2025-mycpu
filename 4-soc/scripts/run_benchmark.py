import os
import re
import subprocess
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
            print(f"Error running command: {cmd}")
            print("STDOUT:", result.stdout[-500:]) # Print last 500 chars
            print("STDERR:", result.stderr[-500:])
            return ""
        return result.stdout
    except Exception as e:
        print(f"Exception running command: {cmd}")
        print(e)
        return ""

def main():
    base_dir = "/home/jctai/Projects/ca2025-mycpu/4-soc"
    
    # Configurations
    # Format: (Name, ConfigString)
    configs = [
        ("BTB", "MODEL=btb"),
        ("GShare", "MODEL=gshare"),
        ("TwoLevel", "MODEL=twolevel"),
        ("Per-T", "MODEL=per-t"),
        ("Per-B", "MODEL=per-b")
    ]
    
    # Benchmarks
    # Format: (Name, Target, Params)
    benchmarks = [
        ("bubblesort(50)", "sim_bub", "SIZE=50"),
        ("dijkstra", "sim_dijkstra", ""),
        ("shellsort(50)", "sim_shellsort", "SIZE=50"),
        ("parser", "sim_parser", ""),
        ("chess", "sim_chess", ""),
        ("pattern", "sim_pattern", "")
    ]
    
    results = {} # Key: (benchmark_name, config_name) -> (ipc, mr, cycles)
    
    print("Starting Comprehensive Benchmark Run...")
    
    # Run loop
    for config_name, config_args in configs:
        print(f"\n[{config_name}] Configuration")
        
        # 1. Clean to ensure recompilation of hardware with new config
        print(f"cleaning build...")
        run_command("make clean", cwd=base_dir)
        
        for bench_name, target, params in benchmarks:
            print(f"  Running {bench_name}...")
            
            cmd = f"make {config_args} {params} {target}"
            print(f"    Executing: {cmd}")
            
            output = run_command(cmd, cwd=base_dir)
            if not output:
                print(f"    FAILED")
                results[(bench_name, config_name)] = ("ERR", "ERR", "ERR")
                continue
                
            ipc, mr, cycles = parse_output(output)
            print(f"    Result: Cycles={cycles}, IPC={ipc}, MR={mr}")
            results[(bench_name, config_name)] = (ipc, mr, cycles)

    # Print Markdown Table
    print("\n\n# Benchmark Results")
    headers = ["Benchmark"]
    for cfg, _ in configs:
        headers.append(f"Cycles {cfg}")
        headers.append(f"MR {cfg}")
    
    header_line = "| " + " | ".join(headers) + " |"
    sep_line = "| " + " | ".join(["---"] * len(headers)) + " |"
    
    print(header_line)
    print(sep_line)
    
    for bench_name, _, _ in benchmarks:
        row = [f"`{bench_name}`"]
        for config_name, _ in configs:
            ipc, mr, cycles = results.get((bench_name, config_name), ("-", "-", "-"))
            if cycles != "ERR":
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

import os
import re
import subprocess
import random

def generate_data(filename, size=200):
    data = [random.randint(0, 1000) for _ in range(size)]
    with open(filename, "w") as f:
        f.write("int data[] = {" + ",".join(map(str, data)) + "};\n")

def modify_size(filename, size):
    with open(filename, "r") as f:
        content = f.read()
    
    # regex to replace #define SIZE ...
    new_content = re.sub(r"#define SIZE \d+", f"#define SIZE {size}", content)
    
    with open(filename, "w") as f:
        f.write(new_content)

def parse_output(output):
    ipc_match = re.search(r"IPC:\s+([\d\.]+)", output)
    mr_match = re.search(r"Misprediction rate:\s+([\d\.]+)%", output)
    
    ipc = ipc_match.group(1) if ipc_match else "N/A"
    mr = mr_match.group(1) + "%" if mr_match else "N/A"
    return ipc, mr

def run_command(cmd, cwd=None):
    try:
        result = subprocess.run(cmd, shell=True, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, cwd=cwd)
        return result.stdout
    except subprocess.CalledProcessError as e:
        print(f"Error running command: {cmd}")
        print(e.stdout)
        print(e.stderr)
        return ""

def main():
    base_dir = "/home/jctai/Projects/ca2025-mycpu/4-soc"
    csrc_dir = os.path.join(base_dir, "csrc")
    data_header = os.path.join(csrc_dir, "bubblesort_data.h")
    c_file = os.path.join(csrc_dir, "bubblesort.c")
    
    # 1. Generate Data
    print("Generating data...")
    generate_data(data_header, 200)
    
    sizes = [10, 50, 100, 200]
    configs = [0, 1] # GSHARE=0, GSHARE=1
    
    results = {}
    
    for size in sizes:
        print(f"Configuring Bubblesort Size: {size}")
        modify_size(c_file, size)
        
        # Compile asmbin
        print("Compiling asmbin...")
        run_command("make bubblesort.asmbin", cwd=csrc_dir)
        
        for gshare in configs:
            print(f"Running size={size}, GSHARE={gshare}...")
            # Use specific invalidation of verilator/obj_dir dependent on GSHARE change?
            # Actually standard make verilator target should handle it, BUT
            # we need to force sbt run if GSHARE changes.
            # The Makefile does:
            # verilator target depends on sbt running Board.VerilogGenerator.
            # The Board.VerilogGenerator depends on sbt arg.
            # So calling make GSHARE=... check-riscof (no)
            # make GSHARE=... sim_bub
            # sim_bub -> sim -> verilator -> sbt ...
            # Yes, make should handle it.
            
            # Note: sim_bub calls 'make sim ...'. We need to pass GSHARE to the outer make.
            output = run_command(f"make GSHARE={gshare} sim_bub", cwd=base_dir)
            
            ipc, mr = parse_output(output)
            print(f"Result: IPC={ipc}, MR={mr}")
            results[(size, gshare)] = (ipc, mr)
            
    print("\n\nFinal Results:")
    print("||IPC - BTB|IPC - gshare|MR - BTB|MR - gshare|")
    print("|-|-|-|-|-|")
    
    # Print fib(20) row (static)
    print("|`fib(20)`|0.160|0.160|8.30%|5.02%|")
    
    # Print bubblesort rows
    for size in sizes:
        ipc_btb, mr_btb = results.get((size, 0), ("-", "-"))
        ipc_gshare, mr_gshare = results.get((size, 1), ("-", "-"))
        print(f"|`bubblesort({size})`|{ipc_btb}|{ipc_gshare}|{mr_btb}|{mr_gshare}|")

if __name__ == "__main__":
    main()

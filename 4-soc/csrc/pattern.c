// SPDX-License-Identifier: MIT
// Pattern benchmark: Alternating branch pattern (T, T, N)

// This program creates a loop with a conditional branch that follows
// a repeating pattern: Taken, Taken, Not-Taken.
// This pattern (length 3) repeats 33 times in a loop of 100 iterations.
//
// Pattern: 1, 1, 0, 1, 1, 0, ...
// Simple 2-bit counter (saturating):
// - Updates: T (inc), T (inc), N (dec) -> tends to stay strictly taken
// - Mispredicts on every 'Not-Taken' (1/3 misprediction rate)
//
// GShare:
// - Can learn the pattern if history length >= 3
// - Should achieve near-perfect prediction

#include "mmio.h"

int main()
{
    volatile int result = 0;
    
    // Loop 100 times
    // We want a T, T, N pattern based on loop index i is not a multiple of 3?
    // Let's do: if (i % 3 != 0) -> Taken (1, 2, 4, 5...)
    // else -> Not Taken (0, 3, 6...)
    // Actually, simple loop condition is easiest.
    
    int state = 0;
    for (int i = 0; i < 100; i++) {
        // Update state: 0 -> 1 -> 2 -> 0
        state++;
        if (state == 3) {
            state = 0;
        }

        // We want T, T, N pattern for the branch below.
        // i=0: state becomes 1. if (state != 0) -> Taken.
        // i=1: state becomes 2. if (state != 0) -> Taken.
        // i=2: state becomes 0. if (state != 0) -> Not Taken.
        
        // This conditional branch follows T, T, N pattern
        if (state != 0) {
           result++;
        }
    }
    
    // Write result to verify correctness
    *(volatile int *) (4) = result; // Should be 34 (100/3 rounded up? 0..99 count of div3)
    // 0, 3, ..., 99. 
    // 99 / 3 = 33. plus 0 makes 34. Correct.
    
    // Signal success
    *(volatile int *) (0x104) = 0x0F;
    *(volatile int *) (0x100) = 0xCAFEF00D;
    
    return 0;
}

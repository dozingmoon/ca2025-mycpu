// SPDX-License-Identifier: MIT
// Aliasing benchmark: Multiple loops that collide in small BTB

// This program creates multiple loops with branches that alias
// in a small direct-mapped BTB but can be distinguished by GShare
// using global history.

#define ITERATIONS 100

// Force functions to be at different addresses
// Each function has a loop with similar structure
__attribute__((noinline))
int loop_a(int n)
{
    int sum = 0;
    for (int i = 0; i < n; i++) {
        sum += i;
    }
    return sum;
}

__attribute__((noinline))
int loop_b(int n)
{
    int sum = 0;
    for (int i = 0; i < n; i++) {
        sum += i * 2;
    }
    return sum;
}

__attribute__((noinline))
int loop_c(int n)
{
    int sum = 0;
    for (int i = 0; i < n; i++) {
        sum += i * 3;
    }
    return sum;
}

__attribute__((noinline))
int loop_d(int n)
{
    int sum = 0;
    for (int i = 0; i < n; i++) {
        sum += i * 4;
    }
    return sum;
}

// Interleave calls to create varied global history patterns
int main()
{
    int result = 0;
    
    // Pattern 1: A, B, C, D sequence (creates history pattern)
    for (int i = 0; i < ITERATIONS; i++) {
        result += loop_a(10);
        result += loop_b(10);
        result += loop_c(10);
        result += loop_d(10);
    }
    
    // Pattern 2: A, C, B, D (different history)
    for (int i = 0; i < ITERATIONS; i++) {
        result += loop_a(8);
        result += loop_c(8);
        result += loop_b(8);
        result += loop_d(8);
    }
    
    // Store result to prevent optimization
    *(volatile int *) (4) = result;
    
    // Signal completion
    *(volatile int *) (0x104) = 0x0F;
    *(volatile int *) (0x100) = 0xCAFEF00D;
    
    return 0;
}

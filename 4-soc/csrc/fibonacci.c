// SPDX-License-Identifier: MIT
// MyCPU is freely redistributable under the MIT License. See the file
// "LICENSE" for information on usage and redistribution of this file.

int fib(int n)
{
    if (n <= 1)
        return n;
    return fib(n - 1) + fib(n - 2);
}

#ifndef FIB_N
#define FIB_N 20
#endif

int main()
{
    *(int *) (4) = fib(FIB_N);
    *(volatile int *) (0x104) = 0x0F; // Signal success (UART_TEST_PASS)
    *(volatile int *) (0x100) = 0xCAFEF00D; // Signal completion to sim.cpp
}

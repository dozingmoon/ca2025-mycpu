// SPDX-License-Identifier: MIT
// MyCPU is freely redistributable under the MIT License. See the file
// "LICENSE" for information on usage and redistribution of this file.

#define SIZE 50

#include "bubblesort_data.h"

void bubblesort(int *arr, int n)
{
    int i, j, temp;
    for (i = 0; i < n - 1; i++) {
        for (j = 0; j < n - i - 1; j++) {
            if (arr[j] > arr[j + 1]) {
                temp = arr[j];
                arr[j] = arr[j + 1];
                arr[j + 1] = temp;
            }
        }
    }
}

int verify(int *arr, int n)
{
    int i;
    for (i = 0; i < n - 1; i++) {
        if (arr[i] > arr[i + 1])
            return 0; // Failed
    }
    return 1; // Passed
}

int main()
{
    bubblesort(data, SIZE);
    
    if (verify(data, SIZE)) {
        *(volatile int *) (0x104) = 0x0F; // Signal success (UART_TEST_PASS) - reuse for general success
    } else {
        *(volatile int *) (0x104) = 0x01; // Signal failure (general code)
    }
    
    *(volatile int *) (0x100) = 0xCAFEF00D; // Signal completion to sim.cpp
    
    return 0;
}

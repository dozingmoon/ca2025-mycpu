// SPDX-License-Identifier: MIT
// MyCPU is freely redistributable under the MIT License. See the file
// "LICENSE" for information on usage and redistribution of this file.

#define V 6

int minDistance(int dist[], int sptSet[])
{
    int min = 2147483647, min_index = -1;

    for (int v = 0; v < V; v++)
        if (sptSet[v] == 0 && dist[v] <= min)
            min = dist[v], min_index = v;

    return min_index;
}

void dijkstra(int graph[V][V], int src, int *result_dist)
{
    int dist[V]; 
    int sptSet[V]; 

    for (int i = 0; i < V; i++)
        dist[i] = 2147483647, sptSet[i] = 0;

    dist[src] = 0;

    for (int count = 0; count < V - 1; count++) {
        int u = minDistance(dist, sptSet);
        
        // Safety check if graph is disconnected or min_index invalid
        if (u == -1) break;

        sptSet[u] = 1;

        for (int v = 0; v < V; v++)
            if (!sptSet[v] && graph[u][v] && dist[u] != 2147483647
                && dist[u] + graph[u][v] < dist[v])
                dist[v] = dist[u] + graph[u][v];
    }
    
    // Copy result for verification
    for(int i=0; i<V; i++) {
        result_dist[i] = dist[i];
    }
}


// Graph defined as global to avoid stack initialization issues / memcpy traps
// Truncated to 6x6 for smaller test
int graph[V][V] = { { 0, 4, 0, 0, 0, 0 },
                    { 4, 0, 8, 0, 0, 0 },
                    { 0, 8, 0, 7, 0, 4 },
                    { 0, 0, 7, 0, 9, 14 },
                    { 0, 0, 0, 9, 0, 10 },
                    { 0, 0, 4, 14, 10, 0 } };

int result_dist[V];

int main()
{
    dijkstra(graph, 0, result_dist);

    // Expected distances from src=0 for V=6 graph (calculated manually):
    // 0 -> 1 (4)
    // 1 -> 2 (8) => dist[2]=12
    // 2 -> 5 (4) => dist[5]=16
    // 2 -> 3 (7) => dist[3]=19
    // 3 -> 4 (9) => 19+9=28
    // 5 -> 4 (10) => 16+10=26 (shorter)
    // So dist[4] should be 26.
    
    // Verify a specific node distance (e.g. node 4 is 26)
    if (result_dist[4] == 26) {
        *(int *) (4) = 1; // Success indicator in memory
        *(volatile int *) (0x104) = 0x0F; // UART_TEST_PASS
    } else {
        *(int *) (4) = 0; // Fail
        *(volatile int *) (0x104) = result_dist[4]; // Output actual value for debug
    }

    *(volatile int *) (0x100) = 0xCAFEF00D; // Signal completion
    return 0;
}

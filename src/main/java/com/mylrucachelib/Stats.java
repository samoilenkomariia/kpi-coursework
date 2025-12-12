package com.mylrucachelib;

public record Stats (int threads, int reqsPerThread, int totalReqs, int successfulReqs,
              int failedReqs, double totalTime, double throughput, double avgLatency) {
    @Override
    public String toString() {
        return String.format("""
                Threads %d, requests per thread %d
                Total requests %d
                Successful requests %d
                Failed requests %d
                Total time %fs
                Throughput %f req/s
                Average latency %fms""", threads, reqsPerThread, totalReqs,
                successfulReqs, failedReqs, totalTime,
                throughput, avgLatency);
    }

    public String toCSV() {
        return threads + "," + reqsPerThread + "," + throughput + "," + avgLatency;
    }
}

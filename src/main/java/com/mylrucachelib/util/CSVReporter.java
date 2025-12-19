package com.mylrucachelib.util;

import com.mylrucachelib.Stats;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class CSVReporter {
    private static final String OUTPUT_DIR = "target/csv_reports";

    public static synchronized void record(String className, String testName, Stats stats) {
        ensureDirExists();
        File file = new File(OUTPUT_DIR, className + ".csv");
        boolean isNew = !file.exists();
        try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
            if (isNew) {
                writer.println("Test Name,Threads,Requests per Thread,Throughput(req/s),Average Latency(ms)");
            }
            String info = String.format("%s,%d,%d,%.2f,%.4f",
                    testName,
                    stats.threads(),
                    stats.reqsPerThread(),
                    stats.throughput(),
                    stats.avgLatency()
            );
            writer.println(info);
        } catch (IOException e) {
            System.err.println("Failed to write csv info from tests: " + e.getMessage());
        }
    }

    private static void ensureDirExists() {
        try {
            Files.createDirectories(Paths.get(OUTPUT_DIR));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

package com.mylrucachelib;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

public class LoggerSetup {
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    // if useParent = true, logs also to console, if false, only to file.
    public static void setupLogger(String loggerName, String fileName, boolean useParent) {
        Logger logger = Logger.getLogger(loggerName);
        // remove existing handlers to prevent duplicate lines if called twice
        for (Handler h : logger.getHandlers()) {
            logger.removeHandler(h);
        }
        logger.setUseParentHandlers(useParent);
        logger.setLevel(Level.ALL);

        try {
            Path logDir = Path.of("logs");
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }

            FileHandler fileHandler = new FileHandler("logs/" + fileName, true);

            fileHandler.setFormatter(new Formatter() {
                @Override
                public String format(LogRecord record) {
                    StringBuilder builder = new StringBuilder();
                    builder.append("[")
                            .append(dateFormat.format(new Date(record.getMillis())))
                            .append("] [")
                            .append(record.getLevel())
                            .append("] ")
                            .append(formatMessage(record))
                            .append(System.lineSeparator());

                    if (record.getThrown() != null) {
                        builder.append("EXCEPTION: ");
                        builder.append(record.getThrown().toString());
                        builder.append(System.lineSeparator());
                        for (StackTraceElement element : record.getThrown().getStackTrace()) {
                            builder.append("\t").append(element.toString()).append(System.lineSeparator());
                        }
                    }
                    return builder.toString();
                }
            });

            logger.addHandler(fileHandler);

        } catch (IOException e) {
            System.err.println("Failed to setup logger for " + loggerName + ": " + e.getMessage());
        }
    }
}

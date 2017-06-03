/*
 *  Copyright (C) 2017 nandsito
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package br.com.nandsito.narina_rgc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Scanner;

/**
 * Old application entry point.
 *
 * @author nandsito
 */
public class Main {

    private static final String OLD_URL_PREFIX = "http://media.gov.gr/images/prosfygiko/";

    /**
     * Because logging is essential.
     */
    private static final Logger logger;

    static {

        // Copy Log4j 2 config file from resources to disk,
        // then create logger.

        Path configDir = Paths.get("config");

        Path log4j2File = configDir.resolve("log4j2.xml");

        try {

            if (Files.notExists(log4j2File)) {

                if (Files.notExists(configDir)) {

                    Files.createDirectory(configDir);
                }

                Files.copy(Thread
                        .currentThread()
                        .getContextClassLoader()
                        .getResourceAsStream("log4j2.xml"), log4j2File);
            }

        } catch (IOException e) {

            e.printStackTrace(System.err);
        }

        logger = LoggerFactory.getLogger(Main.class);
    }

    /**
     * List of date formatters that try to guess the date formats
     * of the Greek servers files.
     */
    private static final List<DateTimeFormatter> dtfList;

    static {

        dtfList = new ArrayList<>(12);

        dtfList.add(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.US));
        dtfList.add(DateTimeFormatter.ofPattern("dd.M.yyyy", Locale.US));
        dtfList.add(DateTimeFormatter.ofPattern("d.MM.yyyy", Locale.US));
        dtfList.add(DateTimeFormatter.ofPattern("d.M.yyyy", Locale.US));

        dtfList.add(DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US));
        dtfList.add(DateTimeFormatter.ofPattern("dd-M-yyyy", Locale.US));
        dtfList.add(DateTimeFormatter.ofPattern("d-MM-yyyy", Locale.US));
        dtfList.add(DateTimeFormatter.ofPattern("d-M-yyyy", Locale.US));

        dtfList.add(DateTimeFormatter.ofPattern("dd_MM_yyyy", Locale.US));
        dtfList.add(DateTimeFormatter.ofPattern("dd_M_yyyy", Locale.US));
        dtfList.add(DateTimeFormatter.ofPattern("d_MM_yyyy", Locale.US));
        dtfList.add(DateTimeFormatter.ofPattern("d_M_yyyy", Locale.US));
    }

    /**
     * {@code Properties} that store the language (English or Greek)
     * of a file in a given date.
     */
    private static final Properties languageProperties;

    /**
     * {@code Properties} that store the filename (i.e. its format)
     * in a given date.
     */
    private static final Properties filenameProperties;

    static {

        // Read language and filename properties, if any.

        languageProperties = new Properties();

        filenameProperties = new Properties();

        try {

            Path metadataDir = Paths.get("metadata");

            Path languagePropertiesFile = metadataDir.resolve("language.properties");

            if (Files.exists(languagePropertiesFile)) {

                try (Reader r = Files.newBufferedReader(languagePropertiesFile,
                        Charset.forName("UTF-8"))) {

                    languageProperties.load(r);
                }
            }

            Path filenamePropertiesFile = metadataDir.resolve("filename.properties");

            if (Files.exists(filenamePropertiesFile)) {

                try (Reader r = Files.newBufferedReader(filenamePropertiesFile,
                        Charset.forName("UTF-8"))) {

                    filenameProperties.load(r);
                }
            }

        } catch (IOException e) {

            logger.debug("IOException while reading properties", e);
        }
    }

    public static void main(String[] args) {

        // Read start and end dates for crawling.

        LocalDate startDate;
        LocalDate endDate;

        try (Scanner scanner = new Scanner(System.in, "UTF-8")) {

            boolean areDatesOk;

            do {

                startDate = readStartDate(scanner);
                endDate = readEndDate(scanner);

                areDatesOk = startDate.isBefore(endDate) || startDate.isEqual(endDate);

                if (!areDatesOk) {

                    System.out.println();
                    System.out.println("we can't go backwards in time yet...");
                    System.out.println();
                }

            } while (!areDatesOk);
        }

        // For each day between start and end dates, inclusive, try to get a document.

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {

            // If the English document could not be obtained,
            // try getting the Greek one.

            if (!getEnglishDocument(date)) {

                getGreekDocument(date);
            }
        }

        // Persist the properties of gotten documents,
        // so they won't need to be guessed in the future.

        try {

            Path metadataDir = Paths.get("metadata");

            if (Files.notExists(metadataDir)) {

                Files.createDirectory(metadataDir);
            }

            Path languagePropertiesFile = metadataDir.resolve("language.properties");

            try (Writer w = Files.newBufferedWriter(languagePropertiesFile,
                    Charset.forName("UTF-8"),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {

                languageProperties.store(w, null);
            }

            Path filenamePropertiesFile = metadataDir.resolve("filename.properties");

            try (Writer w = Files.newBufferedWriter(filenamePropertiesFile,
                    Charset.forName("UTF-8"),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {

                filenameProperties.store(w, null);
            }

        } catch (IOException e) {

            logger.debug("IOException while writing properties", e);
        }
    }

    /**
     * Read from input until a valid date in ISO 8601 format, or the text
     * <tt>beginning</tt> is entered. <tt>beginning</tt> is an alias for
     * 2016-03-21, which is the date of the very first published document.
     *
     * @param scanner a scanner that wraps {@link System#in}
     * @return a date read from input
     */
    private static LocalDate readStartDate(Scanner scanner) {

        LocalDate startDate = null;

        do {

            System.out.print("please enter a start date (e.g. 2016-03-21, or \"beginning\"): ");

            String line = scanner.nextLine();

            String trimmedLine = line.trim();

            if (trimmedLine.equals("beginning")) {

                startDate = LocalDate.of(2016, Month.MARCH, 21);

            } else {

                try {

                    startDate = LocalDate.parse(trimmedLine);

                } catch (DateTimeParseException e) {

                    System.out.format(Locale.US,
                            "%nsorry, i couldn't understand this start date: \"%1$s\"%n%n",
                            line);
                }
            }

        } while (startDate == null);

        return startDate;
    }

    /**
     * Read from input until a valid date in ISO 8601 format, or the text
     * <tt>today</tt> is entered. <tt>today</tt> is an alias for
     * the current date.
     *
     * @param scanner a scanner that wraps {@link System#in}
     * @return a date read from input
     */
    private static LocalDate readEndDate(Scanner scanner) {

        LocalDate endDate = null;

        do {

            System.out.format(Locale.US,
                    "please enter an end date (e.g. %1$s, or \"today\"): ",
                    LocalDate.now());

            String line = scanner.nextLine();

            String trimmedLine = line.trim();

            if (trimmedLine.equals("today")) {

                endDate = LocalDate.now();

            } else {

                try {

                    endDate = LocalDate.parse(line);

                } catch (DateTimeParseException e) {

                    System.out.format(Locale.US,
                            "%nsorry, i couldn't understand this end date: \"%1$s\"%n%n",
                            line);
                }
            }

        } while (endDate == null);

        return endDate;
    }

    /**
     * Try to get the English document for a given date.
     *
     * @param date the document date
     * @return {@code true} if the document could be obtained, or
     * {@code false} otherwise
     */
    private static boolean getEnglishDocument(LocalDate date) {

        // If the document language and file name are cached for the given date,
        // read data from cache. It is waaaaay faster.

        String languageProperty = languageProperties.getProperty(date.toString());

        String filenameProperty = filenameProperties.getProperty(date.toString());

        if (languageProperty != null
                && languageProperty.equals(Language.ENGLISH.toString())
                && filenameProperty != null
                && !filenameProperty.trim().isEmpty()) {

            String url = OLD_URL_PREFIX + filenameProperty;

            Path destinationFile = Paths.get("output",
                    "documents",
                    Integer.toString(date.getYear()),
                    String.format(Locale.US, "%1$02d", date.getMonthValue()),
                    Language.ENGLISH.toString(),
                    filenameProperty);

            try {

                if (doHttpGet(url, destinationFile)) {

                    return true;
                }

            } catch (IOException e) {

                logger.debug("IOException while getting URL {}", url, e);
            }

            try {

                Thread.sleep(50);

            } catch (InterruptedException e) {

                logger.debug("InterruptedException while sleeping", e);
            }
        }

        // Try various word separators and date formats to find out the file name.

        for (EnglishSeparator separator : EnglishSeparator.values()) {

            for (DateTimeFormatter formatter : dtfList) {

                String filename = String.format(Locale.US,
                        "REFUGEE_FLOWS%1$s%2$s.pdf",
                        separator,
                        formatter.format(date));

                String url = OLD_URL_PREFIX + filename;

                Path destinationFile = Paths.get("output",
                        "documents",
                        Integer.toString(date.getYear()),
                        String.format(Locale.US, "%1$02d", date.getMonthValue()),
                        Language.ENGLISH.toString(),
                        filename);

                try {

                    if (doHttpGet(url, destinationFile)) {

                        languageProperties.setProperty(date.toString(),
                                Language.ENGLISH.toString());

                        filenameProperties.setProperty(date.toString(), filename);

                        return true;
                    }

                } catch (IOException e) {

                    logger.debug("IOException while getting URL {}", url, e);
                }

                try {

                    Thread.sleep(50);

                } catch (InterruptedException e) {

                    logger.debug("InterruptedException while sleeping", e);
                }
            }
        }

        return false;
    }

    /**
     * Try to get the Greek document for a given date.
     *
     * @param date the document date
     * @return {@code true} if the document could be obtained, or
     * {@code false} otherwise
     */
    @SuppressWarnings("UnusedReturnValue")
    private static boolean getGreekDocument(LocalDate date) {

        // If the document language and file name are cached for the given date,
        // read data from cache.

        String languageProperty = languageProperties.getProperty(date.toString());

        String filenameProperty = filenameProperties.getProperty(date.toString());

        if (languageProperty != null
                && languageProperty.equals(Language.GREEK.toString())
                && filenameProperty != null
                && !filenameProperty.trim().isEmpty()) {

            String url = OLD_URL_PREFIX + filenameProperty;

            Path destinationFile = Paths.get("output",
                    "documents",
                    Integer.toString(date.getYear()),
                    String.format(Locale.US, "%1$02d", date.getMonthValue()),
                    Language.GREEK.toString(),
                    filenameProperty);

            try {

                if (doHttpGet(url, destinationFile)) {

                    return true;
                }

            } catch (IOException e) {

                logger.debug("IOException while getting URL {}", url, e);
            }

            try {

                Thread.sleep(50);

            } catch (InterruptedException e) {

                logger.debug("InterruptedException while sleeping", e);
            }
        }

        // Try various date formats to find out the file name.

        for (DateTimeFormatter formatter : dtfList) {

            String filename = String.format(Locale.US, "%1$s.pdf", formatter.format(date));

            String url = OLD_URL_PREFIX + filename;

            Path destinationFile = Paths.get("output",
                    "documents",
                    Integer.toString(date.getYear()),
                    String.format(Locale.US, "%1$02d", date.getMonthValue()),
                    Language.GREEK.toString(),
                    filename);

            try {

                if (doHttpGet(url, destinationFile)) {

                    languageProperties.setProperty(date.toString(),
                            Language.GREEK.toString());

                    filenameProperties.setProperty(date.toString(), filename);

                    return true;
                }

            } catch (IOException e) {

                logger.debug("IOException while getting URL {}", url, e);
            }

            try {

                Thread.sleep(50);

            } catch (InterruptedException e) {

                logger.debug("InterruptedException while sleeping", e);
            }
        }

        return false;
    }

    /**
     * Do an HTTP GET and store the gotten content, if any.
     *
     * @param url             the location to get the content from
     * @param destinationFile the file where the content will be stored
     * @return {@code true} if the document could be properly downloaded, or
     * {@code false} otherwise
     * @throws IOException if a network or file system exception occurs
     */
    private static boolean doHttpGet(String url, Path destinationFile) throws IOException {

        HttpURLConnection connection;

        try {

            connection = (HttpURLConnection) new URL(url).openConnection();

        } catch (MalformedURLException e) {

            throw new RuntimeException(e);
        }

        try {

            connection.setRequestMethod("GET");

        } catch (ProtocolException e) {

            throw new RuntimeException(e);
        }

        int responseCode;

        // try-finally for disconnecting the connection
        try {

            responseCode = connection.getResponseCode();

            if (responseCode == 200) {

                try (InputStream is = connection.getInputStream()) {

                    if (Files.notExists(destinationFile.getParent())) {

                        Files.createDirectories(destinationFile.getParent());
                    }

                    Files.copy(is, destinationFile, StandardCopyOption.REPLACE_EXISTING);
                }
            }

        } finally {

            connection.disconnect();
        }

        logger.debug("HTTP GET {} {}", url, responseCode);

        return responseCode == 200;
    }

    /**
     * The available languages: English and Greek.
     */
    private enum Language {

        ENGLISH("english"), GREEK("greek");

        private final String language;

        Language(String language) {

            this.language = language;
        }

        @Override
        public String toString() {

            return language;
        }
    }

    /**
     * Word separators for English.
     */
    private enum EnglishSeparator {

        HYPHEN_MINUS("-"), LOW_LINE("_");

        private final String separator;

        EnglishSeparator(String separator) {

            this.separator = separator;
        }

        @Override
        public String toString() {

            return separator;
        }
    }
}

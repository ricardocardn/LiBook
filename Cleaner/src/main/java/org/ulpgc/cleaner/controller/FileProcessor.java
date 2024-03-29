package org.ulpgc.cleaner.controller;

import org.ulpgc.cleaner.controller.message.Consumer;
import org.ulpgc.cleaner.controller.message.Publisher;
import org.ulpgc.cleaner.controller.message.broker.EventConsumer;
import org.ulpgc.cleaner.controller.message.broker.EventPublisher;
import org.ulpgc.cleaner.controller.process.Cleaner;
import org.ulpgc.cleaner.controller.process.ParseFiles;
import org.ulpgc.cleaner.controller.reader.Reader;
import org.ulpgc.cleaner.controller.writer.Writer;

import javax.jms.JMSException;
import java.nio.file.*;
import java.util.List;

public class FileProcessor {
    private final String listenedPath;
    private final Reader reader;
    private final ParseFiles parseFiles;
    private final Cleaner cleaner;
    private final Consumer eventConsumer;
    private final Publisher eventPublisher;
    private final Publisher metadataPublisher;

    public FileProcessor(String listenedPath, Reader reader, ParseFiles parseFiles, Cleaner cleaner) throws JMSException {
        this.listenedPath = listenedPath;
        this.reader = reader;
        this.parseFiles = parseFiles;
        this.cleaner = cleaner;
        this.eventConsumer = new EventConsumer("61616", "datalakeEvents");
        this.eventPublisher = new EventPublisher("61616", "cleanerEvents");
        this.metadataPublisher = new EventPublisher("61616", "cleanerMetadataEvents");
    }

    public void run() {
        while (true) {
            String file = eventConsumer.getMessage();
            try {
                processFile(file);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    protected void processFile(String filePath)  throws Exception {
        String book = reader.readFile(Path.of(listenedPath + "/RawBooks/" + filePath));
        List<String> content = parseFiles.processFile(book);
        String cleaned_Content = cleaner.processFile(content);

        Writer writer = new Writer(Path.of(listenedPath + "/Content"),
                Path.of(listenedPath + "/Metadata"),
                Path.of("./Cleaner/src/main/resources/cleanEvents"));

        writer.writeToDatalake(cleaned_Content, filePath);
        writer.writeMetadataToDatalake(content.get(1), filePath);

        eventPublisher.publish("Content/" + filePath);
        metadataPublisher.publish("Content/" + filePath);
    }
}


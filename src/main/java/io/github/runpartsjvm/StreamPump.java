package io.github.runpartsjvm;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

class StreamPump implements Runnable {

    final InputStream inputStream;

    final Consumer<String> consumer;

    final Supplier<Optional<String>> reportSupplier;

    public StreamPump(InputStream inputStream, Consumer<String> consumer, Supplier<Optional<String>> reportSupplier) {
        this.inputStream = inputStream;
        this.consumer = consumer;
        this.reportSupplier = reportSupplier;
    }

    @Override
    public void run() {
        new BufferedReader(new InputStreamReader(inputStream))
            .lines()
            .peek(s -> reportSupplier.get().ifPresent(consumer))
            .forEach(consumer);
    }

}

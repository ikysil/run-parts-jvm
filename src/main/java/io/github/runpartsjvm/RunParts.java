package io.github.runpartsjvm;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static java.lang.System.err;
import static java.lang.System.out;

@TopCommand
@CommandLine.Command(name = "run-parts", showEndOfOptionsDelimiterInUsageHelp = true)
public class RunParts implements Callable<Integer> {

    @CommandLine.Option(names = {"--test"})
    boolean test;

    @CommandLine.Option(names = {"--list"})
    boolean list;

    @CommandLine.Option(names = {"-v", "--verbose"})
    boolean verbose;

    @CommandLine.Option(names = {"--report"})
    boolean report;

    @CommandLine.Option(names = {"--reverse"})
    boolean reverse;

    @CommandLine.Option(names = {"--exit-on-error"})
    boolean exitOnError;

    @CommandLine.Option(names = {"--umask"}, defaultValue = "022")
    int umask;

    @CommandLine.Option(names = {"--lsbsysinit"})
    boolean lsbSysInit;

    @CommandLine.Option(names = {"--regex"}, arity = "1..1")
    Pattern regex;

    @CommandLine.Option(names = {"-a", "--arg"}, arity = "1..*")
    List<String> args = new ArrayList<>();

    @CommandLine.Option(names = {"-h", "--help"})
    boolean help;

    @CommandLine.Parameters(paramLabel = "DIRECTORY", arity = "1..1")
    File dir;

    int exitCode = CommandLine.ExitCode.OK;

    final ExecutorService executorService = Executors.newFixedThreadPool(5);

    @Override
    public Integer call() throws Exception {
        if ((dir == null) || !dir.isDirectory() || !dir.canRead()) {
            err.printf("Not a directory: %s%n", dir);
            return CommandLine.ExitCode.USAGE;
        }
        final File[] files = getFiles();
        Arrays.stream(files)
            .filter(this::filter)
            .forEach(this::process);
        return exitCode;
    }

    private void process(File f) {
        if (exitOnError && (exitCode != CommandLine.ExitCode.OK)) {
            // shortcut execution of the remaining scripts
            return;
        }
        exitCode = CommandLine.ExitCode.OK;
        if (list) {
            out.printf("%s %s%n", f, String.join(" ", args));
            return;
        }
        if (!f.canExecute()) {
            return;
        }
        if (test) {
            out.printf("%s %s%n", f, String.join(" ", args));
            return;
        }
        // TODO - implement random sleep
        if (verbose) {
            err.printf("%s %s%n", f, String.join(" ", args));
        }
        // TODO - implement umask
        try {
            exitCode = exec(f);
            if (verbose) {
                err.printf("%s %s exit status %s%n", f, String.join(" ", args), exitCode);
            }
        } catch (Exception e) {
            exitCode = 255;
        }
    }

    private int exec(File f) throws InterruptedException, IOException {
        final String fPath = f.toString();
        final OutputReport outputReport = new OutputReport(fPath, report, verbose);
        final ProcessBuilder processBuilder = new ProcessBuilder().command(fPath);
        processBuilder.command().addAll(args);
        final Process process = processBuilder.start();
        final StreamPump outStreamPump = new StreamPump(process.getInputStream(), out::println, outputReport::getOutReport);
        final StreamPump errStreamPump = new StreamPump(process.getErrorStream(), err::println, outputReport::getErrReport);
        executorService.submit(outStreamPump);
        executorService.submit(errStreamPump);
        return process.waitFor();
    }

    private File[] getFiles() {
        final File[] files = dir.listFiles();
        if (files == null) {
            return new File[0];
        }
        final Comparator<File> fileNameComparator = getFileNameComparator();
        Arrays.sort(files, fileNameComparator);
        return Arrays.stream(files)
            .filter(this::filter)
            .toArray(File[]::new);
    }

    private static final String[] stdSuffixToIgnore = {
        "~", ",",
        ".disabled", ".cfsaved",
        ".rpmsave", ".rpmorig", ".rpmnew",
        ".swp", ",v"
    };

    private static final String[] lsbSysInitSuffixToIgnore = {
        ".dpkg-old", ".dpkg-dist", ".dpkg-new", ".dpkg-tmp"
    };

    private static final List<Predicate<String>> lsbSysInitPatternToAccept = Arrays.asList(
        Pattern.compile("^[a-z0-9]+$").asMatchPredicate(), // LANANA-assigned LSB hierarchical
        Pattern.compile("^_?([a-z0-9_.]+-)+[a-z0-9]+$").asMatchPredicate(), // LANANA-assigned LSB reserved
        Pattern.compile("^[a-zA-Z0-9_-]+$").asMatchPredicate() // Debian cron script namespaces
    );

    private boolean filter(File f) {
        if (f.isDirectory()) {
            return false;
        }
        final String fName = f.getName();
        if (Arrays.stream(stdSuffixToIgnore).anyMatch(fName::endsWith)) {
            return false;
        }
        if (lsbSysInit) {
            if (Arrays.stream(lsbSysInitSuffixToIgnore).anyMatch(fName::endsWith)) {
                return false;
            }
            if (lsbSysInitPatternToAccept.stream().noneMatch(p -> p.test(fName))) {
                return false;
            }
        }
        if (regex != null) {
            return regex.asMatchPredicate().test(fName);
        }
        return true;
    }

    private Comparator<File> getFileNameComparator() {
        final Comparator<File> baseComparator = Comparator.comparing(File::getName);
        if (reverse) {
            return baseComparator.reversed();
        } else {
            return baseComparator;
        }
    }

}

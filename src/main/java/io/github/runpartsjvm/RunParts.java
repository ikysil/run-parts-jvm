package io.github.runpartsjvm;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@TopCommand
@CommandLine.Command(name = "run-parts", showEndOfOptionsDelimiterInUsageHelp = true)
public class RunParts implements Callable<Integer> {

    @CommandLine.Option(names = {"--test"})
    private boolean test;

    @CommandLine.Option(names = {"--list"})
    private boolean list;

    @CommandLine.Option(names = {"-v", "--verbose"})
    private boolean verbose;

    @CommandLine.Option(names = {"--report"})
    private boolean report;

    @CommandLine.Option(names = {"--reverse"})
    private boolean reverse;

    @CommandLine.Option(names = {"--exit-on-error"})
    private boolean exitOnError;

    @CommandLine.Option(names = {"--umask"}, defaultValue = "022")
    private int umask;

    @CommandLine.Option(names = {"--lsbsysinit"})
    private boolean lsbSysInit;

    @CommandLine.Option(names = {"--regex"}, arity = "1..1")
    private Pattern regex;

    @CommandLine.Option(names = {"-a", "--arg"}, arity = "1..*")
    private String[] args;

    @CommandLine.Option(names = {"-h", "--help"})
    private boolean help;

    @CommandLine.Parameters(paramLabel = "DIRECTORY", arity = "1..1")
    private File dir;

    @SuppressWarnings("java:S106")
    private void usage(String fmt, Object... o) {
        System.err.printf(fmt, o);
    }

    @Override
    public Integer call() throws Exception {
        if ((dir == null) || !dir.isDirectory() || !dir.canRead()) {
            usage("Not a directory: %s\n", dir);
            return CommandLine.ExitCode.USAGE;
        }
        final File[] files = getFiles();
        Arrays.stream(files)
            .filter(this::filter)
            .forEach(System.out::println);
        return CommandLine.ExitCode.OK;
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
        if (f.isDirectory() || !f.canExecute()) {
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

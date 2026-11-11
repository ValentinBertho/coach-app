package com.coachrun;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aucune date civile ne doit dépendre du fuseau de la machine.
 *
 * <p>En production (Railway), la JVM tourne en UTC. {@code LocalDate.now()} y renvoie donc la
 * veille entre minuit et 1 h (2 h l'été) à Paris : séance du jour, check-in ou bilan RGPD
 * changent de date selon l'heure de la requête. La date du jour se lit dans le fuseau de
 * l'application, via {@code ClockService}. Les tests tournent eux aussi en UTC et ne voient pas
 * ce décalage : ce garde-fou relit donc le code source.</p>
 */
class NoMachineClockTest {

    private static final Pattern MACHINE_CLOCK = Pattern.compile(
            "\\b(LocalDate|LocalDateTime|LocalTime|ZonedDateTime|OffsetDateTime)\\.now\\(\\)"
                    + "|ZoneId\\.systemDefault\\(\\)|TimeZone\\.getDefault\\(\\)");

    @Test
    void civilDatesAreReadInTheApplicationTimeZone() throws IOException {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .flatMap(NoMachineClockTest::offendingLines)
                    .toList();
        }
        assertThat(offenders)
                .as("Utiliser ClockService (today(), nowDateTime(), zone()) plutôt que l'horloge de la machine")
                .isEmpty();
    }

    private static boolean isComment(String line) {
        String t = line.trim();
        return t.startsWith("*") || t.startsWith("//") || t.startsWith("/*");
    }

    private static Stream<String> offendingLines(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            return java.util.stream.IntStream.range(0, lines.size())
                    .filter(i -> !isComment(lines.get(i)) && MACHINE_CLOCK.matcher(lines.get(i)).find())
                    .mapToObj(i -> file + ":" + (i + 1) + "  " + lines.get(i).trim());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}

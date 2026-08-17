package com.coachrun.service;

import com.coachrun.dto.response.TimelineResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Le bilan de période — le document que le coach remet à son athlète en fin de cycle.
 *
 * <h2>Pourquoi il manquait</h2>
 *
 * <p>Le produit savait exporter le <b>programme</b> — ce qui est prévu. Il ne savait rien exporter
 * de ce qui a eu lieu. Or c'est l'autre moitié du métier : à la fin d'un cycle, le coach n'a rien à
 * montrer de son travail, et l'athlète qui le paie n'a rien à garder. Un fichier PDF est une preuve
 * de travail d'une façon qu'aucun écran n'égale.</p>
 *
 * <h2>Ce qu'il contient, et pas plus</h2>
 *
 * <p>Le volume, la répartition d'intensité, la charge, et la frise des faits marquants — tests,
 * records, courses, coupures. Rien qui demande une saisie supplémentaire : tout est déjà en base.</p>
 *
 * <p><b>Aucune donnée de santé nominative détaillée.</b> Les blessures déclarées apparaissent dans
 * la frise à l'écran, où le coach est déjà autorisé à les lire ; elles sortent du PDF, qui circule
 * par e-mail et se retrouve dans des boîtes que personne ne maîtrise. C'est la même règle que pour
 * les bilans hebdomadaires.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeriodReportPdfService {

    private static final Locale FR = Locale.FRENCH;
    private static final Color BRAND = new Color(0x0E, 0x6E, 0x78);
    private static final Color INK = new Color(0x22, 0x2A, 0x33);
    private static final Color MUTED = new Color(0x6B, 0x72, 0x80);
    private static final Color RULE = new Color(0xE3, 0xE7, 0xEC);

    /** Les faits qu'un bilan raconte. La blessure n'en fait pas partie : cf. javadoc de classe. */
    private static final List<String> REPORTED_KINDS =
            List.of("TEST", "RECORD", "RACE", "UNAVAILABILITY", "STRENGTH_TEST");

    private final AthleteRepository athleteRepository;
    private final AthleteTimelineService timelineService;
    private final AnalyticsService analyticsService;
    private final AthleteLoadService loadService;

    public byte[] generate(UUID clubId, UUID athleteId, LocalDate from, LocalDate to) {
        Athlete athlete = athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 54, 48);
        PdfWriter.getInstance(doc, out);
        doc.open();

        writeHeader(doc, athlete, from, to);
        writeVolume(doc, clubId, athleteId, from, to);
        writeLoad(doc, clubId, athleteId);
        writeTimeline(doc, timelineService.forCoach(clubId, athleteId, from, to));

        doc.close();
        return out.toByteArray();
    }

    // ---------------------------------------------------------------------

    private void writeHeader(Document doc, Athlete athlete, LocalDate from, LocalDate to) {
        doc.add(new Paragraph(athlete.getClub().getName(),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BRAND)));

        Paragraph title = new Paragraph("Bilan de période",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, INK));
        title.setSpacingBefore(4);
        doc.add(title);

        Paragraph sub = new Paragraph(
                athlete.getFirstName() + " " + athlete.getLastName()
                        + "  ·  du " + fmt(from) + " au " + fmt(to),
                FontFactory.getFont(FontFactory.HELVETICA, 11, MUTED));
        sub.setSpacingAfter(18);
        doc.add(sub);
    }

    /**
     * Volume prévu / réalisé, semaine par semaine.
     *
     * <p>Les deux colonnes côte à côte, jamais l'une sans l'autre : « 320 km » ne dit rien, « 320
     * réalisés sur 340 prévus » dit tout.</p>
     */
    private void writeVolume(Document doc, UUID clubId, UUID athleteId, LocalDate from, LocalDate to) {
        int weeks = (int) Math.max(1, java.time.temporal.ChronoUnit.WEEKS.between(from, to) + 1);
        var analytics = analyticsService.compute(clubId, athleteId, Math.min(weeks, 26));

        section(doc, "Volume");

        double planned = analytics.weeklyVolume().stream().mapToDouble(w -> w.plannedKm()).sum();
        double realized = analytics.weeklyVolume().stream().mapToDouble(w -> w.realizedKm()).sum();
        doc.add(kv("Réalisé", String.format(FR, "%.0f km", realized)
                + String.format(FR, "  (prévu %.0f km)", planned)));

        PdfPTable table = new PdfPTable(new float[]{3f, 2f, 2f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(8);
        headerCell(table, "Semaine du");
        headerCell(table, "Prévu");
        headerCell(table, "Réalisé");
        for (var w : analytics.weeklyVolume()) {
            bodyCell(table, fmt(w.weekStart()), INK, Font.NORMAL);
            bodyCell(table, String.format(FR, "%.1f km", w.plannedKm()), MUTED, Font.NORMAL);
            bodyCell(table, String.format(FR, "%.1f km", w.realizedKm()), INK, Font.BOLD);
        }
        doc.add(table);
    }

    /** Charge et répartition d'intensité — les deux chiffres qui disent comment la période a été vécue. */
    private void writeLoad(Document doc, UUID clubId, UUID athleteId) {
        section(doc, "Charge");
        try {
            var load = loadService.load(clubId, athleteId);
            doc.add(kv("Charge chronique", Math.round(load.chronicLoad28d()) + " UA / semaine"));
            if (load.ratioReady() && load.ratio() != null) {
                doc.add(kv("Rapport aigu / chronique", String.valueOf(load.ratio()).replace('.', ',')));
            }
            var d = load.distribution28d();
            doc.add(kv("Répartition d'intensité",
                    String.format(FR, "%.0f %% facile · %.0f %% seuil · %.0f %% intense",
                            d.domain1Pct(), d.domain2Pct(), d.domain3Pct())));
        } catch (RuntimeException e) {
            doc.add(muted("Charge non calculable sur cette période."));
        }
    }

    /** La frise : ce qui s'est passé, dans l'ordre. */
    private void writeTimeline(Document doc, TimelineResponse timeline) {
        section(doc, "Faits marquants");
        var events = timeline.events().stream()
                .filter(e -> REPORTED_KINDS.contains(e.kind()))
                .toList();
        if (events.isEmpty()) {
            doc.add(muted("Aucun test, record, course ni coupure sur cette période."));
            return;
        }
        PdfPTable table = new PdfPTable(new float[]{2.6f, 1.8f, 5.6f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(8);
        headerCell(table, "Date");
        headerCell(table, "Nature");
        headerCell(table, "Détail");
        for (var e : events) {
            bodyCell(table, fmt(e.date()), INK, Font.NORMAL);
            bodyCell(table, kindLabel(e.kind()), BRAND, Font.BOLD);
            String label = e.detail() == null || e.detail().isBlank()
                    ? e.title() : e.title() + "  —  " + e.detail();
            bodyCell(table, label, INK, Font.NORMAL);
        }
        doc.add(table);

        Paragraph foot = new Paragraph("Généré le " + fmt(LocalDate.now()),
                FontFactory.getFont(FontFactory.HELVETICA, 9, MUTED));
        foot.setSpacingBefore(18);
        doc.add(foot);
    }

    // ---------------------------------------------------------------------

    private String kindLabel(String kind) {
        return switch (kind) {
            case "TEST" -> "Test";
            case "RECORD" -> "Record";
            case "RACE" -> "Course";
            case "UNAVAILABILITY" -> "Coupure";
            case "STRENGTH_TEST" -> "Test de force";
            default -> kind;
        };
    }

    private void section(Document doc, String label) {
        Paragraph p = new Paragraph(label.toUpperCase(FR),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BRAND));
        p.setSpacingBefore(20);
        p.setSpacingAfter(6);
        doc.add(p);
    }

    private Paragraph kv(String key, String value) {
        Paragraph p = new Paragraph();
        p.add(new Phrase(key + " : ", FontFactory.getFont(FontFactory.HELVETICA, 11, MUTED)));
        p.add(new Phrase(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, INK)));
        p.setSpacingAfter(2);
        return p;
    }

    private Paragraph muted(String text) {
        return new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 11, MUTED));
    }

    private void headerCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE)));
        cell.setBackgroundColor(BRAND);
        cell.setPadding(7);
        cell.setBorderColor(BRAND);
        table.addCell(cell);
    }

    private void bodyCell(PdfPTable table, String text, Color color, int style) {
        PdfPCell cell = new PdfPCell(new Phrase(text,
                FontFactory.getFont(FontFactory.HELVETICA, 10, style, color)));
        cell.setPadding(7);
        cell.setBorderColor(RULE);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private String fmt(LocalDate d) {
        return d.getDayOfWeek().getDisplayName(TextStyle.SHORT, FR) + " "
                + d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}

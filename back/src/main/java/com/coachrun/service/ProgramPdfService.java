package com.coachrun.service;

import com.coachrun.dto.response.ScheduledStrengthResponse;
import com.coachrun.dto.response.WorkoutResponse;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Export PDF du programme d'un athlète (séances course + force sur une période, cf. DARI Lab).
 * Génère un document A4 chronologique, prêt à remettre à l'athlète.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProgramPdfService {

    private static final Locale FR = Locale.FRENCH;
    private static final Color BRAND = new Color(0x1F, 0x6F, 0xEB);
    private static final Color INK = new Color(0x22, 0x2A, 0x33);
    private static final Color MUTED = new Color(0x6B, 0x72, 0x80);

    private final AthleteRepository athleteRepository;
    private final WorkoutService workoutService;
    private final StrengthScheduleService strengthScheduleService;

    /** Une entrée de programme normalisée (course ou force). */
    private record Item(LocalDate date, String kind, String title, String detail) {
    }

    public byte[] generate(UUID clubId, UUID athleteId, LocalDate from, LocalDate to) {
        Athlete athlete = athleteRepository.findByIdAndClubId(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));

        List<Item> items = new ArrayList<>();
        for (WorkoutResponse w : workoutService.calendar(clubId, athleteId, from, to)) {
            items.add(new Item(w.scheduledDate(), "Course", nz(w.title(), "Séance course"), courseDetail(w)));
        }
        for (ScheduledStrengthResponse s : strengthScheduleService.coachCalendar(clubId, athleteId, from, to)) {
            items.add(new Item(s.scheduledDate(), "Force", nz(s.title(), "Séance de force"), ""));
        }
        items.sort((a, b) -> a.date().compareTo(b.date()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 54, 48);
        PdfWriter.getInstance(doc, out);
        doc.open();
        writeHeader(doc, athlete, from, to);
        writeBody(doc, items);
        doc.close();
        return out.toByteArray();
    }

    private void writeHeader(Document doc, Athlete athlete, LocalDate from, LocalDate to) {
        Paragraph club = new Paragraph(athlete.getClub().getName(),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BRAND));
        doc.add(club);

        Paragraph title = new Paragraph("Programme d'entraînement",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, INK));
        title.setSpacingBefore(4);
        doc.add(title);

        Paragraph sub = new Paragraph(
                athlete.getFirstName() + " " + athlete.getLastName()
                        + "  ·  du " + fmt(from) + " au " + fmt(to),
                FontFactory.getFont(FontFactory.HELVETICA, 11, MUTED));
        sub.setSpacingAfter(16);
        doc.add(sub);
    }

    private void writeBody(Document doc, List<Item> items) {
        if (items.isEmpty()) {
            Paragraph empty = new Paragraph("Aucune séance planifiée sur cette période.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 12, MUTED));
            doc.add(empty);
            return;
        }

        PdfPTable table = new PdfPTable(new float[]{3.2f, 1.6f, 5.2f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        headerCell(table, "Date");
        headerCell(table, "Type");
        headerCell(table, "Séance");

        for (Item it : items) {
            bodyCell(table, fmt(it.date()), INK, Font.NORMAL);
            bodyCell(table, it.kind(), "Force".equals(it.kind()) ? new Color(0x9A, 0x3F, 0xB0) : BRAND, Font.BOLD);
            String label = it.detail().isBlank() ? it.title() : it.title() + "  —  " + it.detail();
            bodyCell(table, label, INK, Font.NORMAL);
        }
        doc.add(table);

        Paragraph foot = new Paragraph(
                "Généré le " + fmt(LocalDate.now()) + " · " + items.size() + " séance(s)",
                FontFactory.getFont(FontFactory.HELVETICA, 9, MUTED));
        foot.setSpacingBefore(18);
        doc.add(foot);
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
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA, 10, style, color)));
        cell.setPadding(7);
        cell.setBorderColor(new Color(0xE3, 0xE7, 0xEC));
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private String courseDetail(WorkoutResponse w) {
        List<String> parts = new ArrayList<>();
        if (w.targetDistanceM() != null && w.targetDistanceM() > 0) {
            parts.add(String.format(FR, "%.1f km", w.targetDistanceM() / 1000.0));
        }
        if (w.targetDurationS() != null && w.targetDurationS() > 0) {
            parts.add(w.targetDurationS() / 60 + " min");
        }
        return String.join(" · ", parts);
    }

    private String fmt(LocalDate d) {
        String day = d.getDayOfWeek().getDisplayName(TextStyle.SHORT, FR);
        return day + " " + d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private String nz(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }
}

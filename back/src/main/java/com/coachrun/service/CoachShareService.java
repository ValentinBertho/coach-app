package com.coachrun.service;

import com.coachrun.dto.response.PublicCoachDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ce qu'un lien de coach montre quand on le colle dans WhatsApp, un SMS ou Instagram.
 *
 * <h2>Pourquoi le serveur s'en charge</h2>
 *
 * <p>La page {@code /coachs/<slug>} pose bien ses balises d'aperçu, mais depuis Angular, dans le
 * navigateur. Les robots de prévisualisation (WhatsApp, iMessage, LinkedIn, Facebook) ne
 * l'exécutent pas : ils lisent le {@code index.html} brut, et tout lien de coach partagé
 * s'affichait comme la page d'accueil. Le lien court {@code /c/<slug>} est donc servi ici : une
 * page minuscule qui porte les balises du coach, puis renvoie le visiteur humain vers sa fiche.</p>
 *
 * <h2>L'image de partage</h2>
 *
 * <p>Dessinée à la volée plutôt que stockée : elle suit la fiche (photo, titre, spécialités) sans
 * qu'aucun geste ne l'invalide, et son coût — quelques millisecondes — est absorbé par le cache
 * des robots, qui ne la redemandent pas à chaque affichage.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoachShareService {

    /** Format des aperçus de liens (Open Graph). */
    public static final int CARD_W = 1200;
    public static final int CARD_H = 630;
    /** Format story (Instagram, WhatsApp) : à télécharger et publier tel quel. */
    public static final int STORY_W = 1080;
    public static final int STORY_H = 1920;

    private static final Color PRIMARY = new Color(0x0e6e78);
    private static final Color PRIMARY_DARK = new Color(0x0a4f58);
    private static final Color WASH = new Color(0xc5e6e8);

    private final CoachDirectoryService directory;
    private final CoachPhotoService photoService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /** Adresse courte et partageable d'une fiche. */
    public String shortLink(String slug) {
        return base() + "/c/" + slug;
    }

    /**
     * La page d'aperçu : les balises du coach, et un renvoi immédiat vers sa fiche.
     *
     * <p>Le renvoi passe par {@code meta refresh}, pas par un script : la politique de sécurité du
     * site n'autorise aucun script en ligne, et les robots ignorent l'un comme l'autre.</p>
     */
    public String previewHtml(String slug) {
        PublicCoachDetailResponse c = directory.bySlug(slug);
        String target = base() + "/coachs/" + c.slug();
        String title = c.name() + " · Coach sur DARI Lab";
        String description = description(c);
        String image = base() + "/api/public/share/coach/" + c.slug() + "/card.png";
        return """
                <!doctype html>
                <html lang="fr">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%1$s</title>
                <meta name="description" content="%2$s">
                <meta property="og:type" content="profile">
                <meta property="og:site_name" content="DARI Lab">
                <meta property="og:locale" content="fr_FR">
                <meta property="og:title" content="%1$s">
                <meta property="og:description" content="%2$s">
                <meta property="og:url" content="%3$s">
                <meta property="og:image" content="%4$s">
                <meta property="og:image:width" content="%5$d">
                <meta property="og:image:height" content="%6$d">
                <meta property="og:image:alt" content="%7$s">
                <meta name="twitter:card" content="summary_large_image">
                <meta name="twitter:title" content="%1$s">
                <meta name="twitter:description" content="%2$s">
                <meta name="twitter:image" content="%4$s">
                <link rel="canonical" href="%8$s">
                <meta http-equiv="refresh" content="0; url=%8$s">
                </head>
                <body>
                <p><a href="%8$s">Voir la fiche de %7$s sur DARI Lab</a></p>
                </body>
                </html>
                """.formatted(esc(title), esc(description), esc(shortLink(c.slug())), esc(image),
                CARD_W, CARD_H, esc(c.name()), esc(target));
    }

    /** Une ligne lisible : titre, puis ville et spécialités, ce qu'un athlète cherche d'abord. */
    static String description(PublicCoachDetailResponse c) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(c.headline())) {
            parts.add(c.headline().trim());
        }
        if (StringUtils.hasText(c.city())) {
            parts.add(c.city().trim());
        }
        if (c.specialtyLabels() != null && !c.specialtyLabels().isEmpty()) {
            parts.add(String.join(", ", c.specialtyLabels().subList(0, Math.min(3, c.specialtyLabels().size()))));
        }
        String text = parts.isEmpty() ? "Coach course à pied et préparation physique." : String.join(" · ", parts);
        return text + " Demandez à être coaché sur DARI Lab.";
    }

    /** L'image d'aperçu, 1200 × 630. */
    public byte[] card(String slug) {
        PublicCoachDetailResponse c = directory.bySlug(slug);
        BufferedImage img = new BufferedImage(CARD_W, CARD_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(img);
        try {
            g.setPaint(new GradientPaint(0, 0, PRIMARY, CARD_W, CARD_H, PRIMARY_DARK));
            g.fillRect(0, 0, CARD_W, CARD_H);
            drawRings(g, CARD_W - 140, CARD_H - 60, 9);

            int photoSize = 300;
            int px = 90;
            int py = (CARD_H - photoSize) / 2;
            drawPhoto(g, c, px, py, photoSize);

            int tx = px + photoSize + 70;
            int maxW = CARD_W - tx - 70;
            g.setColor(WASH);
            g.setFont(font(Font.BOLD, 26));
            g.drawString("COACH SUR DARI LAB", tx, 175);

            g.setColor(Color.WHITE);
            int y = drawWrapped(g, c.name(), font(Font.BOLD, 64), tx, 255, maxW, 2, 72);
            if (StringUtils.hasText(c.headline())) {
                g.setColor(new Color(255, 255, 255, 225));
                y = drawWrapped(g, c.headline(), font(Font.PLAIN, 32), tx, y + 56, maxW, 2, 42);
            }
            drawChips(g, chips(c), tx, y + 30, maxW);

            g.setColor(new Color(255, 255, 255, 200));
            g.setFont(font(Font.PLAIN, 24));
            g.drawString(host() + "/c/" + c.slug(), tx, CARD_H - 60);
        } finally {
            g.dispose();
        }
        return png(img);
    }

    /** Le format story, 1080 × 1920, à publier tel quel. */
    public byte[] story(String slug) {
        PublicCoachDetailResponse c = directory.bySlug(slug);
        BufferedImage img = new BufferedImage(STORY_W, STORY_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(img);
        try {
            g.setPaint(new GradientPaint(0, 0, PRIMARY, 0, STORY_H, PRIMARY_DARK));
            g.fillRect(0, 0, STORY_W, STORY_H);
            drawRings(g, STORY_W / 2, STORY_H - 200, 12);

            int photoSize = 520;
            drawPhoto(g, c, (STORY_W - photoSize) / 2, 330, photoSize);

            g.setColor(WASH);
            g.setFont(font(Font.BOLD, 36));
            centered(g, "COACH SUR DARI LAB", 230);

            g.setColor(Color.WHITE);
            int y = drawCentered(g, c.name(), font(Font.BOLD, 84), 1000, STORY_W - 160, 2, 96);
            if (StringUtils.hasText(c.headline())) {
                g.setColor(new Color(255, 255, 255, 225));
                y = drawCentered(g, c.headline(), font(Font.PLAIN, 44), y + 80, STORY_W - 180, 3, 58);
            }
            List<String> chips = chips(c);
            if (!chips.isEmpty()) {
                g.setFont(font(Font.PLAIN, 38));
                g.setColor(WASH);
                centered(g, String.join(" · ", chips), y + 80);
            }

            g.setColor(Color.WHITE);
            g.setFont(font(Font.BOLD, 46));
            centered(g, "Demandez à être coaché", STORY_H - 330);
            g.setFont(font(Font.PLAIN, 40));
            g.setColor(new Color(255, 255, 255, 220));
            centered(g, host() + "/c/" + c.slug(), STORY_H - 260);
        } finally {
            g.dispose();
        }
        return png(img);
    }

    // ---------------------------------------------------------------- dessin

    private static List<String> chips(PublicCoachDetailResponse c) {
        List<String> chips = new ArrayList<>();
        if (StringUtils.hasText(c.city())) {
            chips.add(c.city().trim());
        }
        if (c.specialtyLabels() != null) {
            c.specialtyLabels().stream().limit(2).forEach(chips::add);
        }
        if (c.experienceYears() != null && c.experienceYears() > 0) {
            chips.add(c.experienceYears() + " ans d'expérience");
        }
        return chips;
    }

    private static Graphics2D graphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        return g;
    }

    /** Les cercles concentriques de la marque, en filigrane : une piste vue d'en haut. */
    private static void drawRings(Graphics2D g, int cx, int cy, int count) {
        g.setStroke(new BasicStroke(2f));
        for (int i = 1; i <= count; i++) {
            g.setColor(new Color(255, 255, 255, Math.max(8, 40 - i * 3)));
            int r = i * 70;
            g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2.0, r * 2.0));
        }
    }

    /** La photo en médaillon ; à défaut, les initiales sur un disque clair. */
    private void drawPhoto(Graphics2D g, PublicCoachDetailResponse c, int x, int y, int size) {
        BufferedImage photo = loadPhoto(c);
        Ellipse2D clip = new Ellipse2D.Double(x, y, size, size);
        g.setColor(new Color(255, 255, 255, 60));
        g.fill(new Ellipse2D.Double(x - 10, y - 10, size + 20.0, size + 20.0));
        if (photo != null) {
            java.awt.Shape old = g.getClip();
            g.setClip(clip);
            double scale = Math.max((double) size / photo.getWidth(), (double) size / photo.getHeight());
            int w = (int) Math.round(photo.getWidth() * scale);
            int h = (int) Math.round(photo.getHeight() * scale);
            g.drawImage(photo, x + (size - w) / 2, y + (size - h) / 2, w, h, null);
            g.setClip(old);
        } else {
            g.setColor(WASH);
            g.fill(clip);
            g.setColor(PRIMARY_DARK);
            g.setFont(font(Font.BOLD, size / 3));
            String initials = initials(c.name());
            FontMetrics fm = g.getFontMetrics();
            g.drawString(initials, x + (size - fm.stringWidth(initials)) / 2,
                    y + (size - fm.getHeight()) / 2 + fm.getAscent());
        }
    }

    private BufferedImage loadPhoto(PublicCoachDetailResponse c) {
        if (!StringUtils.hasText(c.photoUrl())) {
            return null;
        }
        try {
            UUID id = UUID.fromString(c.photoUrl().substring(c.photoUrl().lastIndexOf('/') + 1));
            return ImageIO.read(new ByteArrayInputStream(photoService.require(id).getData()));
        } catch (IOException | RuntimeException e) {
            // Une photo illisible ne doit pas priver le coach de son aperçu : les initiales suffisent.
            log.warn("Photo illisible pour l'aperçu de {} : {}", c.slug(), e.getMessage());
            return null;
        }
    }

    static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (!part.isEmpty() && sb.length() < 2) {
                sb.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return sb.toString();
    }

    private static void drawChips(Graphics2D g, List<String> chips, int x, int y, int maxW) {
        g.setFont(font(Font.PLAIN, 24));
        FontMetrics fm = g.getFontMetrics();
        int cx = x;
        for (String chip : chips) {
            int w = fm.stringWidth(chip) + 36;
            if (cx + w > x + maxW) {
                break;
            }
            g.setColor(new Color(255, 255, 255, 40));
            g.fill(new RoundRectangle2D.Double(cx, y, w, 44, 44, 44));
            g.setColor(Color.WHITE);
            g.drawString(chip, cx + 18, y + 30);
            cx += w + 12;
        }
    }

    /** Écrit un texte sur au plus {@code maxLines} lignes, et rend la ligne de base de la dernière. */
    private static int drawWrapped(Graphics2D g, String text, Font font, int x, int y, int maxW,
                                   int maxLines, int lineHeight) {
        g.setFont(font);
        List<String> lines = wrap(g.getFontMetrics(), text, maxW, maxLines);
        for (String line : lines) {
            g.drawString(line, x, y);
            y += lineHeight;
        }
        return y - lineHeight;
    }

    private static int drawCentered(Graphics2D g, String text, Font font, int y, int maxW,
                                    int maxLines, int lineHeight) {
        g.setFont(font);
        List<String> lines = wrap(g.getFontMetrics(), text, maxW, maxLines);
        for (String line : lines) {
            centered(g, line, y);
            y += lineHeight;
        }
        return y - lineHeight;
    }

    private static void centered(Graphics2D g, String text, int y) {
        int w = g.getFontMetrics().stringWidth(text);
        g.drawString(text, (g.getClipBounds() == null ? STORY_W : g.getClipBounds().width) / 2 - w / 2, y);
    }

    static List<String> wrap(FontMetrics fm, String text, int maxW, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(candidate) <= maxW || line.isEmpty()) {
                line = new StringBuilder(candidate);
            } else {
                lines.add(line.toString());
                line = new StringBuilder(word);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        if (lines.size() > maxLines) {
            List<String> kept = new ArrayList<>(lines.subList(0, maxLines));
            String last = kept.get(maxLines - 1);
            while (!last.isEmpty() && fm.stringWidth(last + "…") > maxW) {
                last = last.substring(0, last.length() - 1);
            }
            kept.set(maxLines - 1, last.stripTrailing() + "…");
            return kept;
        }
        return lines;
    }

    private static Font font(int style, int size) {
        return new Font(Font.SANS_SERIF, style, size);
    }

    private static byte[] png(BufferedImage img) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String base() {
        return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    /** Le domaine sans le protocole, tel qu'on l'écrit sur une affiche. */
    private String host() {
        return base().replaceFirst("^https?://", "");
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s == null ? "" : s, "UTF-8");
    }
}

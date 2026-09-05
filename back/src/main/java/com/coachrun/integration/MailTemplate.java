package com.coachrun.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Gabarit transactionnel unique : enveloppe un fragment de corps dans un e-mail complet
 * (doctype, table 600 px centrée, en-tête avec le logo, pied de page avec l'éditeur et le lien de
 * désinscription) et en dérive automatiquement la version texte.
 *
 * <p>Tout est en styles inline et en tables : Outlook et Gmail ignorent une bonne partie du CSS
 * moderne. Les couleurs reprennent les tokens de {@code front/src/styles.scss}, la largeur ne
 * dépasse jamais 600 px et le bouton fait au moins 44 px de haut (cible tactile).</p>
 *
 * <p>La version texte est <strong>dérivée du HTML</strong> plutôt que rédigée à part : deux
 * rédactions divergent toujours, et un e-mail sans version texte tombe plus facilement en spam.</p>
 */
@Component
public class MailTemplate {

    // Tokens repris de front/src/styles.scss (thème clair).
    private static final String PRIMARY = "#0e6e78";
    private static final String INK = "#18191c";
    private static final String INK_2 = "#44464d";
    private static final String INK_3 = "#74767f";
    private static final String PAPER = "#fffefb";
    private static final String PAGE_BG = "#f2efe7";
    private static final String HAIRLINE = "#e3ddd0";

    /** Destinataire : détermine où pointe le lien de gestion des notifications. */
    public enum Audience {
        COACH("/app/notifications"),
        ATHLETE("/athlete/profile"),
        /** L'équipe plateforme. Ses réglages vivent dans le back-office, pas dans le cockpit. */
        ADMIN("/admin");

        private final String preferencesPath;

        Audience(String preferencesPath) {
            this.preferencesPath = preferencesPath;
        }
    }

    /** E-mail rendu : version HTML et version texte, toujours cohérentes entre elles. */
    public record Rendered(String html, String text) {
    }

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.mail.publisher:Darilab}")
    private String publisher;

    @Value("${app.mail.reply-to:}")
    private String replyTo;

    /** Adresse de réponse des e-mails transactionnels, ou {@code null} si non configurée. */
    public String replyTo() {
        return replyTo == null || replyTo.isBlank() ? null : replyTo;
    }

    /**
     * Valeur de l'en-tête {@code List-Unsubscribe} : le lien de préférences, plus une adresse de
     * repli si {@code app.mail.reply-to} est configurée.
     */
    public String listUnsubscribe(Audience audience) {
        String url = "<" + frontendUrl + audience.preferencesPath + ">";
        return replyTo() == null ? url : url + ", <mailto:" + replyTo() + "?subject=Desabonnement>";
    }

    /**
     * Bouton d'action : table imbriquée (le seul rendu fiable sur Outlook), hauteur ≥ 44 px.
     * Remplace les {@code <p><a href>…</a></p>} nus des anciens envois.
     */
    public String button(String label, String url) {
        return """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" \
                style="margin:24px 0;"><tr><td align="center" bgcolor="%s" \
                style="border-radius:10px;">\
                <a href="%s" style="display:inline-block;padding:14px 28px;min-height:16px;\
                line-height:20px;font-family:Helvetica,Arial,sans-serif;font-size:16px;\
                font-weight:700;color:#ffffff;text-decoration:none;border-radius:10px;">%s</a>\
                </td></tr></table>"""
                .formatted(PRIMARY, url, esc(label));
    }

    /**
     * Enveloppe un fragment de corps dans le gabarit complet.
     *
     * @param bodyHtml fragment HTML (paragraphes, listes, bouton) — déjà échappé par l'appelant
     */
    public Rendered render(String subject, String bodyHtml, Audience audience) {
        String preferencesUrl = frontendUrl + audience.preferencesPath;
        String html = """
                <!doctype html>
                <html lang="fr"><head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <meta name="color-scheme" content="light">
                <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background-color:%s;">
                <div style="display:none;max-height:0;overflow:hidden;opacity:0;">%s</div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"
                       style="background-color:%s;padding:24px 12px;">
                  <tr><td align="center">
                    <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0"
                           style="width:100%%;max-width:600px;background-color:%s;border:1px solid %s;
                                  border-radius:14px;overflow:hidden;">
                      <tr><td style="padding:24px 32px;border-bottom:1px solid %s;">
                        <img src="%s/apple-touch-icon.png" width="36" height="36" alt=""
                             style="vertical-align:middle;border-radius:9px;border:0;">
                        <span style="vertical-align:middle;margin-left:10px;
                                     font-family:Helvetica,Arial,sans-serif;font-size:20px;
                                     font-weight:700;color:%s;letter-spacing:-0.02em;">Dari<span
                              style="color:%s;">lab</span></span>
                      </td></tr>
                      <tr><td style="padding:32px;font-family:Helvetica,Arial,sans-serif;
                                     font-size:16px;line-height:24px;color:%s;">%s</td></tr>
                      <tr><td style="padding:20px 32px;border-top:1px solid %s;
                                     font-family:Helvetica,Arial,sans-serif;font-size:12px;
                                     line-height:18px;color:%s;">
                        <p style="margin:0 0 6px;">%s — plateforme de coaching en course à pied.</p>
                        <p style="margin:0;">Vous recevez cet e-mail parce que vous avez un compte Darilab.
                          <a href="%s" style="color:%s;">Gérer mes notifications</a>.</p>
                      </td></tr>
                    </table>
                  </td></tr>
                </table>
                </body></html>"""
                .formatted(esc(subject), PAGE_BG, esc(subject), PAGE_BG, PAPER, HAIRLINE, HAIRLINE,
                        frontendUrl, INK, PRIMARY, INK_2, bodyHtml, HAIRLINE, INK_3,
                        esc(publisher), preferencesUrl, PRIMARY);

        String text = toText(bodyHtml)
                + "\n\n--\n" + publisher + " — plateforme de coaching en course à pied.\n"
                + "Gérer mes notifications : " + preferencesUrl + "\n";
        return new Rendered(html, text);
    }

    /**
     * Version texte dérivée du fragment HTML : les liens deviennent « libellé : URL », les listes
     * des tirets, et les balises restantes disparaissent.
     */
    static String toText(String html) {
        String s = html
                .replaceAll("(?is)<table.*?<a href=\"([^\"]*)\"[^>]*>(.*?)</a>.*?</table>", "\n$2 : $1\n")
                .replaceAll("(?is)<a href=\"([^\"]*)\"[^>]*>(.*?)</a>", "$2 : $1")
                .replaceAll("(?i)<li[^>]*>", "\n- ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>|</ul>|</ol>|</div>", "\n")
                .replaceAll("<[^>]+>", "");
        s = HtmlUtils.htmlUnescape(s);
        // Espaces en fin de ligne et lignes vides multiples : un texte propre, pas un HTML dénudé.
        s = s.replaceAll("[ \\t]+\n", "\n").replaceAll("\n{3,}", "\n\n");
        return s.strip();
    }

    /** Échappe le HTML en gardant les accents littéraux (le document est déclaré en UTF-8). */
    private String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value, "UTF-8");
    }
}

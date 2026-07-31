package com.coachrun;

import com.coachrun.integration.MailTemplate;
import com.coachrun.integration.MailTemplate.Audience;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gabarit transactionnel : un e-mail complet (et non un fragment), avec sa version texte,
 * son en-tête de désinscription et un bouton cliquable au pouce.
 */
class MailTemplateTest {

    private MailTemplate template;

    @BeforeEach
    void setUp() {
        template = new MailTemplate();
        ReflectionTestUtils.setField(template, "frontendUrl", "https://www.darilab.app");
        ReflectionTestUtils.setField(template, "publisher", "Darilab");
        ReflectionTestUtils.setField(template, "replyTo", "contact@darilab.app");
    }

    @Test
    void rendersACompleteDocumentBoundedTo600px() {
        String body = "<p>Bonjour Marie,</p>" + template.button("Voir ma séance", "https://x.test/s");

        MailTemplate.Rendered mail = template.render("Nouvelle séance", body, Audience.ATHLETE);

        assertThat(mail.html()).startsWith("<!doctype html>");
        assertThat(mail.html()).contains("<title>Nouvelle séance</title>");
        // Mise en page : table centrée de 600 px maximum, en-tête avec le logo, pied de page.
        assertThat(mail.html()).contains("max-width:600px");
        assertThat(mail.html()).contains("/apple-touch-icon.png");
        assertThat(mail.html()).contains("Darilab — plateforme de coaching");
        assertThat(mail.html()).contains("Gérer mes notifications");
    }

    @Test
    void buttonIsAtLeast44pxTall() {
        String button = template.button("Voir ma séance", "https://x.test/s");

        // 14 px de padding haut et bas + 20 px de hauteur de ligne = 48 px.
        assertThat(button).contains("padding:14px 28px").contains("line-height:20px");
        assertThat(button).contains("href=\"https://x.test/s\"");
        // Styles inline uniquement : les classes CSS ne survivent pas à Outlook.
        assertThat(button).doesNotContain("class=");
    }

    @Test
    void derivesAPlainTextVersionWithTheLinkSpelledOut() {
        String body = "<p>Bonjour Marie,</p><p>Votre coach a planifié <strong>Séance seuil</strong>.</p>"
                + template.button("Voir ma séance", "https://x.test/s");

        MailTemplate.Rendered mail = template.render("Nouvelle séance", body, Audience.ATHLETE);

        assertThat(mail.text()).contains("Bonjour Marie,");
        assertThat(mail.text()).contains("Votre coach a planifié Séance seuil.");
        // Un lecteur en texte seul doit pouvoir suivre le lien.
        assertThat(mail.text()).contains("Voir ma séance : https://x.test/s");
        assertThat(mail.text()).doesNotContain("<").doesNotContain("&nbsp;");
        assertThat(mail.text()).contains("Gérer mes notifications : https://www.darilab.app/athlete/profile");
    }

    @Test
    void listUnsubscribePointsAtThePreferencesOfTheRightAudience() {
        assertThat(template.listUnsubscribe(Audience.COACH))
                .contains("<https://www.darilab.app/app/notifications>")
                .contains("mailto:contact@darilab.app");
        assertThat(template.listUnsubscribe(Audience.ATHLETE))
                .contains("<https://www.darilab.app/athlete/profile>");
    }

    @Test
    void replyToIsNullWhenNotConfigured() {
        ReflectionTestUtils.setField(template, "replyTo", "");
        assertThat(template.replyTo()).isNull();
        // Sans adresse de réponse, l'en-tête se réduit au lien de préférences.
        assertThat(template.listUnsubscribe(Audience.COACH)).doesNotContain("mailto:");
    }

    @Test
    void escapesUserSuppliedValuesInTheSubject() {
        MailTemplate.Rendered mail = template.render(
                "<script>alert(1)</script>", "<p>Bonjour,</p>", Audience.COACH);

        assertThat(mail.html()).doesNotContain("<script>");
        assertThat(mail.html()).contains("&lt;script&gt;");
    }
}

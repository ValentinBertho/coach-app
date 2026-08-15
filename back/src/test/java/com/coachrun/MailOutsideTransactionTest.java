package com.coachrun;

import com.coachrun.integration.MailTemplate;
import com.coachrun.integration.ResendMailClient;
import com.coachrun.service.MailLogService;
import com.coachrun.service.NotificationService;
import com.coachrun.service.PushNotificationService;
import com.coachrun.repository.CoachAthleteRelationRepository;
import com.coachrun.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * L'envoi d'e-mail ne doit plus partir <b>pendant</b> une transaction : l'appel HTTP y retenait une
 * connexion Hikari (pool de 10) pour toute sa durée — quelques envois lents suffisaient à assécher
 * le pool et à figer l'API. Bénéfice secondaire : une transaction qui échoue n'annonce plus par
 * e-mail une action qui n'a pas eu lieu.
 *
 * <p>L'envoi est donc reporté après le commit quand une synchronisation de transaction est active,
 * et reste immédiat sinon.</p>
 */
@ExtendWith(MockitoExtension.class)
class MailOutsideTransactionTest {

    @Mock private ResendMailClient mailClient;
    @Spy private MailTemplate mailTemplate = new MailTemplate();
    @Mock private UserRepository userRepository;
    @Mock private PushNotificationService pushService;
    @Mock private CoachAthleteRelationRepository relationRepository;
    /** Journal des envois : le report après commit ne le concerne pas, il est simulé. */
    @Mock private MailLogService mailLog;
    @InjectMocks private NotificationService notificationService;

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void mailEnabled() {
        ReflectionTestUtils.setField(notificationService, "enabled", true);
    }

    /** Transaction en cours : rien ne part tant qu'elle n'est pas validée. */
    @Test
    void nothingIsSentWhileTheTransactionIsOpen() {
        mailEnabled();
        TransactionSynchronizationManager.initSynchronization();

        notificationService.notifyEmailVerification("tx@darilab.app", "Tx Coach",
                "https://www.darilab.app/verify-email/tok");

        verify(mailClient, never()).send(anyString(), anyString(), anyString(), any(), any(), any());
    }

    /** Au commit, l'envoi a bien lieu : le report ne perd rien. */
    @Test
    void mailIsSentOnCommit() {
        mailEnabled();
        TransactionSynchronizationManager.initSynchronization();

        notificationService.notifyEmailVerification("tx@darilab.app", "Tx Coach",
                "https://www.darilab.app/verify-email/tok");
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());

        verify(mailClient).send(anyString(), anyString(), anyString(), any(), any(), any());
    }

    /** Hors transaction, l'envoi reste immédiat. */
    @Test
    void mailIsSentImmediatelyWithoutTransaction() {
        mailEnabled();

        notificationService.notifyEmailVerification("hors.tx@darilab.app", "Hors Tx",
                "https://www.darilab.app/verify-email/tok");

        verify(mailClient).send(anyString(), anyString(), anyString(), any(), any(), any());
    }
}

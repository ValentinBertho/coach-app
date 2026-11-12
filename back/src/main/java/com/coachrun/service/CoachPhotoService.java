package com.coachrun.service;

import com.coachrun.entity.CoachPhoto;
import com.coachrun.entity.CoachProfile;
import com.coachrun.exception.ApiException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.CoachPhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * La photo d'une fiche coach : réception, normalisation, service.
 *
 * <h2>L'image stockée n'est jamais celle qui a été envoyée</h2>
 *
 * <p>Tout fichier reçu est décodé, redimensionné et ré-encodé en JPEG. Ce n'est pas une coquetterie
 * d'affichage, c'est ce qui règle trois problèmes d'un coup :</p>
 *
 * <ul>
 *   <li><b>Les métadonnées.</b> Une photo prise au téléphone porte ses coordonnées GPS — souvent
 *       celles du domicile. Publier le fichier tel quel dans un annuaire ouvert reviendrait à
 *       publier l'adresse du coach sans le lui dire. Le ré-encodage ne recopie aucune métadonnée.</li>
 *   <li><b>Les fichiers qui prétendent être des images.</b> Un {@code Content-Type} est déclaratif :
 *       il se choisit côté client. Un fichier qui ne se décode pas n'est pas une image, et la
 *       question du contenu réel ne se pose plus.</li>
 *   <li><b>Le poids.</b> Une photo d'appareil moderne pèse plusieurs mégaoctets pour être affichée
 *       en 128 pixels de côté. Servie telle quelle à chaque vignette de l'annuaire, elle ferait
 *       payer aux athlètes une bande passante qui n'apporte rien.</li>
 * </ul>
 *
 * <h2>La borne avant décodage</h2>
 *
 * <p>Le plafond de taille du fichier ne suffit pas : une image très compressée peut peser deux
 * mégaoctets et se déplier en plusieurs gigaoctets de bitmap — c'est le principe d'une bombe de
 * décompression, et elle emporterait le serveur avant qu'aucun contrôle applicatif ne s'exécute.
 * Les dimensions sont donc lues dans l'en-tête, <b>sans décoder</b>, et refusées avant.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoachPhotoService {

    /** Ce qu'un navigateur sait produire depuis un sélecteur de fichier, et rien d'autre. */
    private static final Set<String> ACCEPTED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");

    /** 5 Mo : large pour une photo de portrait, étroit pour un envoi qui n'en est pas une. */
    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;

    /** Borne lue dans l'en-tête, avant tout décodage (cf. bombe de décompression). */
    private static final int MAX_SOURCE_SIDE = 8000;

    /** Côté maximal de l'image conservée. Une fiche affiche un portrait, pas une affiche. */
    private static final int MAX_STORED_SIDE = 512;

    private final CoachPhotoRepository photoRepository;

    /**
     * Remplace la photo de la fiche.
     *
     * <p>Remplacement et non ajout : une fiche a une photo. L'ancienne ligne est supprimée, ce qui
     * change l'identifiant — et c'est voulu, puisque c'est lui qui compose l'URL. Une photo
     * remplacée obtient donc une nouvelle adresse, et les caches n'ont rien à invalider.</p>
     */
    @Transactional
    public CoachPhoto replace(CoachProfile profile, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Aucun fichier reçu.");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "L'image ne doit pas dépasser 5 Mo. La vôtre en fait "
                            + (file.getSize() / (1024 * 1024)) + ".");
        }
        String declared = file.getContentType();
        if (declared == null || !ACCEPTED_TYPES.contains(declared.toLowerCase(java.util.Locale.ROOT))) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Formats acceptés : JPEG, PNG, WebP ou HEIC.");
        }

        byte[] source;
        try {
            source = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Le fichier n'a pas pu être lu.");
        }

        BufferedImage normalized = normalize(source);
        byte[] jpeg = toJpeg(normalized);

        photoRepository.deleteByProfileId(profile.getId());
        // Vidé maintenant : sans cela, la suppression et l'insertion partiraient dans le même
        // flush, dans un ordre que rien ne garantit, et la contrainte d'unicité sur le profil
        // refuserait l'insertion.
        photoRepository.flush();

        CoachPhoto photo = new CoachPhoto();
        photo.setProfile(profile);
        photo.setContentType("image/jpeg");
        photo.setWidth(normalized.getWidth());
        photo.setHeight(normalized.getHeight());
        photo.setData(jpeg);
        CoachPhoto saved = photoRepository.save(photo);
        log.info("Photo de fiche coach {} remplacée ({}×{}, {} ko)",
                profile.getId(), saved.getWidth(), saved.getHeight(), jpeg.length / 1024);
        return saved;
    }

    @Transactional
    public void delete(CoachProfile profile) {
        photoRepository.deleteByProfileId(profile.getId());
    }

    @Transactional(readOnly = true)
    public CoachPhoto require(UUID photoId) {
        return photoRepository.findById(photoId)
                .orElseThrow(() -> new NotFoundException("Photo introuvable."));
    }

    // ------------------------------------------------------------------ normalisation

    /**
     * Décode l'image après avoir vérifié ses dimensions dans l'en-tête, puis la réduit.
     *
     * <p>L'ordre compte : lire les dimensions <b>sans</b> décoder est la seule protection contre une
     * image de deux mégaoctets qui se déplierait en plusieurs gigaoctets en mémoire.</p>
     */
    private BufferedImage normalize(byte[] source) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (in == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Ce fichier n'est pas une image.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                // Cas courant et légitime : un iPhone envoie du HEIC, que Java ne sait pas lire.
                // Le message doit dire quoi faire, pas seulement que ça a échoué.
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Ce format d'image n'est pas pris en charge par le serveur. "
                                + "Enregistrez la photo en JPEG ou en PNG et réessayez.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > MAX_SOURCE_SIDE || height > MAX_SOURCE_SIDE) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "Image trop grande (" + width + "×" + height + " pixels). "
                                    + "Réduisez-la à " + MAX_SOURCE_SIDE + " pixels de côté au plus.");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Ce fichier n'est pas une image.");
                }
                return scaleDown(decoded);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ce fichier n'est pas une image lisible.");
        }
    }

    /** Réduit l'image à {@link #MAX_STORED_SIDE} de côté, en gardant ses proportions. */
    private BufferedImage scaleDown(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        double factor = Math.min(1.0, (double) MAX_STORED_SIDE / Math.max(w, h));
        int tw = Math.max(1, (int) Math.round(w * factor));
        int th = Math.max(1, (int) Math.round(h * factor));

        // TYPE_INT_RGB et non ARGB : la sortie est un JPEG, qui n'a pas de canal alpha. Sans cette
        // conversion, un PNG transparent ressortait avec un fond noir.
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, tw, th);
            g.drawImage(src, 0, 0, tw, th, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private byte[] toJpeg(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, "jpg", out)) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "L'image n'a pas pu être convertie.");
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "L'image n'a pas pu être convertie.");
        }
        return out.toByteArray();
    }
}

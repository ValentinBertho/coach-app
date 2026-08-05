package com.coachrun;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Encodeur FIT minimal, réservé aux tests.
 *
 * <p>Un fichier de montre est un binaire : le committer comme fixture rendrait les tests
 * illisibles et impossibles à modifier. On l'écrit donc ici, octet par octet, selon la même
 * spécification que celle que le décodeur lit — en-tête, messages de définition, messages de
 * données. Un test qui échoue désigne alors une ligne de code, pas un blob.</p>
 */
final class FitFileBuilder {

    static final int TYPE_ENUM = 0x00;
    static final int TYPE_U8 = 0x02;
    static final int TYPE_U16 = 0x84;
    static final int TYPE_S32 = 0x85;
    static final int TYPE_U32 = 0x86;

    /** Époque FIT : 31/12/1989 00:00:00 UTC. */
    static final long FIT_EPOCH_S = 631_065_600L;

    /** Valeurs « non mesuré » réservées par le format. */
    static final long INVALID_U8 = 0xFF;
    static final long INVALID_U16 = 0xFFFF;

    private final ByteArrayOutputStream body = new ByteArrayOutputStream();
    private final boolean bigEndian;

    FitFileBuilder() {
        this(false);
    }

    FitFileBuilder(boolean bigEndian) {
        this.bigEndian = bigEndian;
    }

    record Field(int num, int size, int baseType) {
    }

    /** Convertit un instant ISO en secondes FIT. */
    static long fitTime(String iso) {
        return java.time.Instant.parse(iso).getEpochSecond() - FIT_EPOCH_S;
    }

    /** Degrés → semi-cercles, l'unité de position du format. */
    static long semicircles(double degrees) {
        return Math.round(degrees * 2_147_483_648.0 / 180.0);
    }

    FitFileBuilder define(int localType, int globalNum, List<Field> fields) {
        return define(localType, globalNum, fields, List.of());
    }

    /** @param devFieldSizes tailles des champs développeur, que le décodeur doit savoir sauter */
    FitFileBuilder define(int localType, int globalNum, List<Field> fields, List<Integer> devFieldSizes) {
        body.write(0x40 | (devFieldSizes.isEmpty() ? 0 : 0x20) | localType);
        body.write(0);                       // octet réservé
        body.write(bigEndian ? 1 : 0);       // architecture
        write(2, globalNum);                 // numéro de message global, dans l'architecture déclarée
        body.write(fields.size());
        for (Field f : fields) {
            body.write(f.num());
            body.write(f.size());
            body.write(f.baseType());
        }
        if (!devFieldSizes.isEmpty()) {
            body.write(devFieldSizes.size());
            for (int i = 0; i < devFieldSizes.size(); i++) {
                body.write(i);                   // numéro de champ développeur
                body.write(devFieldSizes.get(i));
                body.write(0);                   // index de description développeur
            }
        }
        return this;
    }

    /** Message de données : les valeurs dans l'ordre exact des champs définis. */
    FitFileBuilder data(int localType, List<Field> fields, long... values) {
        body.write(localType);
        for (int i = 0; i < fields.size(); i++) {
            write(fields.get(i).size(), values[i]);
        }
        return this;
    }

    /** Octets bruts, pour simuler les champs développeur d'un message de données. */
    FitFileBuilder raw(int byteCount) {
        for (int i = 0; i < byteCount; i++) {
            body.write(0);
        }
        return this;
    }

    byte[] build() {
        byte[] data = body.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(12);                       // taille d'en-tête (sans CRC d'en-tête)
        out.write(0x10);                     // version de protocole
        writeLittleEndian(out, 2, 2100);     // version de profil — l'en-tête est toujours petit-boutiste
        writeLittleEndian(out, 4, data.length);
        out.writeBytes(new byte[] {'.', 'F', 'I', 'T'});
        out.writeBytes(data);
        out.writeBytes(new byte[] {0, 0});   // CRC, non vérifié par le décodeur
        return out.toByteArray();
    }

    private void write(int size, long value) {
        if (bigEndian) {
            for (int i = size - 1; i >= 0; i--) {
                body.write((int) ((value >> (8 * i)) & 0xFF));
            }
        } else {
            writeLittleEndian(body, size, value);
        }
    }

    private static void writeLittleEndian(ByteArrayOutputStream out, int size, long value) {
        for (int i = 0; i < size; i++) {
            out.write((int) ((value >> (8 * i)) & 0xFF));
        }
    }
}

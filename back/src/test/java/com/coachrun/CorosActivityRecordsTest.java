package com.coachrun;

import com.coachrun.integration.CorosActivityRecords;
import com.coachrun.integration.CorosActivityRecords.Rec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lecture des réponses COROS MCP, dont le format n'est pas publié : structuré, JSON dans le
 * texte, ou texte numéroté. Ce qui compte : retrouver le labelId et le code sport de chaque sortie.
 */
class CorosActivityRecordsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private static JsonNode json(String s) throws Exception {
        return MAPPER.readTree(s);
    }

    private static JsonNode text(String t) {
        var r = MAPPER.createObjectNode();
        r.putArray("content").addObject().put("type", "text").put("text", t);
        return r;
    }

    @Test
    void litLeContenuStructure() throws Exception {
        List<Rec> recs = CorosActivityRecords.records(MAPPER, json("""
                {"content":[{"type":"text","text":"2 records"}],
                 "structuredContent":{"records":[
                   {"labelId":"476051234567","sportType":100,"date":"20261110","distanceKm":10.5,"totalTime":3125,"name":"Footing"},
                   {"labelId":"476051234999","sportType":200,"startTime":1762850000}
                 ]}}
                """), PARIS);

        assertThat(recs).hasSize(2);
        assertThat(recs.get(0)).isEqualTo(new Rec("476051234567", 100, LocalDate.of(2026, 11, 10), 10500, 3125, "Footing"));
        assertThat(recs.get(1).sportType()).isEqualTo(200);
        assertThat(recs.get(1).date()).isNotNull();
    }

    @Test
    void litUnTexteQuiEstDuJson() {
        List<Rec> recs = CorosActivityRecords.records(MAPPER,
                text("[{\"labelId\":\"abc12345\",\"sportType\":\"102\",\"date\":\"2026-11-09\"}]"), PARIS);

        assertThat(recs).extracting(Rec::labelId, Rec::sportType, Rec::date)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("abc12345", 102, LocalDate.of(2026, 11, 9)));
    }

    @Test
    void litLesBlocsNumerotesDuTexte() {
        List<Rec> recs = CorosActivityRecords.records(MAPPER, text("""
                Sport Records — 2026-11-01 to 2026-11-12 (2 records)

                1. Run(100) — 2026-11-10
                   Duration: 0:52:05 | Distance: 10.03 km
                   labelId: 476051234567 | sportType: 100

                2. Trail Run(102) — 2026-11-11
                   Duration: 1:12:00 | Distance: 12,5 km
                   labelId=476051235000, sportType=102
                """), PARIS);

        assertThat(recs).hasSize(2);
        assertThat(recs.get(0)).isEqualTo(new Rec("476051234567", 100, LocalDate.of(2026, 11, 10), 10030, 3125, null));
        assertThat(recs.get(1)).isEqualTo(new Rec("476051235000", 102, LocalDate.of(2026, 11, 11), 12500, 4320, null));
    }

    @Test
    void sansLabelIdAucuneSortieNEstRetenue() {
        assertThat(CorosActivityRecords.records(MAPPER, text("""
                1. Sport(9807) — 2026-10-03
                   Duration: 2:12:38 | Distance: 12.03 km
                """), PARIS)).isEmpty();
    }

    @Test
    void trouveLesLiensFit() throws Exception {
        assertThat(CorosActivityRecords.fitUrls(MAPPER, text("""
                Files (1):
                - 476051234567.fit: https://oss.coros.com/fit/476051234567.fit?sig=abc&exp=1.
                See https://support.coros.com for help.
                """))).containsExactly("https://oss.coros.com/fit/476051234567.fit?sig=abc&exp=1");
        assertThat(CorosActivityRecords.fitUrls(MAPPER, json("""
                {"content":[{"type":"resource_link","uri":"https://cdn.example/x/1.fit","name":"1.fit"}]}
                """))).containsExactly("https://cdn.example/x/1.fit");
        assertThat(CorosActivityRecords.fitUrls(MAPPER, json("""
                {"content":[],"structuredContent":{"files":[{"url":"https://s3.example/a.fit"}]}}
                """))).containsExactly("https://s3.example/a.fit");
    }

    @Test
    void recupereUnFitJointALaReponse() {
        byte[] fit = new FitFileBuilder().build();
        var r = MAPPER.createObjectNode();
        var c = r.putArray("content").addObject();
        c.put("type", "resource");
        c.putObject("resource").put("uri", "file://1.fit").put("blob", Base64.getEncoder().encodeToString(fit));
        r.withArray("content").addObject().put("type", "text").put("text", "not a file");

        assertThat(CorosActivityRecords.fitBlobs(r)).hasSize(1);
        assertThat(CorosActivityRecords.fitBlobs(r).get(0)).isEqualTo(fit);
    }
}

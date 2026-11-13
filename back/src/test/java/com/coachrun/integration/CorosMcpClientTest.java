package com.coachrun.integration;

import com.coachrun.integration.CorosMcpClient.Endpoints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ce que le client COROS MCP lit des réponses de COROS, sans réseau. */
class CorosMcpClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void laDecouverteDeLaPasserelleDesigneLeServeurRegional() throws Exception {
        JsonNode doc = mapper.readTree("""
                {"issuer":"https://mcpeu.coros.com",
                 "authorization_endpoint":"https://mcpeu.coros.com/oauth2/authorize",
                 "token_endpoint":"https://mcpeu.coros.com/oauth2/token",
                 "registration_endpoint":"https://mcpeu.coros.com/connect/register",
                 "revocation_endpoint":"https://mcpeu.coros.com/oauth2/revoke"}""");

        Endpoints e = CorosMcpClient.endpointsFrom("https://mcp.coros.com", doc);

        assertThat(e.issuer()).isEqualTo("https://mcpeu.coros.com");
        assertThat(e.token()).isEqualTo("https://mcpeu.coros.com/oauth2/token");
        assertThat(e.revocation()).isEqualTo("https://mcpeu.coros.com/oauth2/revoke");
        assertThat(e.mcp()).isEqualTo("https://mcpeu.coros.com/mcp");
    }

    @Test
    void sansDecouverteOnRetombeSurLesCheminsDocumentes() {
        Endpoints e = CorosMcpClient.endpointsFrom("https://mcp.coros.com", null);

        assertThat(e.issuer()).isEqualTo("https://mcp.coros.com");
        assertThat(e.authorization()).isEqualTo("https://mcp.coros.com/oauth2/authorize");
        assertThat(e.token()).isEqualTo("https://mcp.coros.com/oauth2/token");
        assertThat(e.registration()).isEqualTo("https://mcp.coros.com/connect/register");
        assertThat(e.revocation()).isNull();
        assertThat(e.mcp()).isEqualTo("https://mcp.coros.com/mcp");
    }

    @Test
    void lUrlDAutorisationPorteLeScopeLaRessourceMcpEtPkce() {
        CorosMcpClient client = new CorosMcpClient(true, "https://mcp.coros.com",
                "https://www.darilab.app/app/coros/callback", mapper);
        Endpoints e = CorosMcpClient.endpointsFrom("https://mcpeu.coros.com", null);

        String url = client.authorizeUrl(e, "client-1", "challenge-1", "state-1");

        var p = UriComponentsBuilder.fromUriString(url).build(true).getQueryParams();
        assertThat(url).startsWith("https://mcpeu.coros.com/oauth2/authorize?");
        assertThat(p.getFirst("response_type")).isEqualTo("code");
        assertThat(p.getFirst("client_id")).isEqualTo("client-1");
        assertThat(p.getFirst("code_challenge")).isEqualTo("challenge-1");
        assertThat(p.getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(p.getFirst("state")).isEqualTo("state-1");
        assertThat(decode(p.getFirst("scope"))).isEqualTo("openid offline_access mcp.tools");
        assertThat(decode(p.getFirst("resource"))).isEqualTo("https://mcpeu.coros.com/mcp");
        assertThat(decode(p.getFirst("redirect_uri"))).isEqualTo("https://www.darilab.app/app/coros/callback");
    }

    @Test
    void fermeeTantQueCorosNEstPasActive() {
        assertThat(new CorosMcpClient(false, "https://mcp.coros.com", "https://x/cb", mapper).isConfigured())
                .isFalse();
        assertThat(new CorosMcpClient(true, "https://mcp.coros.com", "", mapper).isConfigured()).isFalse();
        assertThat(new CorosMcpClient(true, "https://mcp.coros.com", "https://x/cb", mapper).isConfigured())
                .isTrue();
    }

    @Test
    void uneReponseJsonEstLueTelleQuelle() {
        JsonNode r = CorosMcpClient.readRpcPayload(mapper,
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}}");
        assertThat(r.get("result").get("content").get(0).get("text").asText()).isEqualTo("ok");
    }

    @Test
    void uneReponseEnFluxSseGardeLeMessageQuiPorteLeResultat() {
        String sse = """
                event: message
                data: {"jsonrpc":"2.0","method":"notifications/progress","params":{}}

                event: message
                data: {"jsonrpc":"2.0","id":2,
                data: "result":{"isError":false}}

                """;
        JsonNode r = CorosMcpClient.readRpcPayload(mapper, sse);
        assertThat(r.get("id").asInt()).isEqualTo(2);
        assertThat(r.get("result").get("isError").asBoolean()).isFalse();
    }

    @Test
    void uneReponseIlisibleEstSignalee() {
        assertThat(CorosMcpClient.readRpcPayload(mapper, "")).isNull();
        assertThatThrownBy(() -> CorosMcpClient.readRpcPayload(mapper, "{pas du json"))
                .isInstanceOf(CorosMcpClient.McpException.class);
    }

    @Test
    void lIdentifiantCorosVientDuSubDeLIdToken() {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String token = b64.encodeToString("{}".getBytes(StandardCharsets.UTF_8)) + "."
                + b64.encodeToString("{\"sub\":\"424242\"}".getBytes(StandardCharsets.UTF_8)) + ".sig";

        assertThat(CorosMcpClient.subjectOf(token)).isEqualTo("424242");
        assertThat(CorosMcpClient.subjectOf(null)).isNull();
        assertThat(CorosMcpClient.subjectOf("pas-un-jwt")).isNull();
    }

    @Test
    void laRegionSeDeduitDuServeur() {
        assertThat(CorosMcpClient.regionOf("https://mcpeu.coros.com")).isEqualTo("EU");
        assertThat(CorosMcpClient.regionOf("https://mcpus.coros.com")).isEqualTo("US");
        assertThat(CorosMcpClient.regionOf("https://mcpcn.coros.com")).isEqualTo("CN");
        assertThat(CorosMcpClient.regionOf("https://mcp.coros.com")).isNull();
    }

    private static String decode(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}

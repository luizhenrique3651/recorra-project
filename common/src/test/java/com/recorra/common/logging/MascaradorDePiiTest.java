package com.recorra.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MascaradorDePiiTest {

  private static void mascara(String entrada, String... segredos) {
    String saida = MascaradorDePii.mascarar(entrada);
    for (String s : segredos) {
      assertThat(saida).as("'%s' → '%s'", entrada, saida).doesNotContain(s).contains("***");
    }
  }

  @Test
  @DisplayName(
      "Dado telefone, nome, CPF, e-mail e token num log / Quando mascara / Então todos saem mascarados")
  void todosOsTiposNaMesmaLinha() {
    String linha =
        "cliente nome=João da Silva, telefone=+55 (11) 98765-4321, cpf=123.456.789-09,"
            + " email=joao.silva@example.com, access_token=abc123segredo";

    String saida = MascaradorDePii.mascarar(linha);

    assertThat(saida)
        .doesNotContain("João", "Silva", "98765", "4321", "123.456", "joao.silva", "example.com")
        .doesNotContain("abc123segredo");
  }

  @Test
  void telefones() {
    for (String t :
        List.of(
            "+55 11 98765-4321",
            "+5511987654321",
            "(11) 98765-4321",
            "(11)987654321",
            "11 98765-4321",
            "11-98765-4321",
            "5511987654321",
            "551132654321",
            "+55 (21) 3265-4321")) {
      mascara(
          "contato " + t + " fim",
          t.replaceAll("\\D", "").substring(Math.max(0, t.replaceAll("\\D", "").length() - 8)));
    }
  }

  @Test
  void cpfECnpj() {
    mascara("doc 123.456.789-09 ok", "123.456.789-09");
    mascara("doc 12345678909 ok", "12345678909");
    mascara("doc 12.345.678/0001-95 ok", "12.345.678/0001-95");
    mascara("doc 12345678000195 ok", "12345678000195");
  }

  @Test
  void emails() {
    mascara("de maria.souza+tag@empresa.com.br para x", "maria.souza", "empresa.com.br");
  }

  @Test
  void segredosPorChave() {
    mascara("Authorization: Bearer abcdef123456", "abcdef123456");
    mascara("Authorization: Basic dXNlcjpwYXNz", "dXNlcjpwYXNz");
    mascara(
        "{\"access_token\":\"tok-123-secreto\",\"refresh_token\":\"ref-456-secreto\"}",
        "tok-123",
        "ref-456");
    mascara("client_secret=s3cr3t&grant_type=x", "s3cr3t");
    mascara("password=hunter2", "hunter2");
    mascara("senha: minhaSenha", "minhaSenha");
    mascara("X-Hub-Signature-256 hmac=deadbeef0123", "deadbeef0123");
    mascara("signature=assinatura-xyz", "assinatura-xyz");
    mascara("api_key=k-999", "k-999");
    mascara("Set-Cookie: sessao=zzz", "sessao=zzz");
  }

  @Test
  @DisplayName(
      "Dado a chave mestra, DEKs e keysets (ADR-0004) / Quando mascara / Então não saem em claro")
  void chaveMestraDeksEKeysets() {
    String b64 = "q83vEjRWeJq83vEjRWeJq83vEjRWeJq83vEjRWeJq8s=";
    String hex = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";
    mascara("chave_mestra=" + hex, hex);
    mascara("master_key: " + b64, b64);
    mascara("dek=" + b64, b64);
    mascara("kek=segredo-kek", "segredo-kek");
    mascara("falha com chave solta " + b64 + " no meio", b64);
    mascara("keyset " + hex, hex);
    mascara("encryptedKeyset=ZW5jcnlwdGVk", "ZW5jcnlwdGVk");
    String keysetTink =
        "{\"primaryKeyId\":123,\"key\":[{\"keyData\":{\"typeUrl\":\"type.googleapis.com/google.crypto.tink.AesGcmKey\","
            + "\"value\":\"GhDtcGhLJm7KE9nLwRqUb2fG\",\"keyMaterialType\":\"SYMMETRIC\"},\"status\":\"ENABLED\"}]}";
    String saida = MascaradorDePii.mascarar(keysetTink);
    assertThat(saida).doesNotContain("GhDtcGhLJm7KE9nLwRqUb2fG").doesNotContain("AesGcmKey");
  }

  @Test
  void jwt() {
    mascara(
        "id eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NSJ9.c2lnbmF0dXJl fim", "eyJhbGciOiJIUzI1NiJ9");
  }

  @Test
  @DisplayName(
      "Dado a query do callback OAuth / Quando mascara / Então code, state e token saem ***")
  void queryOauth() {
    assertThat(MascaradorDePii.mascararQuery("code=abc&state=xyz&token=t1&pagina=2"))
        .isEqualTo("code=***&state=***&token=***&pagina=2");
  }

  @Test
  void naoMascaraTextoInocente() {
    for (String ok :
        List.of(
            "request GET /clientes/{id} status=200 duracaoMs=12",
            "ERP bling: tentativa 1/3 falhou (HTTP 429); nova tentativa em 500 ms",
            "status code: 404",
            "traceId=4bf92f3577b34da6a3ce929d0e0e4736",
            "com.recorra.common.web.ProblemaGlobalHandler",
            "id 550e8400-e29b-41d4-a716-446655440000",
            "epoch 1731266000000",
            "rejeição de negócio: codigo=JANELA_INVALIDA status=422")) {
      assertThat(MascaradorDePii.mascarar(ok)).as(ok).isEqualTo(ok);
    }
  }

  @Test
  void nuloEVazioPassam() {
    assertThat(MascaradorDePii.mascarar(null)).isNull();
    assertThat(MascaradorDePii.mascarar("")).isEmpty();
    assertThat(MascaradorDePii.mascararQuery(null)).isNull();
    assertThat(MascaradorDePii.mascararQuery("")).isEmpty();
  }

  @Test
  @DisplayName(
      "Dado PII em query codificada / Quando mascara / Então nome, telefone e e-mail saem ***")
  void queryComPiiCodificada() {
    assertThat(
            MascaradorDePii.mascararQuery(
                "nome=Maria%20Silva&name=Joao&telefone=1133334444&email=a%40b.com&cpf=123&pagina=2"))
        .isEqualTo("nome=***&name=***&telefone=***&email=***&cpf=***&pagina=2");
  }

  @Test
  @DisplayName("Dado nome de parâmetro codificado / Quando mascara / Então não escapa")
  void queryComNomeCodificado() {
    assertThat(MascaradorDePii.mascararQuery("co%64e=abc&Co%44e=zzz"))
        .isEqualTo("co%64e=***&Co%44e=***");
    String saida = MascaradorDePii.mascararQuery("refresh_token%3Dabc123&x=1");
    assertThat(saida).doesNotContain("abc123");
    assertThat(MascaradorDePii.mascararQuery("TOKEN=abc")).isEqualTo("TOKEN=***");
    assertThat(MascaradorDePii.mascararQuery("pct=%zz&q=ok")).isEqualTo("pct=%zz&q=ok");
  }

  @Test
  @DisplayName("Dado parâmetro cujo nome termina em name / Quando mascara / Então o valor sai ***")
  void queryComChaveTerminadaEmName() {
    assertThat(MascaradorDePii.mascararQuery("contactName=Joana%20Lima&q=ok"))
        .isEqualTo("contactName=***&q=ok");
  }

  @Test
  @DisplayName("Dado chaves terminadas em name/Name / Quando mascara / Então o valor sai ***")
  void chavesTerminadasEmName() {
    mascara("contactName=Joana Lima", "Joana");
    mascara("recipientName: Pedro Alves", "Pedro");
    mascara("{\"customerName\":\"Ana Paula\"}", "Ana");
    mascara("nomeContato=Carlos Dias", "Carlos");
    // chaves técnicas ficam
    assertThat(MascaradorDePii.mascarar("logger_name=com.x hostname=srv1 thread_name=main"))
        .isEqualTo("logger_name=com.x hostname=srv1 thread_name=main");
  }

  @Test
  @DisplayName("Dado 'token <valor>' sem : nem = / Quando mascara / Então o valor sai ***")
  void tokenSemSeparador() {
    mascara("recebido token abc123def456 do ERP", "abc123def456");
    mascara("falha com Token a1b2c3d4e5f6g7h8", "a1b2c3d4e5f6g7h8");
    assertThat(MascaradorDePii.mascarar("token expirado para o cliente"))
        .isEqualTo("token expirado para o cliente");
  }

  @Test
  @DisplayName(
      "Dado telefone fixo de 10 dígitos / Quando mascara / Então só com chave, separador ou +55 (decisão)")
  void fixoDeDezDigitos() {
    assertThat(MascaradorDePii.mascarar("epoch 1731266000 fim")).isEqualTo("epoch 1731266000 fim");
    mascara("telefone=1133334444", "1133334444");
    mascara("ligar (11) 3333-4444", "3333-4444");
    mascara("ligar 11 3333-4444", "3333-4444");
    mascara("ligar +551133334444", "33334444");
  }

  @Test
  void queryComParametroSemValorEPii() {
    assertThat(MascaradorDePii.mascararQuery("flag&email=a@b.com&q=ok"))
        .isEqualTo("flag&email=***&q=ok");
  }

  @Test
  @DisplayName("Dado um traceId hex com 11+ dígitos seguidos / Quando mascara / Então sai intacto")
  void traceIdNaoEhCorrompido() {
    for (String id :
        List.of(
            "9ecb7f5dbd21de01dd12345678901e95",
            "12345678901234567890123456789012",
            "aaaaaaaa12345678901bbbbbbbbbbbbb",
            "00f067aa0ba902b7",
            "4bf92f3577b34da6a3ce929d0e0e4736")) {
      assertThat(MascaradorDePii.mascarar(id)).as(id).isEqualTo(id);
      assertThat(MascaradorDePii.mascarar("traceId=" + id + " ok")).as(id).contains(id);
    }
  }

  @Test
  @DisplayName("Dado caminho de classe/recurso / Quando mascara / Então sai intacto")
  void caminhoDeClasseNaoEhBlocoDeChave() {
    for (String ok :
        List.of(
            "class path resource [org/springframework/boot/autoconfigure/jdbc/DataSourceAutoConfiguration.class]",
            "Bean 'org/springframework/boot/autoconfigure/web/servlet/WebMvcAutoConfiguration'",
            "file [/app/classes/com/recorra/common/web/ProblemaGlobalHandler.class]")) {
      assertThat(MascaradorDePii.mascarar(ok)).as(ok).isEqualTo(ok);
    }
  }

  @Test
  @DisplayName(
      "Dado CPF, telefone, Tink e DEK com fronteira / Quando mascara / Então seguem mascarados")
  void fronteirasNaoDeixamVazar() {
    mascara("cpf 12345678909.", "12345678909");
    mascara("(12345678909)", "12345678909");
    mascara("tel 11987654321, fim", "11987654321");
    mascara("tel: +5511987654321;", "5511987654321");
    mascara(
        "dek q83vEjRWeJq83vEjRWeJq83vEjRWeJq83vEjRWeJq8s= fim",
        "q83vEjRWeJq83vEjRWeJq83vEjRWeJq83vEjRWeJq8s");
    mascara(
        "chave 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "0123456789abcdef0123456789abcdef");
    mascara("blob qA83vEj+RWeJ/q83vEjRWeJq83vEjRWeJq83vEjRW9eJq8s", "qA83vEj+RWeJ");
  }
}

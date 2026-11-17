package com.recorra.common.logging;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mascara PII e segredos em texto de log (REC-005, LGPD, ADR-0004). Defesa em profundidade, em duas
 * frentes: por <b>chave</b> ({@code nome=...}, {@code "access_token":"..."}, {@code Authorization:
 * Bearer ...}) e por <b>formato</b> (e-mail, CPF/CNPJ, telefone BR, JWT, Bearer, blocos longos de
 * base64/hex como a chave mestra, DEKs e keysets AEAD/MAC do Tink).
 *
 * <p>Telefone fixo de 10 dígitos SEM separador NÃO é mascarado solto (colidiria com epoch em
 * segundos e outros ids); só com chave ({@code telefone=1133334444}), com {@code +55}, com (DDD) ou
 * separadores. Celular de 11 dígitos e {@code 55...} de 12/13 dígitos são mascarados.
 *
 * <p>Limites assumidos: nome de pessoa só é pego quando vem com chave (não há como achá-lo em texto
 * livre); números soltos de 11 ou 14 dígitos e blocos de 40+ caracteres base64/hex são mascarados
 * mesmo quando não eram PII (falso positivo é preferível a vazamento). A regra de ouro continua
 * sendo NÃO logar o dado; este é o último filtro.
 */
public final class MascaradorDePii {

  /** Substituto. */
  public static final String MASCARA = "***";

  /** Parâmetros de query que nunca saem em claro (rotas OAuth: code, state, token...). */
  private static final Set<String> QUERY_SENSIVEL =
      Set.of(
          "code",
          "state",
          "token",
          "access_token",
          "refresh_token",
          "id_token",
          "client_secret",
          "secret",
          "password",
          "senha",
          "signature",
          "hmac",
          "api_key",
          "apikey",
          "key",
          "assertion");

  private static final String SEPARADOR = "[\"']?\\s*[:=]\\s*[\"']?";

  /** Chaves cujo valor é segredo (valor até espaço, aspas, vírgula, ponto e vírgula, &, }, ]). */
  private static final Pattern SEGREDO_POR_CHAVE =
      Pattern.compile(
          "(?<![\\w-])((?:proxy-authorization|authorization|set-cookie|cookie|x-api-key|"
              + "encrypted[_-]?keyset|private[_-]?key|master[_-]?key|chave[_-]?mestra|mestra|"
              + "access[_-]?token|refresh[_-]?token|id[_-]?token|client[_-]?secret|api[_-]?key|"
              + "passwd|password|senha|secret|signature|assinatura|hmac|keyset|token|dek|kek)"
              + SEPARADOR
              + ")(?:Bearer\\s+|Basic\\s+)?[^\\s\"',;&}\\]]+",
          Pattern.CASE_INSENSITIVE);

  /**
   * code/state/token na forma de query ({@code a=b}); só com '=' para não pegar "status code: 404".
   */
  private static final Pattern QUERY_OAUTH =
      Pattern.compile("(?<![\\w-])((?:code|state|token)=)[^\\s&\"']+", Pattern.CASE_INSENSITIVE);

  /**
   * Chaves de PII: o valor (pode ter espaços, como um nome) vai até a próxima chave ({@code x=}),
   * aspas, vírgula, ponto e vírgula, &, }, ] ou fim de linha.
   */
  private static final Pattern PII_POR_CHAVE =
      Pattern.compile(
          "(?<![\\w-])((?:(?!(?:logger|thread|host|file|class|method|app|application|service|schema|"
              + "table|column|user|path|cluster|node|pod|container|bean|queue|topic|group|index|"
              + "driver)[_-]?name)[\\w-]*(?:name|nome)|nome[\\w-]*|razao[_-]?social|sobrenome|"
              + "telefone|celular|whatsapp|phone|msisdn|fone|cpf|cnpj|documento|document|"
              + "e-?mail)"
              + SEPARADOR
              + ")[^\"',;&}\\]\\r\\n]+?(?=\\s+[\\w.-]+\\s*[=:]|[\"',;&}\\]\\r\\n]|$)",
          Pattern.CASE_INSENSITIVE);

  /** "token <valor>" sem ':' nem '=' (só com valor de 8+ caracteres e algum dígito). */
  private static final Pattern TOKEN_SOLTO =
      Pattern.compile(
          "(?<![\\w-])(token\\s+)(?=[A-Za-z0-9._~+/=-]*\\d)[A-Za-z0-9._~+/=-]{8,}",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern TINK_KEY_DATA =
      Pattern.compile("(\"keyData\"\\s*:\\s*)\\{[^}]*}", Pattern.CASE_INSENSITIVE);

  private static final Pattern JWT =
      Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*");

  private static final Pattern BEARER_OU_BASIC =
      Pattern.compile("\\b(Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]{8,}", Pattern.CASE_INSENSITIVE);

  /** Bloco base64/hex de 40+ caracteres (chave mestra, DEK, keyset); não começa com '/'. */
  private static final Pattern BLOCO_LONGO =
      Pattern.compile("(?<![\\w/.:-])[A-Za-z0-9+_-][A-Za-z0-9+/_-]{38,}={0,2}");

  private static final Pattern EMAIL =
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

  private static final Pattern CNPJ =
      Pattern.compile("(?<!\\w)(?:\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}|\\d{14})(?!\\w)");

  private static final Pattern CPF =
      Pattern.compile("(?<!\\w)(?:\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}|\\d{11})(?!\\w)");

  /** Telefones BR: com +55, com (DDD), com separadores, ou só dígitos (55DDD... / DDD9XXXXXXXX). */
  private static final Pattern TELEFONE =
      Pattern.compile(
          "(?<!\\w)\\+55[\\s.-]?\\(?\\d{2}\\)?[\\s.-]?9?\\d{4}[\\s.-]?\\d{4}(?!\\w)"
              + "|(?<![\\w.])\\(\\d{2}\\)[\\s.-]?9?\\d{4}[\\s.-]?\\d{4}(?!\\w)"
              + "|(?<![\\w.])\\d{2}[\\s.-]9?\\d{4}[\\s.-]\\d{4}(?!\\w)"
              + "|(?<!\\w)55\\d{2}9?\\d{8}(?!\\w)");

  private MascaradorDePii() {}

  /** Texto livre de log (mensagem, stacktrace, valor de MDC). Nulo vira nulo. */
  public static String mascarar(String texto) {
    if (texto == null || texto.isEmpty()) {
      return texto;
    }
    String r = TINK_KEY_DATA.matcher(texto).replaceAll("$1{" + MASCARA + "}");
    r = substituirGrupo1(SEGREDO_POR_CHAVE, r);
    r = substituirGrupo1(QUERY_OAUTH, r);
    r = substituirGrupo1(TOKEN_SOLTO, r);
    r = substituirGrupo1(PII_POR_CHAVE, r);
    r = JWT.matcher(r).replaceAll(MASCARA);
    r = BEARER_OU_BASIC.matcher(r).replaceAll("$1 " + MASCARA);
    r =
        BLOCO_LONGO
            .matcher(r)
            .replaceAll(
                m -> pareceCaminho(m.group()) ? Matcher.quoteReplacement(m.group()) : MASCARA);
    r = EMAIL.matcher(r).replaceAll(MASCARA);
    r = CNPJ.matcher(r).replaceAll(MASCARA);
    r = CPF.matcher(r).replaceAll(MASCARA);
    return TELEFONE.matcher(r).replaceAll(MASCARA);
  }

  /**
   * Query string de uma request: nomes ficam, valores de parâmetros sensíveis ({@code code}, {@code
   * state}, {@code token}...) viram {@code ***} e os demais passam pelo {@link #mascarar}. Ex.:
   * {@code code=abc&state=xyz&pagina=2} → {@code code=***&state=***&pagina=2}.
   */
  public static String mascararQuery(String query) {
    if (query == null || query.isEmpty()) {
      return query;
    }
    StringBuilder sb = new StringBuilder();
    for (String par : query.split("&", -1)) {
      if (!sb.isEmpty()) {
        sb.append('&');
      }
      int igual = par.indexOf('=');
      if (igual < 0) {
        sb.append(mascarar(decodificar(par)).equals(decodificar(par)) ? par : MASCARA);
        continue;
      }
      String nomeBruto = par.substring(0, igual);
      String valorBruto = par.substring(igual + 1);
      // Decodifica ANTES de decidir: co%64e=abc, refresh_token%3Dabc e ?nome=Maria%20Silva.
      String nome = decodificar(nomeBruto);
      String valor = decodificar(valorBruto);
      sb.append(nomeBruto).append('=');
      if (QUERY_SENSIVEL.contains(nome.toLowerCase(Locale.ROOT))) {
        sb.append(MASCARA);
        continue;
      }
      // Máscara por CHAVE: contactName=Joana, qualquer *name, etc.
      String original = nome + "=" + valor;
      String mascarado = mascarar(original);
      sb.append(mascarado.equals(original) ? valorBruto : mascarado.substring(nome.length() + 1));
    }
    return sb.toString();
  }

  private static String decodificar(String texto) {
    try {
      return URLDecoder.decode(texto, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return texto; // % mal formado: usa o texto como veio
    }
  }

  /**
   * Caminho de classe/recurso ({@code org/springframework/boot/.../DataSourceAutoConfiguration})
   * não é chave: tem '/' e quase nenhum dígito (base64/hex aleatório tem ~16% ou mais).
   */
  private static boolean pareceCaminho(String bloco) {
    if (bloco.indexOf('/') < 0) {
      return false;
    }
    long digitos = bloco.chars().filter(Character::isDigit).count();
    return digitos * 100 < bloco.length() * 6L;
  }

  private static String substituirGrupo1(Pattern padrao, String texto) {
    Matcher m = padrao.matcher(texto);
    return m.replaceAll(r -> Matcher.quoteReplacement(r.group(1) + MASCARA));
  }
}

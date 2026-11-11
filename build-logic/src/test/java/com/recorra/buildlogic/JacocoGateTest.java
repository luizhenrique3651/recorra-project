package com.recorra.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Prova o gate de cobertura do {@code recorra.java-conventions} (REC-002, débito do REC-001): (a)
 * código gerado em {@code build/generated} fica fora da base; (b) classe de produção sem dado de
 * execução FALHA o build em vez de ser pulada.
 */
class JacocoGateTest {

  private static final List<String> ARGUMENTOS =
      List.of(
          "check",
          "-x",
          "spotlessCheck",
          "-x",
          "spotlessJavaCheck",
          "-x",
          "spotbugsMain",
          "-x",
          "spotbugsTest",
          "--stacktrace");

  @TempDir Path projeto;

  @Test
  void classeDeProducaoSemTesteFalhaOBuild() throws IOException {
    montar("");
    escrever("src/main/java/x/Servico.java", servico());

    BuildResult r = rodar(true);

    assertThat(r.getOutput()).contains("nenhum teste rodou").contains("verificarDadosDeCobertura");
  }

  @Test
  void moduloSemClassesDeProducaoPassa() throws IOException {
    montar("");
    escrever("src/main/java/x/package-info.java", "package x;\n");

    BuildResult r = rodar(false);

    assertThat(r.getOutput()).contains("BUILD SUCCESSFUL");
  }

  @Test
  void classeDeProducaoComTesteCobrindoPassa() throws IOException {
    montar("");
    escrever("src/main/java/x/Servico.java", servico());
    escrever("src/test/java/x/ServicoTest.java", testeDoServico());

    BuildResult r = rodar(false);

    assertThat(r.getOutput()).contains("BUILD SUCCESSFUL");
  }

  @Test
  void codigoGeradoSemCoberturaFicaForaDaBase() throws IOException {
    montar(
        """
        val gerar = tasks.register("gerarFonte") {
          val saida = layout.buildDirectory.dir("generated/sources/gen")
          outputs.dir(saida)
          doLast {
            val arquivo = saida.get().asFile.resolve("x/Gerado.java")
            arquivo.parentFile.mkdirs()
            arquivo.writeText(\"\"\"%s\"\"\")
          }
        }
        sourceSets.main { java.srcDir(layout.buildDirectory.dir("generated/sources/gen")) }
        tasks.compileJava { dependsOn(gerar) }
        """
            .formatted(grande("Gerado")));
    escrever("src/main/java/x/Servico.java", servico());
    escrever("src/test/java/x/ServicoTest.java", testeDoServico());

    BuildResult r = rodar(false);

    assertThat(r.getOutput()).contains("BUILD SUCCESSFUL");
    String relatorio =
        Files.readString(projeto.resolve("build/reports/jacoco/test/jacocoTestReport.xml"));
    assertThat(relatorio).contains("x/Servico").doesNotContain("x/Gerado");
  }

  @Test
  void mesmaClasseGrandeEscritaAMaoSemCoberturaDerrubaOGate() throws IOException {
    montar("");
    escrever("src/main/java/x/Servico.java", servico());
    escrever("src/main/java/x/Manual.java", grande("Manual"));
    escrever("src/test/java/x/ServicoTest.java", testeDoServico());

    BuildResult r = rodar(true);

    assertThat(r.getOutput()).contains("Rule violated for bundle");
  }

  // ---------------------------------------------------------------- infra do teste

  private void montar(String extra) throws IOException {
    Path catalogo = Path.of("..", "gradle", "libs.versions.toml").toAbsolutePath().normalize();
    escrever(
        "settings.gradle.kts",
        """
        dependencyResolutionManagement {
          repositories { mavenCentral() }
          versionCatalogs { create("libs") { from(files("%s")) } }
        }
        rootProject.name = "projeto-de-teste"
        """
            .formatted(catalogo.toString().replace("\\", "/")));
    escrever(
        "build.gradle.kts",
        "plugins { id(\"recorra.java-conventions\") }\n\n" + extra + "\n");
  }

  private BuildResult rodar(boolean esperaFalha) {
    GradleRunner runner =
        GradleRunner.create()
            .withProjectDir(projeto.toFile())
            .withPluginClasspath()
            .withArguments(ARGUMENTOS);
    return esperaFalha ? runner.buildAndFail() : runner.build();
  }

  private void escrever(String caminho, String conteudo) throws IOException {
    Path destino = projeto.resolve(caminho);
    Files.createDirectories(destino.getParent());
    Files.writeString(destino, conteudo);
  }

  private static String servico() {
    return """
        package x;

        public final class Servico {
          public int dobro(int n) {
            return n * 2;
          }
        }
        """;
  }

  private static String testeDoServico() {
    return """
        package x;

        import static org.junit.jupiter.api.Assertions.assertEquals;

        import org.junit.jupiter.api.Test;

        class ServicoTest {
          @Test
          void dobra() {
            assertEquals(4, new Servico().dobro(2));
          }
        }
        """;
  }

  /** Classe com dezenas de linhas executáveis, para pesar na cobertura quando não testada. */
  private static String grande(String nome) {
    StringBuilder b = new StringBuilder("package x;\n\npublic final class " + nome + " {\n");
    b.append("  public static int soma(int a) {\n");
    for (int i = 0; i < 40; i++) {
      b.append("    a += ").append(i).append(";\n");
    }
    b.append("    return a;\n  }\n}\n");
    return b.toString();
  }
}

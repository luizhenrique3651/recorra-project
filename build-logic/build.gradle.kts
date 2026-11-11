plugins { `kotlin-dsl` }

dependencies {
  implementation(libs.spring.boot.gradle.plugin)
  implementation(libs.spotless.gradle.plugin)
  implementation(libs.spotbugs.gradle.plugin)
  // Plugin do gerador do contrato (REC-002): vem do classpath do build-logic para dividir o
  // classloader com as convenções (um plugin com versão no módulo quebra o serviço do Spotless).
  implementation(libs.jsonschema2pojo.gradle.plugin)
  // Necessário: o Kotlin do script de convenção precisa enxergar GenerationConfig, supertipo de
  // JsonSchemaExtension que o plugin só expõe em runtime (sem ele: "Cannot access 'GenerationConfig'").
  implementation(libs.jsonschema2pojo.core)

  // Testes das próprias convenções (TestKit): provam o gate do JaCoCo (REC-002).
  testImplementation(platform(libs.spring.boot.bom))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj.core)
  testRuntimeOnly(libs.junit.platform.launcher)
  testImplementation(gradleTestKit())
}

tasks.test {
  useJUnitPlatform()
  // Cada cenário sobe um build Gradle de verdade; precisa de tempo e não deve rodar em paralelo.
  maxParallelForks = 1
}

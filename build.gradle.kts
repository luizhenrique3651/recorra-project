// Projeto raiz: sem código. Só garante que `./gradlew build` também roda os testes das convenções
// do build-logic (gate do JaCoCo, REC-002), que ficam num build incluído e não rodariam sozinhos.
plugins { base }

tasks.named("check") { dependsOn(gradle.includedBuild("build-logic").task(":check")) }

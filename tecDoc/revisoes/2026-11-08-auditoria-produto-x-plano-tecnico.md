# Auditoria produto × plano técnico — Recorra (pré-REC-001)

*Tech lead · 2026-11-08 · Escopo: tecDoc/CONTEXTO-PROJETO.md, tecDoc/roadmap-desenvolvimento-sdd.md, docs/adr/, README e código do monorepo.*

**Veredito: ALINHADO COM RESSALVAS.**

A arquitetura (hexagonal, driver-based, contrato canônico, read-only, RLS, MessagingPort) serve ao produto, e o fluxo venda→ciclo→ação→WhatsApp está coberto de S2 a S5. Mas, do jeito que está, o piloto (S7) não entrega valor: nenhum card cria a base de opt-in (sem opt-in, nada é enviado), não há card de UI para o dono, ninguém validou se as vendas do Bling trazem cliente identificado, e o onboarding da Meta não tem card. O plano também traz infraestrutura de escala cedo demais.

> **Decisão do usuário (2026-11-08):** aplicar todos os achados no roadmap e nos docs. Git: `main` + `develop` + `feature/REC-XXX`.

## Crítico

**C1. Opt-in sem origem e na ordem errada.** O REC-041 (S4) checa opt-in, mas o consentimento só nasce no REC-053 (S5), e nenhum card diz COMO o cliente dá opt-in. O ERP não guarda isso, e uma base importada do Bling começa com 0% de opt-in, então o piloto não envia nada.
→ Criar um card de modelo de consentimento (tabela + `ConsentPolicy` única) no S3. Criar a STORY "captação de opt-in": link/QR na loja ou no recibo, landing que registra origem e timestamp, e importação com prova de consentimento prévio. Ligar o opt-out ao cancelamento das ações PENDENTES. REC-041 e REC-053 passam a usar a MESMA `ConsentPolicy` (hoje a regra está duplicada).

**C2. Hipótese de dado não validada.** O produto depende de a venda no Bling ter cliente identificado com WhatsApp e de existir consumível recorrente. No varejo pequeno, muita venda sai como "consumidor final". Nenhum card trata venda sem cliente, normalização do telefone para E.164 ou cliente duplicado.
→ Antes do S2, analisar o export real de um comerciante-candidato (% de vendas com telefone, nº de clientes com ≥3 compras do mesmo SKU). No REC-020, incluir política para venda sem cliente (ignorar e contar métrica), normalização E.164 e chave de deduplicação do cliente.

**C3. Dependências externas com prazo longo tratadas como card de sprint.**
(a) O contato com o suporte do Bling (CONTEXTO §5.2, REC-019) só acontece no S1, mas trava o S2.
(b) A Meta exige Business verification, um WABA e número por comércio (Embedded Signup?) e aprovação de template de marketing. Nenhum card cobre isso, e o REC-051 já assume template aprovado e credencial por comércio.
→ Abrir as duas frentes AGORA, fora do board (não é código). Adicionar um card S5 "onboarding WhatsApp do comércio": credenciais, template por comércio e tratamento de template reprovado. Criar um driver-stub no S2 (antecipando o REC-073 como ferramenta de teste) para que S2–S4 não fiquem bloqueados pelo Bling.

**C4. Logs, rastreabilidade e PII em log (regra do usuário: OBRIGATÓRIO).** Hoje só o REC-003 cita Logback JSON com MDC traceId/comercioId. O mascaramento de PII fica para o S7 (REC-072), mas a PII entra no S2. Não há nada sobre propagar a correlação driver→broker/outbox→core→WhatsApp, nem sobre a política de log de erro.
→ Ampliar o REC-003 (ou criar um REC-005 no S0):
- logs JSON com traceId/spanId/comercioId;
- propagação W3C `traceparent` em HTTP e em headers de mensagem/outbox (gravar o traceId na linha do outbox);
- conversor de mascaramento de PII (telefone, nome, CPF, e-mail, tokens), com teste de appender, desde o S0;
- política de nível: stacktrace uma única vez, em ERROR, na borda; WARN sem stacktrace para rejeição de negócio; nunca logar body de request/webhook;
- handler global RFC 7807 com traceId na resposta.

O REC-072 deixa de "introduzir" o mascaramento e passa a verificá-lo.

**C5. O wa-terceiro (REC-052) é risco de produto e jurídico.** API não oficial viola os termos do WhatsApp, o número pode ser banido, e isso contradiz "regras da Meta" (§1.6 inv. 6).
→ Tirar do MVP (−5 SP), registrar uma ADR com o motivo e fechar a decisão §5.3 pelo oficial. Manter só a porta `MessagingPort`.

## Importante

**I1. "Visibilidade para o dono" não tem frontend.** O S6 é só backend, o React não tem card nenhum, e o feedback CONVERTIDA/DESCARTADA (REC-044) depende do dono marcar à mão, o que na prática ninguém faz.
→ (a) Card de UI mínima (ou trocar por um resumo semanal no WhatsApp/e-mail para o piloto).
(b) Card de ATRIBUIÇÃO AUTOMÁTICA DE CONVERSÃO: nova venda do mesmo cliente+produto até X dias após ENVIADA marca a ação como CONVERTIDA. É a métrica de ROI que vende o produto.

**I2. Regras de negócio sem critério de aceite que as prove:**
- §1.4 diz "média (ou mediana)" e o REC-031 diz mediana → fixar mediana no §1.4.
- "Confiança suficiente" (REC-041) não está definida. "2 compras = baixa" significa 1 gap só → fixar "gera ação só com ≥3 compras (≥2 gaps)" e incluir um cenário para 2 compras.
- Compras no mesmo dia: o gap zero deve virar uma ocorrência só (definir; hoje o teste só pede "não quebrar").
- Quantidade comprada (2 unidades ≈ ciclo dobrado) não é considerada → documentar como limitação do MVP.
- Elegibilidade de produto: produto não recorrente (acessório, ferramenta) gera ação → precisa de flag por produto ou categoria via `RegraVertical`.
- Janela do REC-041 `proxima−N ≤ hoje ≤ proxima`: cliente atrasado ou job parado por dias perde a ação → definir o tratamento de atraso e o "corte de inativo" (citado no NÃO FAZER e definido em lugar nenhum).
- Máquina de estados do REC-040 sem EXPIRADA/CANCELADA/FALHA: cliente que recompra antes do envio ou dá opt-out depois da geração fica sem estado.
- "Ação ativa idêntica" usa (cliente,produto,tipo,janela), mas "janela" não está definida como chave.
- Falta teto de frequência por cliente: vários produtos vencendo na mesma semana viram N mensagens → consolidar ou limitar.
- "hoje" sem fuso → timezone por comércio.
- Venda cancelada ou devolvida no ERP continua contando: não existe evento canônico de cancelamento → adicionar `venda.canonica.cancelada` no REC-010.

**I3. A janela N tem três fontes:** REC-013 (config do comércio, S1), REC-041 (S4) e REC-062 (`RegraVertical`, S6) → definir no S1 uma fonte única com default documentado; o REC-062 só estende.

**I4. Arquitetura em aberto que os cards já assumem:**
(a) RLS × jobs/consumers/SUPER_ADMIN. O REC-011 resolve o tenant pelo JWT, mas consumers de eventos e o DailyConsolidationJob não têm JWT, e o onboarding lista comércios de vários tenants → definir tenant a partir do comercio_id do evento e uma role/policy para jobs e admin.
(b) Onde roda o wa-cloud? É lib; se o core depende dele no build, isso contradiz o REC-050 ("core compila sem adapter"). E o `/webhooks/whatsapp` mora em qual app? → definir um módulo de montagem ou um serviço.
(c) O comando `mensagem.enviar` está no contrato (REC-010), mas o `MessagingPort` é interface in-process → escolher um dos dois.
(d) A DoD diz "eventos refletidos no contracts", mas eventos internos (venda.persistida, perfil.recalculado, acao.gerada) não são core↔driver → a DoD deve exigir isso só para os eventos de fronteira.

**I5. LGPD só no S7.** Criptografia de PII em repouso e retenção mudam o schema do S2. Também falta o papel controlador/operador (o comércio é o controlador) e o termo/DPA com o comércio, pré-requisito do piloto.
→ Decidir criptografia/coluna no S1 (ADR). Retenção e exclusão podem ficar no S7.

**I6. Custo por mensagem e regras da Meta ignorados no plano.** ~R$0,37/msg de marketing, sem saber quem paga e sem teto. A Meta também limita marketing por usuário, então entrega não é garantida.
→ Card S5: orçamento/teto de envio por comércio, métrica de custo por comércio e status "não entregue por política" tratado como FALHA, sem reenvio.

**I7. Escopo e prazo.** 176 SP / 22 ≈ 8 sprints sem folga, e os cards que faltam (opt-in, onboarding Meta, UI/resumo, atribuição, logging) somam ~30–40 SP → compensar cortando o excesso (abaixo).

## Excesso (pré-piloto, 1 dev parcial)

- **E1.** REC-074: EKS + ArgoCD + Terraform + Amazon MQ + ElastiCache para 1 comércio-piloto → trocar por deploy simples (1 VM/ECS ou PaaS + Postgres gerenciado). K8s vira Fase 2.
- **E2.** Redis só para dedup → tabela com unique no Postgres basta. Remover do MVP.
- **E3.** RabbitMQ + DLQ admin (REC-043) → para o piloto, avaliar driver→core por HTTP de ingestão idempotente (o OpenAPI de ingestão já existe na ADR-0001) e outbox/poller no Postgres para eventos internos. É uma recomendação, não uma ordem; se ficar, que seja sem o endpoint admin no MVP.
- **E4.** REC-073 (2º driver, diff vazio) no S7, junto com o piloto → o driver-stub vai para o S2 como ferramenta (C3) e a prova formal fica para depois do piloto.
- **E5.** REC-012: 4 papéis antes de existir usuário → MVP com SUPER_ADMIN + OWNER; MANAGER/SELLER na Fase 2.
- **E6.** Spring Batch para o job diário → `@Scheduled` + cursor resolve no MVP. Batch só se o backfill pedir.

## Contradições doc × doc × código

- **D1.** Boot 3.3 (CONTEXTO §3, REC-001 "Spring Boot 3.3", "starter-web") × ADR-0002/código 4.1.1 → vale a ADR. Atualizar CONTEXTO §3 e REC-001 (item de ação 3 da ADR, ainda aberto). Boot 4.2 tem GA previsto para nov/2026 → fazer o bump.
- **D2.** CONTEXTO §3/§5.1 "decisão a confirmar" × ADR-0001 aceita → marcar como fechada (item 5 da ADR).
- **D3.** Layout do REC-002 (`asyncapi/` com JSON Schema dentro; openapi-generator "e/ou" jsonschema2pojo) × ADR-0001 (`src/main/schemas/`, só jsonschema2pojo) → reescrever o REC-002 conforme a ADR.
- **D4.** CONTEXTO §2 diz "nenhuma linha de código escrita" e §8 cita arquivos que não estão no repo (desenho-tecnico-plataforma, plano-desenvolvimento-plataforma, roadmap-sprints...) → atualizar ou indicar onde estão.
- **D5.** REC-070 "ativar gate JaCoCo ≥80%" no S7 × DoD/código, que já aplicam o gate no `check` desde o S0 → alinhar o texto do card.
- **D6.** DoD manda PR para `develop`, mas o repo não tinha commit nem branch develop → **resolvido pelo usuário**: `main` + `develop` + `feature/REC-XXX`.
- **D7.** ADR-0002 recomenda RestTestClient; os smoke tests usam `java.net.http.HttpClient` → menor, padronizar no REC-003.

## REC-001 × monorepo atual

**Atende:** settings com os 7 módulos; build-logic com convention plugins (toolchain 21, Spotless, SpotBugs, JaCoCo 80%); version catalog; core-api e driver-bling como apps Boot (webmvc, actuator, springdoc 3.1); os demais como java-library; pacotes corretos; virtual threads ligadas; smoke `@SpringBootTest` + `/actuator/health` UP nos dois apps; README "como rodar"; nenhum domínio ou endpoint de negócio; nenhum acoplamento entre módulos.

**Falta / não verificado:**
- `./gradlew build` não rodou na auditoria. A máquina tem o JDK 21 via SDKMAN; ele só não estava no PATH do shell não interativo dos agentes (resolvido nas regras da bancada).
- REC-001 e CONTEXTO ainda dizem Boot 3.3 (D1).
- Configuração de log ainda inexistente (é escopo do REC-003, mas entra como obrigatória: C4).

## Decisões que continuam com o usuário

1. Estratégia de captação de opt-in (C1) e o papel LGPD/DPA com o comércio.
2. Comerciante-piloto e beachhead (§5.6), com a análise do export do Bling (C2). Contato com o suporte do Bling e o processo da Meta (C3).
3. Escopo do piloto: UI React × resumo por WhatsApp/e-mail (I1); RabbitMQ no MVP ou não (E3); IdP (§5.4).

# Independência entre AM2.5 e Playground — CR13

O AM2.5 é o mod base; Playground é um addon que continua precisando de uma versão
de Ars Magica suportada pelo próprio addon. Independência aqui significa não
exigir nossas modificações no outro projeto para iniciar e aplicar seu conteúdo.

## Responsabilidades

O AM2.5 mantém as correções dos seus próprios recursos: Solar/Lunar, Wave, AoE,
Zone, limites de conjuração, Bless/regeneração, giz nativo e Compêndio. A API de
categorias e entradas permite addons opcionais; nenhum conteúdo do novo addon
Arcane Engineering foi incluído no núcleo.

O contador cinza adicional do Oculus, as mensagens de descoberta e a política
de gastar pontos Silver livres somente em talentos pertencem ao Playground
mantido. A CR13 remove esses comportamentos incondicionais do núcleo. Sem um
addon que os aplique, o Oculus mantém os três contadores e as compras nativas.
Habilidades aprendidas em saves anteriores permanecem conhecidas.

## O quarto saldo do protocolo

O upstream já possui `SkillPointTypes.SILVER`, quatro posições em `spellPoints`
e quatro inteiros no pacote de sincronização. O receptor lia somente três.
A CR13 conserva a correção dessa leitura como correção do protocolo nativo.
Ela não cria um contador visível, não concede pontos e não exige outro mod.

## World Fusion e o crash de inicialização

O Playground mantido anterior registrava um listener cuja assinatura usava
diretamente `am2.api.events.WorldFusionEvent`, acrescentado na CR5. Um AM2 sem
essa API, como CR2 ou o upstream, falhava ao carregar o listener. A API era
opcional para o núcleo, mas a assinatura tornou-se obrigatória para o addon.

A API genérica permanece no AM2.5 para não quebrar extensões já distribuídas.
O ritual original não exige ouvintes e segue seu fluxo normal sem Playground.
A correção da dependência no addon deve usar seu próprio mecanismo de extensão,
sem exigir a classe acrescentada na CR5. Não basta capturar a exceção e deixar a
consagração sem funcionamento.

## Compatibilidades opcionais preservadas

O giz do AM2.5 consulta os planos de rituais do Playground somente após confirmar
que o addon está instalado. Usa reflexão e tipos da API do AM2; não possui imports
de classes do Playground. Sem o addon, o catálogo contém apenas os rituais nativos.
Essa consulta não altera a progressão nem torna o addon obrigatório.

## Verificação

`VerifySilverPointsJar` inspeciona o JAR final e confere que os quatro saldos são
enviados, que o quarto é recebido, e que o núcleo não contém o contador extra,
o tooltip de descoberta ou a política de compras do addon. Também verifica a
preservação da API opcional World Fusion e a ausência de referências diretas a
classes Playground no tratamento das habilidades e na tela nativa.

O build inclui os verificadores existentes de magias e giz. Esses checks não
substituem os testes de inicialização e jogabilidade entre versões diferentes.
Os resultados locais e do GitHub Actions devem ser registrados separadamente.

### Build local

Em 2 de novembro de 2026, o build local da CR13 concluiu com sucesso. Passaram
os quatro verificadores do JAR: limites de magias, escalonamento Solar/Lunar,
separação da política Silver e registro das ações do giz.

JAR `AM2.5-LTS-1.6.11-LE-CR13.jar`, SHA-256:

`ee2c49d0b2f4b95014bb07f496396cd9503813183e4c5e329e7c053ac17850b4`

Esse resultado comprova a compilação e os checks estruturais locais. Os testes
gráficos das combinações de versões e a execução do GitHub Actions ainda devem
ter seus próprios resultados conferidos; não estão atestados por este registro.

Participação substancial do OpenAI Codex na investigação e implementação desta
separação. Autoria, histórico e licenças originais do AM2.5 foram preservados.

## Matriz de integração concluída

Os sete cenários gráficos locais e as regressões concluíram com sucesso. Veja a
[matriz completa, versões, hashes e limites](https://github.com/Carairritante/am2playground/blob/correcoes/maintenance/docs/INDEPENDENCIA-AM2-PLAYGROUND.md).
O PG corrigido funcionou também com LTS original sem a API customizada, CR2 e
Mithion original (neste último, com as avançadas indisponíveis por escolha do usuário).

# Antes e depois da manutenção

Resumo das correções e melhorias instaladas até AM2.5 CR8 e Playground
`advanced-silver-progression`. Os históricos originais e os commits da manutenção
permanecem nas branches `correcoes`, sem reescrita.

[Baixar o slide único editável](Resumo-antes-depois-AM2.pptx)

![Antes e depois das correções](Resumo-antes-depois-AM2.png)

## Atualizações na ordem em que foram publicadas

Os primeiros commits de importação agrupam os reparos investigados anteriormente
na sessão. A tabela aponta os commits funcionais e de CI na ordem cronológica de
publicação. Ajustes menores de documentação continuam no histórico Git completo.

| Data (Manaus) | Atualização | Commit |
| --- | --- | --- |
| 31/10/2026 03:23 | AM2.5: proteções Lunar/AoE/Zone, escala Solar/Lunar, Wave até 128 blocos e limites de Lightning/Explosion/Dig | [cc5788f](https://github.com/Carairritante/ArsMagica2/commit/cc5788f) |
| 31/10/2026 03:23 | Playground: patches do caldeirão Witchery, notas de Void Paradox e recompensa do portal do ritual | [3825e93](https://github.com/Carairritante/am2playground/commit/3825e93) |
| 31/10/2026 03:28 | Playground: CI das ferramentas com JAR e SHA-256 separados, sem ZIP externo | [45e4fe9](https://github.com/Carairritante/am2playground/commit/45e4fe9) |
| 31/10/2026 03:34–03:36 | AM2.5: CI do mod e verificadores dos patches no JAR gerado | [be0a69e](https://github.com/Carairritante/ArsMagica2/commit/be0a69e), [d5f786e](https://github.com/Carairritante/ArsMagica2/commit/d5f786e) |
| 31/10/2026 04:11 | AM2.5 CR3: saldo Silver sincronizado e contador cinza no Oculus | [c34bcf3](https://github.com/Carairritante/ArsMagica2/commit/c34bcf3) |
| 31/10/2026 04:11 | Playground: ponto Silver livre na orb do portal do ritual | [6246608](https://github.com/Carairritante/am2playground/commit/6246608) |
| 31/10/2026 12:48 | AM2.5 CR4: pontos Silver livres compram somente talentos, na interface e no servidor | [45e4c17](https://github.com/Carairritante/ArsMagica2/commit/45e4c17) |
| 31/10/2026 12:48 | Playground: corrigido o ciclo infinito de regeneração de mana com Archmage | [3da1fa6](https://github.com/Carairritante/am2playground/commit/3da1fa6) |
| 31/10/2026 13:14 | Playground: recompensa final do portal passa a ser somente 1 Silver, sem pontos RGB nem habilidades | [7d03954](https://github.com/Carairritante/am2playground/commit/7d03954) |
| 31/10/2026 18:28 | AM2.5 CR5: evento de extensão do ritual World Fusion no servidor | [836a0d5](https://github.com/Carairritante/ArsMagica2/commit/836a0d5) |
| 31/10/2026 18:28 | Playground: sete descobertas avançadas, requisitos pessoais e Compendium sem revelar combinações | [f18f627](https://github.com/Carairritante/am2playground/commit/f18f627) |
| 31/10/2026 19:32 | AM2.5 CR6: novo Giz do Arquiteto Arcano, guia no survival e montagem no criativo | [85d46d5](https://github.com/Carairritante/ArsMagica2/commit/85d46d5) |
| 31/10/2026 23:41 | AM2.5 CR7: seleção e ações do giz corrigidas também no servidor integrado | [c947e79](https://github.com/Carairritante/ArsMagica2/commit/c947e79) |
| 01/11/2026 00:43 | AM2.5 CR8: compatibilidade opcional do giz com rituais e infusões do Playground, sem alterar seu JAR | [9b4f2df](https://github.com/Carairritante/ArsMagica2/commit/9b4f2df) |

## Estado atual e limites

- A orb do Crimson Portal criado pelo ritual concede **1 Silver**, sem pontos
  coloridos ou habilidades. A pérola original continua no drop. Portais naturais
  não recebem essa recompensa extra.
- A proposta posterior de 5 pontos de cada cor + 1 Silver foi cancelada, não foi
  instalada e não faz parte dos patches publicados.
- O ritual original não invoca o portal no Pacífico. Não alteramos essa regra nem
  a dificuldade do jogo do usuário.
- Level, Nullify, Coven Connection, Flowering Nature, Diabolic, Ethereal e Sacrament
  recebem progressão por descobertas, iniciação World Fusion e requisitos pessoais.
  O Compendium informa requisitos extras e preserva o segredo das combinações.
- As consultas verificam coven próprio com 3 bruxas e pelo menos 200.000 de mana
  disponível nos itens Botania carregados, sem consumir bruxas ou mana Botania.
  Nullify exige o Ender Guardian pessoal. Sacrament inclui consagração pessoal.
- No pack completo de teste, o giz reconhece 30 rituais nativos, 32 rituais do
  Playground e 33 infusões de buffs. Quantidades dependem dos mods instalados.
  Sobrevivência recebe projeção e conferência de materiais, montagem é exclusiva
  do criativo e não ativa o ritual.
- A CR8 avisa sobre três ritos com suspeita de falha original de ativação. Não
  conserta esses ritos. Caldeirão e ritos Witchery não fazem parte do catálogo do giz.
- O fork público do AM2.5 contém o código do mod. O fork público do Playground
  contém recursos e ferramentas de manutenção, não o JAR completo modificado.
  Esse JAR continua no arquivo privado já existente. O Actions do Playground
  gera ferramentas, não uma versão jogável completa do addon.
- Créditos e licenças originais preservados. A participação substancial do
  OpenAI Codex está documentada nos arquivos de divulgação de autoria.

## Colaboração

Abra PRs para `correcoes` nos forks de
[AM2.5](https://github.com/Carairritante/ArsMagica2/tree/correcoes) e
[Playground](https://github.com/Carairritante/am2playground/tree/correcoes).
O CI continua automático por push e PR, com JAR e checksum separados, sem ZIP externo.

## Referências da manutenção

- [Changelog do AM2.5](https://github.com/Carairritante/ArsMagica2/blob/correcoes/CHANGELOG.md)
- [Compatibilidade do giz na CR8](https://github.com/Carairritante/ArsMagica2/blob/correcoes/docs/COMPATIBILIDADE-RITUAIS-CR8.md)
- [Patches do Playground](https://github.com/Carairritante/am2playground/tree/correcoes/maintenance/src)
- [Resultados dos testes do Playground](https://github.com/Carairritante/am2playground/blob/correcoes/maintenance/RESULTADOS.md)

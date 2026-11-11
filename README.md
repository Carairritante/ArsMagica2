Ars Magica 2.5
==========

## Versão de manutenção 1.6.11-LE-CR40 (branch `correcoes`)

A série CR32–CR40 evolui os efeitos atmosféricos e círculos de conjuração no
cliente com foco em Firestorm (arcos de lava no céu), Blizzard (placas
hexagonais), Storm + Lightning (nuvem e funil 3D em estilo anime compartilhados
com `OrbitalStormVFX` e correção de seleção com IDs `+1000` / Lunar) e Starfall
(portal circular progressivo de até 96 m sincronizado aos círculos, pilar
celestial de 76 blocos, câmara de 16 fitas verticais, ondas de choque no solo,
cometas 3D com rastro e otimizações de FPS via tabela trigonométrica e redução
de vértices na CR40), mantendo compatibilidade de metadata `1.6.7` em
`mcmod.info` e limites de servidor/protocolo preservados.
[Formatos elementais e validação](docs/CEUS-ELEMENTAIS-CR31.md).

## Versão local anterior 1.6.11-LE-CR30

A CR30 intensifica a atmosfera com nuvens em espiral, um funil, fissuras no céu,
auroras e quedas elementais mais densas. Mantém limites de carga e restauração
do clima natural. Validada localmente no Forbidden e em servidor dedicado.
[Visual apocalíptico e validação](docs/ATMOSFERA-APOCALIPTICA-CR30.md).

## Versão local anterior 1.6.11-LE-CR29

A CR29 corrige as quedas de fogo, gelo e estrelas durante a terceira fase de
conjuração, com trajetórias, impactos e clima temporário de desenho. Preserva
as texturas e a correção do Freecam. Continua local, sem publicação no GitHub.
[Quedas elementais, atmosfera e validação](docs/ATMOSFERA-E-QUEDAS-CR29.md).

## Versão local anterior 1.6.11-LE-CR28

A CR28 mantém os círculos e suas partículas no personagem ao usar câmera livre,
preservando as paletas por afinidade e as animações. Validada em servidor dedicado
e numa cópia do Forbidden; continua local, sem publicação no GitHub.
[Correção do Freecam e auditoria](docs/FREECAM-E-AUDITORIA-CR28.md).

## Versão local anterior 1.6.11-LE-CR27

A CR27 controla o custo dos círculos de conjuração e protege a leitura de dados
visuais inválidos, mantendo as novas texturas e o livro animado da CR26. A versão
continua local, sem publicação no GitHub nesta etapa.
[Segurança e validação dos VFX](docs/VFX-SEGURANCA-CR27.md).

## Animações da CR26

A CR26 corrige a abertura, o enquadramento e o glifo dos livros de magia em
primeira e terceira pessoa, incluindo o Livro Arcano do Guardião. Esta atualização
foi validada localmente, sem publicação no GitHub por enquanto.
[Detalhes do livro e compatibilidade de versão](docs/LIVRO-ANIMADO-CR26.md).

## Correção anterior: CR25

A CR25 corrige a criação de efeitos de área, fusões de Explosion/Lightning e
Storm quando não há um conjurador disponível. O efeito usa um contexto neutro,
como os efeitos de área nativos, mantendo a identificação do conjurador real
quando ele existe. A CR26 preserva essa correção e a identificação CR no Forge e
no nome do JAR; o campo legado de `mcmod.info` mantém a identificação da base para
preloaders antigos. [Correção e validação da CR25](docs/CORRECAO-SERVIDOR-CR25.md).

O CI também verifica a ausência de referências gráficas no handler comum de
armaduras e a consistência da versão, usando o JAR reofuscado que será entregue.

[Slide antes/depois e histórico cronológico das atualizações](docs/RESUMO-ANTES-DEPOIS.md)

Fork mantido por [Carairritante](https://github.com/Carairritante), com participação
substancial do **OpenAI Codex** na investigação, implementação, verificações e
documentação das alterações de manutenção.

A base desta versão é [ArsMagica2 LE v1.6.11-LE, de DrParadox7](https://github.com/DrParadox7/ArsMagica2/tree/v1.6.11-LE),
continuação do AM2.5 LTS de TCLProject e do AM2 original de Mithion. O histórico,
a licença e os créditos originais foram preservados.

- [Builds e artefatos do Actions](https://github.com/Carairritante/ArsMagica2/actions)
- [Correções e mudanças de comportamento](CHANGELOG.md)
- [Reformulação de VFX, fusões e estabilização multiplayer na CR24](#destaques-da-versão-1611-le-cr24)
- [Expansão da Box of Illusions com mobs vanilla, sons, animações e dragão na CR22](docs/BOX-OF-ILLUSIONS-CR22.md)
- [Estabilização da Box of Illusions e item na mão na CR21](docs/BOX-OF-ILLUSIONS-CR21.md)
- [Vara Arcana e visual do Shrunken na CR20](docs/VARA-SHRUNKEN-CR20.md)
- [Isolamento das molduras e investigação da desconexão na CR19](docs/CORRECAO-MOLDURAS-CR19.md)
- [Commits e validação local da CR14 à CR18](docs/VALIDACAO-CR18.md)
- [Menu e HUD do Giz do Arquiteto Arcano](docs/MENU-GIZ-CR16.md)
- [Separação entre o AM2.5 e o Playground na CR13](docs/INDEPENDENCIA-PLAYGROUND-CR13.md)
- [Correção das prévias de criaturas na CR11](docs/CORRECAO-COMPENDIUM-CR11.md)
- [API opcional de categorias do Compêndio na CR12](docs/CATEGORIAS-COMPENDIUM-CR12.md)
- [Compêndio completo em português e troca de idioma na CR10](docs/TRADUCAO-COMPENDIUM-CR10.md)
- [Correção de Bless e Mana Regen na CR9](docs/CORRECAO-BLESS-CR9.md)
- [Participação da IA e autoria original](AI_DISCLOSURE.md)
- [Verificações e limites da validação](docs/VALIDACAO-CR2.md)
- [Validação da sincronização e dos pontos Silver na CR3](docs/VALIDACAO-CR3.md)
- [Registro histórico das compras de talentos na CR4](docs/VALIDACAO-CR4.md)
- [Giz do Arquiteto Arcano: uso e validação da CR6](docs/GIZ-ARQUITETO-CR6.md)
- [Correção da seleção do giz em um jogador na CR7](docs/CORRECAO-GIZ-CR7.md)
- [Compatibilidade opcional do giz com rituais na CR8](docs/COMPATIBILIDADE-RITUAIS-CR8.md)
- [Ferramentas de verificação](tools/verification/README.md)

Esta manutenção inclui proteções para Lunar, AoE e Zone, progressão gradual de
Solar/Lunar, limites de conjuração para Lightning Strike, Explosion e Dig, redução
de partículas e limite de **128 blocos para o percurso da Wave**, preservando sua
largura. Bless permanece habilitado.

A CR13 separa as regras específicas do Playground do núcleo. O AM2.5 sozinho
mantém os três contadores originais do Oculus e suas regras nativas de compra.
O contador cinza, suas mensagens e a regra de gastar pontos Silver livres somente
em talentos passam a ser responsabilidade da manutenção do Playground, que aplica
seus ajustes quando está instalado. Conhecimentos já salvos são preservados.

A correção da leitura do quarto saldo continua no AM2.5: esse campo já existia e
era enviado pelo protocolo original, mas não era lido. Isso não acrescenta uma
moeda, um contador visível nem requisitos de outro mod. A API opcional World Fusion
da CR5 permanece para compatibilidade com extensões já distribuídas; o AM2.5 não
precisa do Playground para executar o ritual nativo.

A CR6 acrescenta o **Giz do Arquiteto Arcano**, uma ferramenta reutilizável para
planejar os rituais de magia nativos. No survival, projeta o desenho e mostra
materiais e reagentes faltantes; no criativo, pode montar a estrutura. A ativação
dos rituais continua sendo feita pelas magias e condições normais.

A CR7 corrige a seleção, rotação, limpeza e montagem do giz em mundos de um
jogador. A fila de ações passa a ser registrada também no servidor integrado,
e o menu foi testado num cliente real com troca de mensagens e sincronização.

A CR8 acrescenta compatibilidade opcional com os rituais do AM2 Playground. O giz
lê os desenhos e ingredientes fornecidos pelo addon instalado, sem depender dele
para iniciar. No ambiente completo de teste, foram reconhecidos 32 rituais e 33
infusões de buffs adicionais. O guia mostra orientações legais, poções e Phylactery
preenchida; a construção continua exclusiva do criativo e não executa rituais.

A CR14 amplia o catálogo para 41 modelos nativos e permite pesquisar as partes
das magias pelos nomes em inglês e pelo idioma do jogo. A CR15 renova a HUD do
giz. A CR16 aplica o mesmo azul-escuro ao menu, com runas do Wizard Chalk,
seleção iluminada e navegação adaptada ao tamanho da janela. A montagem
automática permanece exclusiva do criativo.

A CR17 posiciona a HUD do giz no canto superior esquerdo e torna seu fundo
mais transparente, preservando a leitura dos textos e o tema do menu.

Na CR18, a engrenagem no menu do giz abre os ajustes da HUD: arraste o painel
para reposicioná-lo, use **− / +** para mudar o tamanho ou a transparência do fundo
e **Restaurar** para voltar ao padrão. As preferências ficam salvas localmente
ao fechar os ajustes.

### Destaques da Versão 1.6.11-LE-CR24

A CR24 traz uma reformulação completa de efeitos visuais (VFX), otimização de desempenho e correções críticas de compatibilidade multiplayer:

- **Tempestade Orbital (`Storm` + `LightningDamage`)**:
  - Artilharia orbital com raio de 10 chunks (160 blocos) e proteção contra carregamento forçado de chunks (`world.blockExists`).
  - Efeito volumétrico no céu com funil de tempestade, anéis de plasma ionizado e faíscas dinâmicas no solo.
  - Sincronização da duração da tempestade com o container `Zone` e encerramento sincronizado com o `Lightning Strike`.
  - Frequência acelerada de raios com fuzilamento focado em criaturas hostis.
- **Lightning Strike (4 Tiers de Fusão Visual)**:
  - Nuvens escuras volumétricas 3D no céu, chuva de raios zigue-zague 3D, faíscas de impacto no chão e pilar de plasma.
  - Efeito de fogo incendiando entidades atingidas.
  - Fusão visual por tiers: Raio Individual (T1) -> Tempestade Concentrada (T2) -> Dragão do Trovão (T3) -> Dragão Divino Primordial (T4).
- **Rework de VFX e Fusões Mágicas**:
  - **Explosion**: Fusão unificada em 64 blocos com escavação suave, eliminação do lag gerado por partículas de fumaça vanilla, onda de choque geométrica e pilar de erupção.
  - **Grow**: Mandala botânica sagrada em 4 tiers concêntricos sem poluição de partículas.
  - **Starfall**: Queda celestial de estrela cadente, disco de acreção cósmico, supernova e agrupamento de estrelas com dano acumulativo.
  - **Blizzard e Firestorm**: Conjuntos arcanos rúnicos hexagonais e circulares limpos, removendo caixas de colisão físicas invisíveis.
  - **Mana Shield**: Visual minimalista de "segunda pele" hexagonal com runas corporais reativas a dano.
- **Balanceamento de Formas**:
  - Limite de 3 pulsos para o formato `Wave` quando combinado com magias de alto impacto (`Lightning Strike`, `Explosion` e `Dig`), prevenindo sobrecargas severas de blocos.
- **Estabilização de Servidores Dedicados e Multiplayer**:
  - Isolamento rigoroso de métodos de renderização de clientes (`ArmorClientEventHandler` cliente-only vs `ArmorEventHandler` common-safe), eliminando `ClassNotFoundException` no carregamento de servidores dedicados.
  - Restauração de estados OpenGL em renderizadores para compatibilidade plena com Angelica e shaders.
  - Total interoperabilidade e compatibilidade retroativa com versões clássicas e de manutenção do AM2PlayGround.

O Actions compila o código desta branch a cada push e pull request. Nas execuções
concluídas com sucesso, a seção **Artifacts** disponibiliza o JAR reobfuscado e o
arquivo **SHA256SUMS**, separadamente e sem ZIP externo, por 14 dias.
O checksum identifica o arquivo daquela execução; o hash do JAR de teste anterior,
registrado em `docs/VALIDACAO-CR2.md`, pertence a outro artefato.
Use uma cópia do modpack para validar o novo build no cliente e no servidor.

Para compilar localmente, use JDK 17 para executar o Gradle e JDK 8 para o mod:
`./gradlew build`. A compilação usa Gradle 8.8 e RetroFuturaGradle 1.4.9,
preservando os nomes de métodos e campos fornecidos pelo Forge original.

### Pull requests

Esta branch `correcoes` concentra a manutencao experimental do nosso servidor.
Crie uma branch ou um fork com suas mudancas e abra o PR com destino a
`Carairritante/ArsMagica2:correcoes`. Os commits e creditos de origem foram mantidos.
Nao ha compromisso de suporte geral ou de novas funcionalidades.

---

Ars Magica 2.5 - Carrying the Torch of True Magic

## New Features (TL;DR)

- Re-added Staffs
- Re-added Caster Blocks and Runes
- Added New Components, Shape, Modifiers and Talents
- Added an enormous amount of new spell icons
- Added compat with Not Enough Items and Magic Bees
- Added recipes for multiple previously uncraftable items
- Added multiple new items, blocks, contraptions and rituals with utility
- Added and changed multiple core mod mechanics
- Re-added and implemented multiple lost and unfinished features
- Refined Altar, Burnout, Leveling and Bosses
- Improved/optimized startup time
- Fixed missing lang entries
- Fixed a plethora of bugs and crashes
- See the Arcane Compendium in-game: for a detailed and thorough documentation
- See the commits and release notes: for a better and more specific summary

### License
This mod is open sourced under the Creative Commons 3.0 Attribution Non-Commercial License
https://creativecommons.org/licenses/by-nc/3.0/legalcode

Summary
https://creativecommons.org/licenses/by-nc/3.0/

You are free to:
* Share — copy and redistribute the material in any medium or format
* Adapt — remix, transform, and build upon the material
* The licensor cannot revoke these freedoms as long as you follow the license terms.

Under the following conditions:
* Attribution — You must give appropriate credit, provide a link to the license, and indicate if changes were made. You may do so in any reasonable manner, but not in any way that suggests the licensor endorses you or your use.
* NonCommercial — You may not use the material for commercial purposes.
* No additional restrictions — You may not apply legal terms or technological measures that legally restrict others from doing anything the license permits.

The exceptions to this license are textures, majority of which are property of D3miurge, licensed to this mod for it's use.
More information cam be found here: http://www.minecraftforum.net/user/2252536-d3miurge/#am2

Spell icons used include Painterly Spell Packs 1 through 4, courtesy of J. W. Bjerk (eleazzaar) at opengameart.com.

Some icons used courtesy of Ails http://www.alteredgamer.com/rpg-maker/100053-vx-custom-icons

Some textures used have been adapted from the Sanity texturepack by Alvoria

https://www.curseforge.com/minecraft/texture-packs/alvorias-sanity

Parts of models by Staralighto have also been used.

Sounds are courtesy of opengameart.com and freesound.org, and licenses are that of their respective owners.

## CR44 — Sobrecarga sem morte (21/11/2026)

- Burnout excessivo agora causa Silêncio por 600 ticks, sem dano, Wither, raios, teleporte ou morte direta. Custos e recuperação de exaustão permanecem.
- O setter usado por dados salvos/sincronização apenas limita valores; punição pertence ao custo real via addBurnout. Pulsos não renovam a duração: nova ocorrência exige recuperar até80% e intervalo600ticks. Silêncio existente é preservado.
- Documentação de exaustão atualizada no Compêndio PT/EN. Alteração somente AM2.5, mantendo os mods separados e CR44.

## 21/11/2026 — CR44: corpo encolhido com Futura3D

- Corrigido o corpo que permanecia grande enquanto o nome e a caixa diminuíam. O AM2.5 aplica sua escala na matriz do renderizador substituto opcional, incluindo armadura/localizadores, sem editar o Futura3D.
- Mantidos animações, física, câmera e renderização vanilla. Integração sem dependência obrigatória, idempotente, validada nos dois binários locais do Futura3D. Resultados em docs/SHRINK-FUTURA-CR44.md e relatório da tarefa.

# Alterações de manutenção do AM2.5 LE

### Recall com intervenções dimensionais — CR44 — 2026-11-21

- Recall passa a reconhecer apoios dimensionais na etapa ativa da magia e usa a dimensão e as coordenadas da marca do conjurador como destino para jogadores.
- As intervenções usadas como apoio não executam seus teleportes próprios; a transferência só é confirmada após sucesso da etapa, preservando validações e bloqueios opcionais dos addons, independentemente da ordem dos componentes.
- Transferência ao final do tick, sem portais/plataformas ou escala de coordenadas, com validação de marca/dimensão, expiração da fila, respeito a Distorção Astral, montarias e Escudo de Mana. Retorno do End e recusa de spawn recebem tratamento próprio; flags de sonho do Witchery são opcionais e reversíveis em falha.
- Recall na mesma dimensão e rituais existentes preservados. Compatibilidade inteiramente no AM2.5, sem editar ou exigir o Playground em disco. Guia PT/EN com quatro folhas e sem revelar receitas de descobertas Silver.

### Mana Shield: proteção por mana e remoção dos efeitos visuais — CR44 — 2026-11-20

- Removidas a cobertura corporal, a camada de primeira pessoa, a textura exclusiva e os clarões/partículas de impacto do escudo; ícone e duração preservados.
- Proteção independente da poção, controlada no servidor, com valores I–V de 50 / 37,5 / 25 / 17,5 / 12,5 mana por ponto de dano efetivo. Frações, absorção parcial e regeneração normal preservadas.
- Resistências adicionais: efeitos negativos e controle (1% da mana máxima, mínimo25), desarme coberto (2%, mínimo50), dissipação coberta (5%, mínimo100) e morte direta coberta (25%, mínimo250). Escritas da mesma origem/categoria no mesmo tick compartilham uma taxa; dano é cobrado separadamente.
- Guardas comuns de vida, efeitos, fogo, movimento e morte; compatibilidade pontual com desarme do Witchery, telecinese do Wizardry e os despachantes do PlayerAPI usados pelo Galacticraft. Nenhum JAR estrangeiro é alterado no disco.
- Leite, dissipação própria, teleportes próprios reconhecidos, inventário e custo intencional de vida do Life Tap preservados. Dissipação sem mana encerra a proteção; aplicações novas de poção por addons continuam reconhecidas.
- Life Tap contra um alvo protegido só devolve mana pela parcela de vida efetivamente paga; conjuração própria mantém seu custo em vida, sem desativar a proteção de outras entidades durante o ataque.
- Nove folhas do Compêndio em português e inglês explicam custos, funcionamento e limites. Sistemas privados de outros mods e escrita direta em campos podem contornar os pontos interceptados; não há promessa de imunidade universal.

### Guia do Tornado e descoberta nível 90 — CR44 — 2026-11-20

- Página do Tornado expandida em português e inglês com folhas curtas, títulos coloridos e quebras nativas do Compêndio: escala F1–F5, modos Solar/Lunar, elementos, modificadores, dano, terreno, formas e renovação.
- Descoberta de Magias Douradas passa a exigir nível mágico 90 no servidor; avisos do Oculus e texto do Compêndio atualizados. Guardiões, Silver de Ofensa e habilidades já aprendidas preservados.
- Velocidade Acrescentada incluída na lista de modificadores do Tornado. Jogabilidade do vórtice preservada.
- Modificadores do Tornado organizados em linhas na folha direita, incluindo os adicionais presentes no pack, sem atravessar o texto da folha esquerda.

### Oculus: remoção da borda externa — CR44 — 2026-11-20

- Removido o contorno externo extra das habilidades Golden, que aparecia branco e espesso. Moldura original do ícone, cores de disponibilidade, requisitos e aprendizagem preservados.

### Limpeza dos círculos antigos — CR44 — 2026-11-20

- Retirados 101 recursos sem referências dos antigos círculos, liberando aproximadamente 13,2 MB no JAR.
- Modelos verticais, paletas e efeitos atmosféricos mantidos. Nenhuma alteração de código ou de regras das magias.

### Círculos verticais Mahou — CR44 — 2026-11-20

- Substituídas as três rotas antigas dos círculos por geometria vertical dos 16 modelos aprovados, com atlas e runas próprios por afinidade/SuperTier.
- Afinidades normais limitadas às camadas de 9m e 18,5m; a camada principal de 32m e seu halo pertencem aos SuperTiers.
- Centro aberto, sem manequim, discos preenchidos ou pilar central; abertura progressiva com ponta luminosa e partículas restritas às bordas.
- Geometria validada e preparada uma vez, fases completas reutilizadas no desenho; limites de distância/conjuradores e captura de posição corporal mantidos.
- Efeitos atmosféricos dos SuperTiers preservados. Nenhuma mudança em custos, dano, progressão, Playground ou Segundo Cérebro.

### Agrupamento de drops do Tornado — CR44

- Drops de terreno e de quebras vizinhas síncronas passam pelo consolidador existente.
- Loot permanece no chão para empilhar; a física do vórtice processa criaturas e blocos físicos, preservando os debris visuais.
- Nenhum item antigo do mundo é removido por esta atualização.

### Correções do Tornado e reintegração da Bússola — CR44 — 2026-11-19

- Tornado considera somente os componentes e modificadores da etapa executada;
  Speed não conta mais como Radius no cálculo F1–F5. Prévia e servidor seguem a mesma etapa.
- Conjurações de donos diferentes não se fundem; atualizações do mesmo dono
  preservam a duração vigente. Falha de criação de efeito é propagada ao componente.
- Movimento, homing, fogo, poções e arcos verificam PvP e equipe antes de atingir jogadores.
  Colisão de blocos arremessados não contorna PvP desligado ou proteção de equipe.
- Tentativas recusadas de alteração/spawn também consomem o orçamento por ciclo,
  evitando amplificação de callbacks em áreas protegidas ou sob veto de entidades.
- Alterações de terreno, gelo e fogo verificam proteção de spawn e BreakEvent.
  Blocos valiosos só são removidos após a entidade correspondente ser aceita e estar viva.
- Tromba d'água mantém orçamento de ar por alvo enquanto ele permanece afetado;
  a reposição normal de ar em terra não elimina o progresso. Imunidades respiratórias,
  mortos-vivos e golens são respeitados; dados temporários são descartados.
- Parcela principal respeita armadura, com penetração mágica de25%/40% nos níveis altos.
  Tipos elementais e construtores antigos preservam resistências dos guardiões
  e o comportamento das outras magias. Mana, receitas e multiplicação por AoE preservadas.
- Reintegradas as correções das dimensões/metadata, filtros e prévias da Bússola
  validadas em18/11, sem perder o Tornado e seus efeitos visuais.
- Nome CR44 preservado; identificar esta compilação pelo SHA-256 da entrega.


### True Soulbound anti-dupe contra mobs ladrões — CR44 — 2026-11-18

- `TrueSoulboundHandler`: adicionada purga genérica (cacheada por classe) de itens
  True Soulbound roubados para inventários internos de mobs (`ItemStack[]`, `IInventory`
  e campos escalares mutáveis `ItemStack`, como `BandP.MymainInventory` do OreSpawn).
- Executada no resgate imediato (`reconcileLiveInventorySnapshot` num raio de 16 blocos),
  em `onLivingUpdate` e em `onNonPlayerLivingDeath` (`EventPriority.HIGHEST`, antes de
  `dropFewItems`), impedindo que mobs que descartam NBT ao dropar (como o Criminal)
  dupem livros/itens True Soulbound vazios ao morrer.

### Prévias visuais das estruturas na Bússola — CR44 — 2026-11-18

- Substituído o ícone genérico de bússola na lista de `GuiAstralCompass` por
  miniaturas isométricas `24×24` exclusivas de cada estrutura (102 texturas `256×256`
  em `textures/gui/compass_previews/` cobrindo Vanilla, AM2, os 16 Pylons + 6 ruínas
  do ChromatiCraft, Wizardry, Thaumcraft, os 47 construtores do OreSpawn e Twilight Forest).
- Adicionado painel lateral de prévia nítida com dica visual de reconhecimento
  (`am2.astral_compass.desc.*` em `en_US`/`pt_BR`) e modal de zoom expandido até
  `256×256` ao clicar na prévia, no botão `+` ou com duplo clique na linha.

### Intervalos de pedidos no servidor — CR44 — 2026-11-17

- Bate-estacas: o intervalo mínimo de cinco ticks entre tentativas de início
  também vale após parar o teste ou falhar por falta de espaço. Tentativas
  rejeitadas durante o intervalo não adiam o próximo início permitido.
- Bússola: buscas rejeitadas também respeitam o intervalo de vinte ticks,
  registrado antes das verificações de progressão e da descoberta do catálogo.
  Alvos inexistentes não podem repetir essa leitura a cada tick. Rejeições
  continuam sem custo de mana; cancelamento e consulta do catálogo preservados.
- Acrescentado probe descartável de regressão para servidor dedicado com dois
  jogadores simulados, criação/remoção de manequins, pedidos repetidos, recuperação
  após os intervalos e buscas pela fila com cobrança exata de mana.
- Nome CR44 mantido. Índice de 8.192 locais e demais mods não alterados.


### Apresentação dos instrumentos — CR44 — 2026-11-17

- Novas poses de primeira/terceira pessoa da bússola e renderer de item próprio
  para o bate-estacas, separado do modelo colocado no mundo.
- Ícones vetoriais próprios de inventário/hotbar: bússola com agulha e pedestal
  de medição com núcleo luminoso, sem depender da miniatura do modelo completo.
- Telas internas com painel e botões de instrumentos, cabeçalhos próprios e
  leitura do medidor. Presets/alternâncias acompanham o estado sincronizado.
- Ajustes de posição/escala/transparência preservados. Sem edição de meshes,
  texturas, mecânica, protocolos, custos ou outros mods. Apenas assemble; teste
  visual em jogo fica com o usuário. Nome CR44 mantido.

### Desenho exclusivo da HUD da Bússola — CR44 — 2026-11-17

- Substituída somente a renderização da HUD por mostrador circular de navegação:
  rosa dos ventos e pontos cardeais orientados pelo jogador, agulha de duas faces,
  contrapeso e marca externa do alvo. Paleta azul escura/ciano com metal dourado.
- Mantidos os limites 264×96, posição/tamanho/transparência e seus controles.
  Informações continuam presentes; origem/estado podem ocupar duas linhas.
- Estado visual próprio para alvo em outra dimensão ou ponto já alcançado.
  Sem mudanças em busca, mana, rituais, modelos ou outros mods.
- Somente assemble, sem execução do jogo ou testes nesta etapa; mantido CR44.

### Localização ChromatiCraft na CR44 — 2026-11-17

- Incluídos pylons de qualquer cor e por cor, consultando PylonGenerator.colorCache
  e tiles existentes em chunks carregados, sem alterar o ChromatiCraft.
- Corrigida a rejeição de ruínas V33 com SUCCESS e generatedLocation nula:
  coordenada exata pelo controlador carregado ou região aproximada pelo cache,
  com altura desconhecida em vez de assumir Y=0 real.
- Novo adaptador opcional com leituras limitadas, snapshots e indicação de
  região aproximada em GUI/HUD/histórico. Suporte à exclusão do alvo regional.
- Apenas compilação, sem testes ou abertura do jogo nesta etapa. Mantido CR44.

### Melhorias da localização de estruturas na CR44 — 2026-11-17

- Índice aceita alturas negativas, distingue posições verticais e usa consultas
  por tipo com snapshots reutilizados, mantendo o limite de 8.192 registros.
- Vilas e templos consultam diretamente candidatos por região, sem varrer todos
  os chunks nem alterar o RNG do mundo. Geradores são coletados uma vez por busca.
- Raios de 2.000/5.000/10.000 blocos, aplicados também aos registros conhecidos;
  Encontrar outra ignora o alvo autenticado atual e mantém o custo normal.
- Recuperação adicional de bounding boxes já existentes nos caches de geradores
  MapGenStructure. Não há varredura física ou geração de chunks para recuperação.
- Etapas e contagem de verificações no cliente, origem registrada/prevista/bioma
  no alvo e histórico compatível com os registros anteriores. Timeouts explícitos.
- Detecção de capacidades do servidor: controles novos dependem de suporte;
  consultas básicas conservam o formato anterior quando necessário.
- Apenas `assemble`, sem testes ou execução do jogo a pedido do usuário.
  Detalhes em `docs/BUSSOLA-BUSCA-CR44.md`; nome CR44 preservado.

### Melhorias da Bússola Astral na CR44 — 2026-11-17

- Lista com nome legível, mod de origem, dimensão de busca e filtros por mod e
  dimensão; traduções PT-BR/inglês para 56 estruturas opcionais.
- Favoritos com ordenação prioritária e histórico local dos últimos 12 alvos por
  mundo/servidor/jogador, limitado a 32 contextos. Histórico não concede alvos.
- Estado da busca confirmado pelo servidor, cancelamento pela mesma fila validada
  e interface que permanece aberta durante a consulta. Sem mudança no custo,
  nível, orçamento compartilhado ou concessão de permissões.
- Engrenagem para posição, tamanho e transparência da HUD, com arraste e prévia.
  HUD mostra origem, dimensão com nome e altura quando conhecida.
- Somente compilação nesta entrega, sem testes automatizados nem execução do
  jogo, por solicitação explícita do usuário. Mantido o nome CR44.

### Correção adicional da CR44 — 2026-11-17

- Bússola Astral: o serviço de pedidos passa a iniciar tanto no servidor integrado
  quanto no dedicado. Antes, no singleplayer a interface ficava com as oito entradas
  básicas, mesmo quando o servidor conhecia estruturas dos mods.
- Registro compartilhado em `CommonProxy.init`, sem duplicar handlers, canais ou
  alterar os limites e requisitos das buscas. Nome e versão CR44 preservados.
- Teste de regressão do ciclo de inicialização e reprodução da interface em cópia
  descartável do Forbidden Series. Consulte `docs/BUSSOLA-CATALOGO-CR44.md`.

## 1.6.11-LE-CR44 — 2026-11-16

- Arcane Striker & Manequim Arcano: corrigida a captura de dano para evitar perda de precisão IEEE-754 (`DUMMY_BASE_HEALTH = 1024.0F` com delta capturado diretamente em `setHealth` dentro de `damageEntity`), adicionado popup de dano no cliente e sincronização imediata com a máquina.
- Corrigido o ciclo de vida do manequim: liberado `setDead()`, verificação contínua `isExpectedDummy(this)` e trava anti-duplicação ao iniciar o teste.
- Remodelado o bloco do Arcane Striker para um pedestal 1×1×1 sólido (`arcane_striker_machine.bbmodel`) com spawn do Manequim diretamente em cima `(x+0.5, y+1.0, z+0.5)` e atualização do Compêndio + traduções.

## 1.6.11-LE-CR43 — 2026-11-16

- Bússola Astral: validação estrita, fila no tick do servidor, busca incremental,
  limites compartilhados e confirmação de mana/nível/dimensão antes do resultado.
- Predição vanilla sem alterar o RNG ou os geradores ativos; suporte a listas de
  geradores e leitura do centro das estruturas persistidas, descartando vilas inválidas.
- Integrações opcionais: 46 construtores do OreSpawn Remastered, torres Wizardry,
  seis tipos no cache do ChromatiCraft, três geradores Thaumcraft e adaptador
  Twilight Forest. Índice próprio, limitado e separado por dimensão.
- Campo comum da bússola compatível com a remoção de membros do lado cliente no dedicado.
- Corrigido o mapeamento herdado da AnimationAPI no JAR de produção; novo
  verificador para dez classes de IA, prevenindo `setMutexBits`/métodos sem mapeamento.
- 33 testes locais e nove verificadores passaram; dedicado com 196 JARs externos,
  dez guardiões, busca, cobrança e rejeição de pedidos inválidos. Sem cliente remoto
  ou teste de carga. Os 1.679 modelos/texturas da base foram preservados.
- [Suporte, limitações e reprodução](docs/BUSSOLA-ASTRAL-CR43.md).

## 1.6.11-LE-CR41 — publicações anteriores

- Arcane Striker e alvo invocado, ritual da Bússola Astral com modelo 3D,
  interface e HUD; True Soulbound, integração Otherworld/AE2 e recursos visuais.
- Corrigido o identificador da bússola no Compêndio e adicionado o catálogo
  de receitas de rituais ao NEI. Essas alterações já constavam no histórico Git.

## 1.6.11-LE-CR40 — 2026-11-11 — publicada na branch `correcoes`

- **CR40 (Otimização de FPS em Starfall e Storm + Lightning):** tabela estática pré-computada de seno/cosseno (`GLOW_COS`/`GLOW_SIN`, 24 segmentos) em `CastingAtmosphereVFX`, redução de spawn e limite de 22 cometas ativos por conjurador em Starfall, refatoração de `astralStar` para billboarding em diamante de 18 vértices, anéis de impacto com tabela pré-computada e redução de passos/vértices na nuvem e funil 3D de tempestade.
- **CR39 (Expansão do solo e Câmara Estelar de Starfall):** camada dupla aditiva incandescente nos círculos de solo de Starfall (`CastingCircleVFX`), ondas de choque terrestres aos 40 ticks (25 m) e 80 ticks (37,5 m), 12 nós estelares na Fase 2, 24 nós + 24 espículas radiais na Fase 3 e 16 fitas verticais ascendentes de 76 blocos ligando o círculo de 75 m ao portal celeste (`renderStarfallChamberAndShockwaves`).
- **CR38 (Abertura do portal e cometas 3D):** abertura sincronizada do portal estelar em 4 estágios (`getStarfallPortalProgress`: 0–16, 16–40, 40–80 e 80–100 ticks) acompanhando o crescimento dos círculos, e cometas balísticos 3D inclinados com histórico de rastro de 8 pontos (`drawAnimeComet3D`).
- **CR37 (Pilar celestial e portal cósmico de Starfall):** pilar vertical exclusivo de 76 blocos (`renderStarfallCelestialBeam`) conectando o conjurador ao portal de até 96 m de diâmetro (`drawAstralSky`), com dupla hélice, anéis ascendentes e impactos com coluna luminosa.
- **CR32–CR36 (Céus elementais estilo anime e correção de Storm + Lightning):** descargas horizontais ramificadas no Firestorm, placas hexagonais fragmentáveis no Blizzard, textura `anime_storm_cloud_cr34.png` e funil volumétrico 3D compartilhado entre carga e `OrbitalStormVFX` (`renderActiveStormCanopy`), resolução de IDs deslocados (`+1000`) em `hasAnyComponent` (incluindo Storm + Lightning + modificadores Lunar) e conversão do Starfall para portal circular no zênite.

## 1.6.11-LE-CR31 — 2026-11-09 — local, não publicada

- Nuvem, funil, espirais e olho da CR30 preservados exclusivamente em Storm +
  Lightning. Os outros quatro tipos têm formas próprias.
- Firestorm com oito rios de lava e doze ramificações, fluxo amarelo animado e
  pingos; Blizzard com placas fraturadas; Starfall com fendas; Lunar com eclipse.
- Raio das quedas de 36 para 64 blocos, maior frequência e quantidade. Até 384
  grandes quedas, 96 impactos e 40 raios, com opções reduzidas e limites por jogador.
- Todas as 1.660 imagens e as regras de magia preservadas. Sem alteração no
  Playground ou AM2 Archmage. Dedicado e Forbidden descartáveis aprovados, com
  raio real 63,984 blocos e pico 384. Detalhes em `docs/CEUS-ELEMENTAIS-CR31.md`.

## 1.6.11-LE-CR30 — 2026-11-09 — local, não publicada

- Coberturas de nuvens em espiral, funil, olho e fissuras coloridas no céu; auroras
  astrais, frentes de nevasca e motes elementais. Progressão pelas três fases.
- Mais quedas e impactos maiores, mantendo limites globais e por conjurador.
  Nuvens do mesmo elemento próximas compartilham uma cobertura.
- Até três coberturas desenhadas, 192 quedas, 64 impactos e 24 raios. Clarões com
  intervalo global; opções reduzidas/mínimas respeitadas; pausa congela o efeito.
- Preservadas todas as imagens e a restauração exata do clima. Sem alteração no
  Playground, AM2 Archmage ou nas regras de magia.
- Dedicado e Forbidden descartáveis aprovados; carga real de 192 quedas e dez
  capturas das cinco famílias. Detalhes em `docs/ATMOSFERA-APOCALIPTICA-CR30.md`.

## 1.6.11-LE-CR29 — 2026-11-09 — local, não publicada

- Corrigidas partículas que desapareciam no primeiro tick por falta de controlador.
  Quedas de fogo, gelo e estrelas usam formas próprias maiores e rastros.
- Impactos agora acompanham a chegada ao solo; início no terceiro círculo.
- Atualização por tick, limites globais e por conjurador, terreno apenas em chunks
  carregados e posições independentes da câmera livre.
- Clima visual em três fases, restaurando os quatro valores naturais ao fim de
  cada quadro. Raios cosméticos não criam entidades ou dano.
- Preservadas as 1.660 imagens, paletas e regras de magia. Sem mudança no Playground
  ou AM2 Archmage. Dedicado e bateria funcional no Forbidden aprovados.
- Regressão de famílias, impactos, clima, cancelamento e opções gráficas, com
  capturas individuais opcionais. Detalhes em `docs/ATMOSFERA-E-QUEDAS-CR29.md`.

## 1.6.11-LE-CR28 — 2026-11-09 — local, não publicada

- Círculos de conjuração e partículas usam a posição real interpolada do corpo,
  capturada antes do render do mundo, corrigindo o deslocamento até a câmera livre.
- Limites de distância usam corpo e câmera separadamente; cache fraco limpo no
  unload. Sem dependência de Freecam, Playground ou outros addons.
- Preservadas as paletas por afinidade, tradução e correção do menu de teclas
  presentes na CR27 recebida nesta revisão, além das 1.660 imagens.
- Regressão opcional com Freecam 2.3.1, onze afinidades, Mesa de Inscrição no
  servidor e categorias de teclas com traduções iguais. Dedicado e Forbidden
  descartáveis aprovados. Detalhes em `docs/FREECAM-E-AUDITORIA-CR28.md`.

## 1.6.11-LE-CR27 — 2026-11-09 — local, não publicada

- Círculos de conjuração reutilizam a análise da magia por quatro ticks, sem
  reconstruir livros e varas a cada quadro.
- Emissão de partículas limitada por tick e pelas opções gráficas do jogo,
  evitando multiplicação conforme o FPS.
- Orçamento de oito conjuradores por quadro, prioridade ao jogador local,
  distância máxima de 96 blocos e descarte de efeitos fora da câmera.
- Proteções para definições visuais excessivas e afinidades inválidas; cache
  fraco limpo ao sair do mundo e fases reiniciadas ao reconjurar.
- Todas as 1.660 imagens PNG da CR26 recebida foram preservadas byte a byte.
- Nova regressão descartável de VFX com oito conjuradores e cinco arquétipos,
  mantendo os testes do livro e do servidor. Nenhuma alteração no Playground.


## 1.6.11-LE-CR26 — 2026-11-09 — local, não publicada

- Integradas as animações locais do livro e do círculo de conjuração que estavam
  somente na versão experimental do teste-ars.
- Livros comuns e do Guardião Arcano abrem e fecham suavemente; páginas animadas
  e glifo da afinidade ficam visíveis durante a conjuração.
- Corrigidos o enquadramento em primeira pessoa, a posição do braço e o tamanho
  e orientação do livro em terceira pessoa. O inventário do Guardião mantém o
  modelo anterior.
- Abertura respeita a tecla de uso configurada, inclusive em livro vazio; em
  jogadores remotos usa o indicador sincronizado de utilização.
- Estado visual limpo ao sair do mundo e iluminação restaurada após os efeitos.
- Mantida a versão de compatibilidade 1.6.7 no metadado estático para preloaders
  legados; versão real CR26 preservada no Forge, na tela de mods, no nome do JAR
  e no campo maintenanceVersion. Sem alteração ou dependência nova de addons.
- Mantidas as correções de servidor da CR25. Detalhes e limites em
  `docs/LIVRO-ANIMADO-CR26.md`.

## 1.6.11-LE-CR25 — 2026-11-09

- Criação de efeitos de área e fusões de Explosion, Lightning e Storm protegida
  contra conjurador ausente, usando o contexto neutro nativo do mod.
- Efeitos sem dono usam ID -1; efeitos com conjurador preservam sua identidade.
- Versão do Forge e dos metadados alinhada com o nome CR25 do JAR.
- Verificações de manutenção passam a inspecionar o JAR reofuscado jogável,
  com uma nova barreira contra modelos gráficos no handler comum do servidor.
- Incluída regressão executável em servidor dedicado e cópia descartável de um
  modpack. Resultados e limites: `docs/CORRECAO-SERVIDOR-CR25.md`.

## 1.6.11-LE-CR24 — 2026-11-07/08

- Reformulação dos efeitos de Firestorm, Blizzard, Starfall, Grow, Explosion,
  Lightning Strike, Storm e Mana Shield, com fusões e novos limites de Wave.
- Métodos de modelos e renderização de armaduras isolados no cliente para
  corrigir o carregamento em servidores dedicados.
- Cálculo de duração sincronizada e acesso a Solar/Lunar protegidos contra
  conjurador nulo; removidos auxiliares internos envolvidos em falhas de carga.
- As compilações intermediárias conservaram o mesmo sufixo CR24. Para identificar
  uma delas, consulte o commit e o checksum da execução correspondente do CI.

## 1.6.11-LE-CR23 — 2026-11-05

- Velocidade de animacao do bater de asas do Dragao do End e da Galinha reduzida pela metade, tornando o movimento suave e visualmente natural.
- Velocidade de rotacao das 12 hastes orbitais do Blaze reduzida pela metade.
- Fisica de queda lenta (slowfall continuo sem dano de queda) aplicada tanto para Galinha quanto para Blaze enquanto transformado.
- Adicionado som autentico de pulo do slime (mob.slime.big) acionado a cada salto do jogador disfarcado.
- Renderizador dedicado de ilusao para Slime (RenderIllusionSlime) com textura otimizada de alta claridade: camada externa translucida em gelatina cristalina e olhos/boca destacados com contraste perfeito e visualizacao nitida em qualquer angulo ou iluminacao.
- Orientacao e rotacao da face do slime sincronizadas com a visao da camera/jogador (incluindo visao em terceira pessoa selfie F5).
- Posicionamento aprimorado de itens empunhados (como varinhas) ao lado do corpo do slime, sem colidir ou cortar o cubo.
- Pagina inicial da Caixa de Ilusoes no Arcane Compendium atualizada para exibir instrucoes diretas de uso e principais catalisadores ja na primeira pagina de forma bilingue (pt-BR e en-US).

## 1.6.11-LE-CR22 — 2026-11-05

- Expansão da Box of Illusions para suporte completo a criaturas vanilla do Minecraft via catalisadores, sem dependência de mods externos.
- Dragão do End em miniatura (0.18x) transformável com Ovo de Dragão, com asas batendo e articulação contínua.
- Animações autênticas para criaturas: batimento de asas em queda para galinhas e morcegos, rabo e postura sentada ao agachar para lobos, órbitas de varas de blaze, compressão de pulo em slimes e movimento de tentáculos em lulas.
- Sistema de áudio do mob com sons de dano, morte e ambiente controlados, com cooldown mínimo de 30 segundos para sons ociosos e volumes amigáveis, evitando poluição sonora.
- Reversão instantânea para humano via Shift + Clique Direito e compêndio arcano atualizado com lista bilíngue de catalisadores.

## 1.6.11-LE-CR21 — 2026-11-05

- Estabilização da Box of Illusions em servidores multiplayer, evitando reinfecção de skins antigas por pacotes de outros clientes.
- Renderização correta do item na mão do jogador em todas as transformações de criaturas e skins maid.

## 1.6.11-LE-CR20 — 2026-11-04

- Vara Arcana verifica jogador e item na mão antes de escolher o ícone lançado,
  evitando o crash ao desenhá-la com a mão vazia ou sem jogador disponível.
- Shrunken usa uma única redução visual no renderer, ancorada nos pés, sem
  escalar novamente a posição do jogador em relação à câmera, e reduz o tamanho
  final a um quarto do tamanho normal (2x menor que o tamanho anterior).
- Jogadores remotos conservam offset zero; apenas o cliente local ajusta seu
  offset de visão. Alterar e restaurar o tamanho preserva os pés e a interpolação.
- Câmera em primeira pessoa acompanha a redução proporcional da altura; eventos
  de salto deixam de avançar a transição como se fossem ticks adicionais.
- Mudanças nativas do AM2.5; Playground e o addon do Cérebro permanecem separados.

## 1.6.11-LE-CR19 — 2026-11-03

- Rotina das molduras do Compêndio separa estados e filas do cliente e servidor,
  impedindo a tarefa do cliente de consultar molduras do mundo do servidor.
- Entradas concorrentes são enfileiradas, e a busca da receita consulta somente
  chunks já carregados. O mundo descarregado libera seus registros pendentes.
- Criação do Compêndio continua no servidor. A API sem argumentos foi preservada
  para addons; os ticks nativos informam seu lado explicitamente.
- Correção nativa do AM2.5, sem dependência nova do Playground ou do Cérebro.

## 1.6.11-LE-CR18 — 2026-11-02

- Engrenagem no menu do giz abre os ajustes de posição, tamanho e transparência
  do fundo da HUD (0% a 100%), preservando a leitura dos textos.
- Painel pode ser arrastado; botões de tamanho vão de 50% a 150%, com ajuste
  automático para caber na tela. Restaurar volta ao canto superior esquerdo e 100%.
- Preferências locais persistem ao fechar o menu, sem modificar o item ou enviar
  configurações de interface ao servidor. Fundo e estilo da CR17 preservados.

## 1.6.11-LE-CR17 — 2026-11-02

- HUD do giz fixada no canto superior esquerdo, com margem de oito pixels de
  interface no topo, preservando a adaptação da altura a janelas menores.
- Fundo azul-escuro e sombra mais transparentes; textos, runas e bordas mantêm
  o contraste. O menu da CR16 e os controles do giz são preservados.

## 1.6.11-LE-CR16 — 2026-11-02

- Menu do Giz do Arquiteto Arcano no mesmo tema azul-escuro da HUD, com bordas
  luminosas, runas nativas do Wizard Chalk e destaque para o ritual selecionado.
- Botões de navegação, rotação, limpeza e montagem usam o novo tema. Rótulos
  longos têm tooltip; os controles se ajustam a janelas menores.
- A página é ajustada ao redimensionar a janela, evitando páginas vazias no fim
  do catálogo. A primeira abertura mostra a página do ritual selecionado.
- Seleção e ações continuam usando o mesmo protocolo e as mesmas permissões.
  Projeção no survival; montagem automática apenas no criativo.
- Compilação e verificações locais concluídas. Menu testado em cliente integrado
  sem Playground e com Playground corrigido, incluindo troca de mensagens,
  seleção, rotação, limpeza, nova âncora, montagem criativa e janela pequena.

## 1.6.11-LE-CR15 — 2026-11-02

- HUD do giz com painel azul-escuro, runas do Wizard Chalk, barra de progresso
  e seções para materiais, reagentes e condições de ativação.
- Posicionamento lateral e adaptação a resoluções menores. A HUD respeita a
  opção de ocultar a interface do jogo.
- Validada em clientes com e sem Playground e em cópia descartável do
  Forbidden Series, preservando a instância original.

## 1.6.11-LE-CR14 — 2026-11-02

- Catálogo do giz ampliado para 41 modelos nativos, cobrindo as variantes dos
  rituais. Seleção e projeções verificadas em cliente e servidor.
- Pesquisa da mesa de inscrição aceita tanto o nome traduzido quanto o nome
  em inglês das formas, componentes e modificadores, inclusive termos parciais.
- Preservada a independência entre AM2.5 e Playground.

## 1.6.11-LE-CR13 — 2026-11-02

- Removidos do núcleo o contador cinza adicional do Oculus, a mensagem de
  descoberta e a regra da CR4 que reservava compras Silver somente a talentos.
  Esses ajustes pertencem à manutenção opcional do Playground.
- O AM2.5 sozinho conserva sua interface e suas regras nativas. Conhecimentos,
  saldos salvos e os métodos públicos originais não são removidos.
- Mantida a correção da leitura do quarto saldo do protocolo nativo: o upstream
  já transmitia os quatro saldos e possuía o tipo Silver, mas lia somente três.
- Mantida a API opcional World Fusion da CR5 por compatibilidade com extensões
  já distribuídas. Sua presença não exige Playground; a iniciação continua nativa.
- O verificador do JAR exige sincronização dos quatro saldos e rejeita a volta
  de contador, tooltip e política de compra específicos do addon no núcleo.
- Preservadas as correções de magias, Bless, giz e Compêndio, inclusive as APIs
  genéricas de categorias e entradas da CR12.

## 1.6.11-LE-CR12 — 2026-11-01

- Extensão cliente permite registrar categorias opcionais no Compêndio,
  preservando a ordem e os tipos originais e rejeitando nomes duplicados.
- Registro de entradas completas permite páginas próprias de estruturas e
  guias de addons, sem substituir entradas ou aliases existentes.
- Arcane Engineering usa essa extensão; nenhum conteúdo desse addon é incluído
  no AM2.5. Os testes de manutenção e localização existentes foram preservados.

## 1.6.11-LE-CR11 — 2026-11-01

- Corrigido o escurecimento do Compêndio ao visualizar criaturas como Mana
  Creeper e Earth Elemental: a prévia não deixa o lightmap ativo no desenho do livro.
- Estado gráfico, matrizes e cache de iluminação restaurados após cada modelo,
  mantendo as prévias e a rotação das criaturas.
- Teste gráfico reproduz a falha na CR10 e valida a correção em cliente mínimo
  e no modpack completo, incluindo navegação de volta ao texto e ao mundo.

## 1.6.11-LE-CR10 — 2026-11-01

- Tradução completa PT-BR do Compêndio: 535 entradas e 474 descrições não vazias,
  com IDs, receitas, controladores e marcações de paginação preservados.
- Categorias, título, rotação e receitas sem formato passam a usar a localização.
- Reabrir o livro após trocar o idioma do jogo ou recarregar recursos atualiza
  textos e entradas opcionais, preservando flags de descoberta e novidade por ID.
- Inglês original preservado. A tradução adicional do Playground é fornecida
  por pacote de recursos, sem alterar o JAR do addon.
- Testes de cliente integrado alternam EN/PT/EN e verificam páginas e progresso.

## 1.6.11-LE-CR9 — 2026-11-01

- Mana Regen deixa de inverter a regeneração em níveis altos: a redução do tempo
  cresce 25%, 50%, 75% e chega ao teto de 90% no nível IV ou superior.
- O denominador da regeneração nunca fica abaixo de um tick, evitando divisão por
  zero ou recuperação negativa. Os eventos e talentos do Playground são preservados.
- Bless preserva efeitos benéficos quando não consegue melhorá-los ou não tem
  orçamento, e só remove a versão anterior após construir a substituição.
- Corrigida a diferença entre amplificador zero-based de PotionEffect e nível
  one-based do construtor BuffEffect, impedindo a perda de um nível na reaplicação.
- Bless passa a compartilhar o orçamento de amplificação entre os buffs.
- Haste e Frost Slow acima do nível III mantêm o modificador de atributo mais
  forte implementado, em vez de ficar sem bônus na aplicação direta.
- Teste de cliente integrado cobre a drenagem anterior e as correções, incluindo
  a preservação dos talentos Archmage/Atronach e do auxiliar usado por Curse.

## 1.6.11-LE-CR8 — 2026-11-01

- Compatibilidade opcional do giz com rituais registrados e infusões de buffs do
  Playground, usando as estruturas e ingredientes reais expostos pela API.
- Mantidos os 30 rituais nativos; entradas opcionais são acrescentadas ao catálogo
  e a seleção passa a persistir por ID, sem depender da posição de outros mods.
- O pedido do menu inclui esse ID; pacotes antigos continuam limitados aos índices
  dos rituais nativos. Registro e execução no servidor integrado da CR7 preservados.
- Variantes do addon respeitam seus grupos preenchidos, incluindo desenhos
  espelhados. Grupos padrão vazios não são usados como desenho.
- Região dos reagentes adaptada, Phylactery preenchida e poções verificadas sem
  consumir itens ou alterar o suporte de poções.
- Contenção lateral exigida antes de montar Liquid Essence; fogo central requer
  Netherrack sob ele. O painel informa essas preparações.
- Rituais com suspeita de falha de ativação original apresentam aviso; a ferramenta
  mostra a preparação e não altera a execução do addon.
- Testes em servidor completo, sem integrações e sem addon, além de cliente real
  com navegação, variantes e montagem do desenho do portal.

## 1.6.11-LE-CR7 — 2026-10-31

- Corrigido o desenho do Giz do Arquiteto Arcano que ficava preso na seleção
  anterior em mundos de um jogador. O registro do processador da fila estava
  somente na inicialização do servidor dedicado, substituída pelo proxy cliente.
- O processador agora é registrado uma vez no caminho comum de inicialização,
  atendendo tanto o servidor integrado quanto o dedicado.
- Confirmados no cliente real: troca de ritual e páginas do menu, rotação,
  limpeza do centro, marcação por clique vanilla e montagem criativa.
- Novo verificador de CI impede regressão do registro comum. O teste de servidor
  dedicado usa o evento real de tick em vez de drenar a fila diretamente.

## 1.6.11-LE-CR6 — 2026-10-31

- Novo Giz do Arquiteto Arcano, reutilizável, com receita usando Nether Star,
  Wizard's Chalk, Sunstone, Moonstone, essências arcanas, cinzas arcanas e runa.
- Menu para escolher rituais nativos descobertos, girar o desenho e marcar o
  centro; projeção de blocos, região dos reagentes e lista de faltantes.
- Montagem da estrutura disponível exclusivamente no criativo, validada pelo
  servidor e sem ativar o ritual. Survival continua usando os materiais normais.
- A conferência do guia não consome nem modifica reagentes no chão.
- Instruções em português e inglês no Arcane Compendium.

## 1.6.11-LE-CR5 — 2026-10-31

- Novo evento de servidor `WorldFusionEvent` permite estender o ritual de World
  Fusion, interceptando sua execução e informando uma conclusão alternativa.
- Sem uma extensão que trate o evento, a execução original permanece igual.
- O evento permite validar pré-requisitos e materiais extras antes de consumir
  os reagentes normais. Compras Silver continuam limitadas a talentos.

## 1.6.11-LE-CR4 — 2026-10-31

- Pontos Silver livres compram somente talentos, conforme a regra escolhida
  pelo mantenedor; formas, componentes e modificadores Silver não podem ser
  comprados no Oculus com esse saldo.
- A regra vale também no tratamento do pedido de compra no servidor.
- Descobertas e recompensas que ensinam habilidades diretamente continuam funcionando.
- O Oculus informa que habilidades Silver sem compra dependem de descobertas
  ou recompensas especiais. Conhecimentos já salvos não são removidos.

## 1.6.11-LE-CR3 — 2026-10-31

- Corrigida a leitura do quarto saldo de pontos (Silver) no pacote de sincronização
  que o servidor já enviava. O cliente antes atualizava apenas azul, verde e vermelho.
- Acrescentado o contador de pontos Silver, em cinza, abaixo do vermelho no Oculus.
- Acrescentada verificação do campo de sincronização e do contador no JAR de CI.
- Preservadas as correções anteriores de conjuração, Solar/Lunar, AoE, Zone e Wave.

Os pontos Silver continuam obedecendo às regras de aprendizagem e persistência
existentes. Esta mudança não cria pontos automaticamente nem altera pré-requisitos.

## 1.6.11-LE-CR2 — publicação em 2026-10-31

Base: DrParadox7/ArsMagica2, tag `v1.6.11-LE`, commit
`20e52f3e5160af2af4ae47ef3f98415f2c19cf35`.

### Travamento e processamento de área

- Lunar valida propriedades ausentes, capacidade de mana inválida e resultados
  NaN/infinito, retornando um modificador neutro nesses casos.
- Zone processa a aplicação da magia apenas no servidor, evitando a repetição
  desse trabalho no cliente observador.
- AoE sanitiza o raio, rejeita valores não finitos e mantém um teto defensivo de
  64 blocos para o valor processado.
- AoE evita repetir suas partículas em aplicações subsequentes do mesmo efeito.

Essas mudanças visam o travamento/tela branca observado com combinações como
Projectile + Zone + AoE + Lunar + Radius. Não são uma correção geral para todos
os crashes de um modpack.

### Solar e Lunar

- Solar escala pela capacidade máxima de burnout, tomando 991 como referência,
  em vez de exigir que a barra de burnout atual esteja cheia.
- Lunar escala pela capacidade máxima de mana, com referência de aproximadamente
  57.114, e usa uma curva de força equivalente à do Solar.
- O crescimento do raio foi suavizado; no caso de referência com três cópias,
  o AoE chega a aproximadamente 20,8 blocos, abaixo de 21.
- Uma, duas e três cópias de cada modificador concedem respectivamente 1/3, 2/3
  e o bônus completo. Cópias adicionais não ampliam esse teto.
- Quando Solar e Lunar aparecem juntos em magia com AoE ou Zone, Lunar é removido
  apenas da cópia temporária usada na conjuração. Solar continua funcionando e a
  magia armazenada no item permanece intacta.
- Sem AoE ou Zone, a regra anterior não remove Lunar.

### Lightning Strike, Explosion e Dig

- Limite compartilhado de três conjurações válidas por janela fixa de 200 ticks
  (10 segundos a 20 TPS), iniciada pela primeira conjuração.
- Uma conjuração contendo mais de um desses componentes conta uma vez.
- Tentativas bloqueadas não devem cobrar mana/reagentes; o bloqueio ocorre na
  verificação central anterior ao disparo.
- Canalização mantida conta como um uso, sem confundir conjurações normais.
- Aviso com o componente identificado e o tempo restante, limitado para evitar
  repetição excessiva de mensagens.
- Lightning Strike evita raios duplicados no mesmo bloco no mesmo tick para o
  mesmo lançador e magia, preservando raios nos blocos distintos da área.
- Zones com Lightning Strike ou Explosion têm intervalo de 30 ticks por pulso
  (1,5 segundo a 20 TPS); Dig conserva a frequência normal da Zone.
- O ajuste do intervalo da Zone atualiza tanto o contador atual como o máximo.
- Lightning Strike e Explosion usam duas partículas por aplicação em vez de cinco.

### Wave e documentação

- A Wave percorre no máximo 128 blocos, considerando velocidade e duração.
- Velocidade inválida ou acima do limite é tratada; a largura da onda continua
  determinada pela magia e seus modificadores.
- As páginas relevantes do Arcane Compendium foram atualizadas em inglês e
  português para explicar os limites e o comportamento de Solar/Lunar.
- Bless continua habilitado: o bloqueio temporário experimentado anteriormente
  não faz parte deste arquivo CR2 publicado.

### Estado da publicação

As fontes recuperadas da manutenção foram publicadas sobre a base LE. A importação
do auxiliar de limite na classe Zone foi completada na fonte. O JAR da release é
o arquivo de teste anterior, sem uma reconstrução completa pelo Gradle nesta etapa.
Consulte [VALIDACAO-CR2.md](docs/VALIDACAO-CR2.md) para os limites dessa verificação.

# Alterações de manutenção do AM2.5 LE

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

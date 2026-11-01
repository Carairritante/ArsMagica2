# Alterações de manutenção do AM2.5 LE

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

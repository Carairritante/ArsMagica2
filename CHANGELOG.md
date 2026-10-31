# Alterações de manutenção do AM2.5 LE

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

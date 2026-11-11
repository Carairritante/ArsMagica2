# Céus elementais e maior área — CR31 local

Pedido: manter a nuvem da CR30 somente em Storm + Lightning e dar formas próprias
aos demais elementos, especialmente rios de lava no céu do Firestorm. Aumentar
também a área e a quantidade das quedas.

## Formatos

- Storm + Lightning: nuvem, espirais, funil e olho da CR30 preservados. O corpo do
  método da nuvem é idêntico ao anterior após remover apenas o filtro de tipo.
  O trecho das espirais e do olho também foi preservado literalmente.
- Firestorm: oito canais largos de lava com crosta escura, núcleos amarelos em
  movimento e doze ramificações. Pingos de lava procedurais saem dos canais.
  Sem funil/nuvem de tempestade nesse elemento.
- Blizzard: placas de gelo suspensas e fraturadas, com contornos, rachaduras e
  frentes de nevasca mais largas. Sem a nuvem do Storm.
- Starfall: fendas diagonais no firmamento, bordas violeta/ciano, estrelas dentro
  das fendas e faixas de aurora mais amplas.
- Lunar Lightning: eclipse com crescente luminoso e fragmentos de halo, separado
  da nuvem da variante Storm + Lightning.

## Área, quantidade e limites

O raio das grandes quedas foi de 36 para 64 blocos (até 128 de diâmetro), uma área
aproximadamente 3,2 vezes maior. A frequência e o número por emissão aumentaram.
O orçamento global agora permite até 384 grandes quedas, 96 impactos e 40 raios;
até 80 quedas por conjurador com partículas completas e 40 com reduzidas.
Os motes menores têm orçamento procedural fixo, sem gerar entidades.

As grandes quedas são desenhadas até 128 blocos da câmera. A coleta de conjuradores
continua a 64 blocos, com no máximo oito por cliente; somente chunks já carregados
são consultados. A nuvem do Storm mantém sua geometria e alcance anteriores.
As opções reduzidas/mínimas e o agrupamento de coberturas próximas continuam.

Tudo é visual no cliente: não há lava/gelo/blocos, incêndio, dano ou clima de
servidor criados por esses efeitos. As regras de magia e os demais mods não foram
alterados. Cada observador precisa da CR31 no cliente para ver os novos formatos.
As 1.660 imagens originais foram preservadas byte a byte; os novos formatos são
geometria, sem texturas adicionais. Sons antigos preservados, sem avaliação auditiva.

## Validação

Compilação Java 8, seis verificadores e servidor dedicado mínimo aprovados.
Bateria completa no Forbidden descartável aprovada com oito conjuradores,
Playground atual e Freecam 2.3.1. Foram medidos 384 grandes objetos de queda e
raio máximo de 63,984 blocos. Os limites, impactos, cinco famílias, três fases,
cancelamento, limpeza e restauração exata do clima passaram. Também passaram as
regressões anteriores de livro, mesa, controles, afinidades e dados inválidos.
A seleção exclusiva da nuvem para STORM_LIGHTNING foi verificada no código e em
execução. As dez capturas individuais foram geradas; cinco capturas olhando para
o céu foram inspecionadas para comparar os formatos novos com o Storm preservado.

O dedicado é mínimo, sem classes gráficas ou Playground. A cópia completa usa
servidor integrado e jogadores remotos simulados; não substitui um ensaio longo
num servidor externo.

CR31 instalada no teste-ars fechado, com backup da CR30 e outros mods preservados.
JAR: `AM2.5-LTS-1.6.11-LE-CR31.jar`. SHA-256:
`075c6fd630320c565f98e5bafbf4b6356e0a489701d54365bdc7a96007dba140`.

O aumento de efeitos amplia a carga do cliente. Os limites são finitos e as
opções gráficas continuam disponíveis; não há garantia de FPS ou ensaio prolongado
de multiplayer externo. Sem publicação no GitHub nesta etapa.

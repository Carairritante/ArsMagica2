# Atmosfera apocalíptica — CR30 local

Pedido: intensificar o espetáculo climático da CR29, para parecer um apocalipse
aos observadores. Entrada preservada: CR29 SHA-256
`a2c4e6dc7a2a7ea8fff36397e7f0e225fff878c8120b0b18faa1b16d05ab37e6`.

## Visual

- Cobertura escura de nuvens com borda suave, quatro braços em espiral e um funil
  acima do conjurador. Usa a textura storm_cloud existente, sem alterar imagens.
- Olho luminoso e quatro fissuras coloridas sob as nuvens, com animação suave.
- Fogo: mais meteoros, brasas espalhadas e impactos maiores.
- Gelo: queda mais densa de cristais e flocos, frentes de vento e estilhaços no solo.
- Astral: estrelas com rastros, auroras em três faixas e pulsos de impacto.
- Tempestade/lunar: mais descargas e fissuras dentro da cobertura de nuvens.
- Nuvens começam na primeira fase e crescem conforme a carga. As grandes quedas
  continuam começando na terceira fase. Interromper a conjuração dissipa a cobertura.

## Limites e preservação

São efeitos locais de cliente. Não criam entidades de raio/projétil, fogo, dano,
blocos ou clima do servidor. Observadores próximos também precisam da CR30 no
cliente. A coleta continua até 64 blocos; as quedas são desenhadas até 96 e a
cobertura de nuvens até 160 blocos da câmera, inclusive com Freecam.

Até oito conjuradores são processados. O orçamento global é de 192 quedas,
64 impactos e 24 raios, com limite por conjurador. Existem no máximo 16 células
de clima em transição e três coberturas desenhadas. Conjuradores do mesmo elemento
a menos de 28 blocos compartilham a cobertura, evitando nuvens duplicadas.

Partículas reduzidas diminuem a frequência e os detalhes; mínimas removem quedas
e motes, mantendo uma cobertura simplificada. Clarões adicionais têm intervalo
global mínimo de 12 ticks e intensidade menor. Os sons existentes mantêm o limite
global da CR29. Não houve avaliação auditiva nesta revisão.

Os quatro valores naturais de chuva/trovão e o clarão natural são restaurados ao
fim do quadro. O efeito congela com o jogo pausado e todos os estados são limpos
no unload. Permanecem intactas as 1.660 imagens, as paletas dos círculos, as
regras de magia, o Playground e o AM2 Archmage.

## Validação

Compilação Java 8, seis verificadores do JAR e servidor dedicado mínimo aprovados.
Bateria completa no Forbidden descartável aprovada com oito conjuradores: pico
real de 192 quedas simultâneas e uma cobertura de nuvens compartilhada pelo grupo
próximo. Passaram os limites de células/coberturas, a independência de FPS, as
cinco famílias, as três fases, o cancelamento, o clima natural, o unload e o
Freecam. Também passaram os testes anteriores de livros, mesa, controles e dados
visuais inválidos.

Foram conferidas dez capturas individuais das cinco famílias, incluindo o céu
com a câmera inclinada para cima. Nuvens, olho, fissuras, auroras e quedas mais
densas aparecem nas capturas. Após interromper, as células e coberturas ficaram
vazias. Os jogadores remotos deste ensaio são simulados.

Cinquenta renders controlados dos círculos levaram 219,1763 ms nesta execução,
com pixels descartados. Esse número não mede FPS, o custo das nuvens na GPU nem
uma comparação controlada com outra versão.

CR30 instalada no teste-ars fechado, com backup da CR29 e demais mods preservados.
JAR: `AM2.5-LTS-1.6.11-LE-CR30.jar`, SHA-256:
`38eaeef6fd64f07be6f04124d86454f4ca2ae0e749f74434d60dbb24115bd2a8`.

Não é garantia de FPS nem ensaio prolongado em um servidor externo. Esta alteração
amplia a carga visual do cliente, mantendo um orçamento finito e opções reduzidas.
Nenhuma publicação no GitHub foi feita nesta etapa.

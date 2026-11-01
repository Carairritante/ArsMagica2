# Bless e Mana Regen em níveis altos — CR9

## Causa e correção

O AM2.5 calculava o tempo com `1 - max(0.9, 0.25 * (amplificador + 1))`.
No nível V, amplificador 4, isso resulta em -0,25. Dividir pelo tempo negativo
subtrai mana. A instrução Math.max também foi confirmada no JAR CR2 do forbidden-series.
Bless pode elevar o nível e expor o erro, mas não cobra mana continuamente.

A CR9 usa reduções de tempo de 25%, 50%, 75% e no máximo 90%, com denominador
mínimo de um tick. Amplificadores extremos não causam overflow na soma do nível.
O Playground já corrigido nesta manutenção protege os tempos intermediários e
pode provocar recuperação brusca no lugar da drenagem. A CR9 corrige a fórmula
no AM2.5, preservando os eventos e talentos do addon.

Bless preserva efeitos bons quando não consegue melhorá-los ou não tem orçamento.
Prepara a substituição antes de parar/remover o anterior. Corrige a conversão
entre amplificador zero-based de PotionEffect e nível one-based de BuffEffect,
e compartilha o orçamento de amplificação entre os buffs.
O auxiliar também usado por Curse preserva o nível e a duração ao restaurar um
efeito removido. Curse não recebeu uma reformulação.
Haste/Frost Slow acima do nível III mantêm o modificador máximo implementado na
aplicação direta, sem criar uma nova escala ilimitada.

## Validação

A compilação Java 8 e os quatro verificadores anteriores passaram.
[Fontes e instruções do teste](../tools/integration/bless-mana-regen/README.md).

A reprodução usou CR8 com o Playground original copiado do forbidden-series,
com a mesma fórmula presente na CR2 desse pack. Mana Regen V fez a mana cair de
**500 para 492** em um ciclo. O teste original omite nível IV, pois seu tempo zero
reproduz o ciclo infinito do transformador original já tratado antes.

Na CR9 passaram, em cliente mínimo e com todos os mods da teste-ars:

- Regen I a VI, XXI e amplificador Integer.MAX_VALUE, sem drenagem e com teto.
- Tempos de base zero, um e negativo, sem valores não finitos.
- Uso real de Bless com Buff Power produz nível V e regenera mana.
- Buffs nativos fortes, longos e acima do orçamento preservados.
- Buff vanilla forte preservado e orçamento compartilhado entre dois efeitos.
- Haste/Frost Slow altos continuam aplicando o modificador de movimento.
- Auxiliar compartilhado com Curse restaura nível/duração sem perdas.
- Archmage regenera sem travar e Atronach mantém a supressão.

O cliente é necessário porque Bless referencia classes de partículas de cliente.
Os testes usam mundos descartáveis e jogador simulado, não uma sessão inteira
no forbidden-series. Esta mudança altera apenas AM2.5. O Playground e os demais
mods não mudam. Configs e saves do forbidden-series permanecem intactos.
O slide antes/depois é um retrato histórico da CR8, anterior a este reparo.

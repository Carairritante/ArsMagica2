# Teste de Bless e regeneração de mana

`BuffProbe.java` é um mod temporário de teste, não integra o JAR do AM2.5.
Execute somente num cliente Forge 1.7.10 descartável, com AM2.5, AnimationAPI,
CoFHCore e, para os testes de talentos, Playground atualizado.
O cliente é necessário porque Bless referencia classes de partículas do cliente.

O mod abre automaticamente um mundo plano descartável, cria um jogador simulado,
executa os casos no servidor integrado e encerra o processo. Não instale no seu
modpack de jogo. O resultado é `buff-regen-result.txt` no diretório do teste.

Compile com destino Java 8, usando Forge e Minecraft no classpath. Os acessos às
APIs dos mods usam reflexão, sem empacotar classes desses mods. Empacote apenas
`buffprobe/*.class` em um JAR dentro da pasta mods do ambiente descartável.

Para reproduzir o comportamento anterior, use AM2.5 CR8 e Playground Beta 0.12.1
original com `-Dbuffprobe.baseline=true`. O nível IV é omitido nessa modalidade:
o tempo zero reproduz outro ciclo infinito no transformador original, já corrigido
nos patches anteriores. A reprodução de drenagem usa o nível V e superiores.
Não use Archmage no teste original, pois também reproduz aquele ciclo.

Os testes da CR9 cobrem níveis I a VI, XXI e valor extremo de amplificador,
tempos inválidos, uso real de Bless, buffs nativos e vanilla, orçamento compartilhado,
níveis de Haste/Frost Slow, auxiliar compartilhado com Curse, Archmage e Atronach.
O modo corrigido exige Playground com o reparo prévio do ciclo infinito.

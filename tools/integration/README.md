# Regressão local das molduras — CR19

`FrameWatcherProbe.java` é um mod de teste descartável. Não o instale em uma
instância de jogo normal e não o inclua no JAR do AM2.5.

Compile para Java 8 com Forge e suas bibliotecas no classpath. Empacote somente
`framewatcherprobe/*.class` no JAR de teste, junto ao AM2.5 CR19 em cópia do
Forbidden. A propriedade JVM `frameprobe.reference` aponta para o JAR CR18
usado na comparação; apenas o observador e suas classes internas são carregados
isoladamente, com o restante do jogo fornecido pela instância CR19.

O teste para o servidor brevemente num ponto controlado e chama a rotina antiga
no cliente. Confere a alteração indevida do contador ou da fila de remoção e
verifica que a CR19 não toca esses dados. O log pode registrar o aviso de acesso
fora da tarefa durante essa reprodução intencional da CR18.

Também verifica receita real do Compêndio, progresso visual sem criar o item no
cliente, consulta a chunks ausentes sem gerá-los, quatro produtores concorrentes
de callbacks e descarregamento do mundo. A observação posterior dura 150 segundos
por padrão, ajustável por `frameprobe.soakSeconds`. O resultado deve terminar
com `FRAME_WATCHER_GAMEPLAY=PASS`, saída zero e mundo ainda conectado.

O teste reproduz o defeito de acesso entre tarefas. Ele não provoca de propósito
uma mutação concorrente da lista de tiles do Minecraft e não prova que outros
mods estejam livres de condições de corrida. O CI executa separadamente o
verificador de bytecode `VerifyItemFrameWatcherJar` durante o build.


## Intervalos de pedidos — CR44

`ServerThrottleProbe.java` é um mod de teste descartável para cópia de servidor
Forge do Forbidden Series. Compile para Java 8 com Forge/bibliotecas do servidor
no classpath e empacote a classe no JAR separado do probe. Nunca inclua o probe
no JAR do AM2.5 ou em uma instância de uso normal. Ele cria blocos/entidades em
um mundo descartável e encerra o servidor ao terminar; não usar saves do jogador.

A cópia deve conter AM2.5, OreSpawn, Wizardry e ChromatiCraft, com os mods
exclusivos de cliente removidos. O teste usa dois Forge FakePlayers e chama os
handlers reais de pedidos e a fila de buscas no lado servidor. Os FakePlayers
recebem NetHandlerPlayServer e NetworkDispatcher com canal embarcado fechado:
as respostas são capturadas na fila de saída sem usar sockets. Não conecta
clientes remotos nem valida a interface gráfica ou o transporte pela rede.

Confere 100 sequências parar/iniciar no mesmo tick, independência entre máquinas,
início sem espaço, limpeza dos manequins e recuperação após cinco ticks. Na
bússola, confere 100 alvos inexistentes, intervalo de vinte ticks sem renovação
por pedidos bloqueados, ausência de cobrança nas rejeições/cancelamento,
independência entre jogadores e duas buscas válidas pela fila cobrando exatamente
20.000 de mana uma vez por jogador. Resultado em `throttle-results.txt`, terminando
com `SERVER_THROTTLES=PASS`, mais saída zero e inspeção dos logs.

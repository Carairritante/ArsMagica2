# Sobrecarga de exaustão — CR44

A pedido do usuário, exceder a exaustão gera Silêncio por 600 ticks (30 segundos a 20 TPS). A sobrecarga não causa dano, Wither, raios, teleporte aleatório nem morte direta. Custos de mana, exaustão por bloco de Dig e regeneração permanecem.

O código anterior colocava as punições dentro de `setCurrentFatigue`, incluindo `setHealth(0)` e `onDeath` em valores altos. Esse setter também era chamado por NBT e restauração de jogadores. Ele agora somente limita valores e sinaliza a atualização. Não cancelar globalmente dano mágico ou mortes: a correção pertence à origem da sobrecarga.

Somente `addBurnout`, ao receber aumento real que exceda a capacidade, pode aplicar a penalidade a um jogador vivo em survival no servidor. Cada ocorrência desarma o gatilho até a exaustão recuperar para 80% da capacidade ou menos. Há ainda um intervalo mínimo de 600 ticks entre ocorrências. Pulsos da mesma Zone/AoE não reiniciam a duração; um Silêncio preexistente também não é encurtado ou estendido. A exaustão continua limitada como antes a capacidade menos um.

Decisão posterior do usuário: o Mana Shield pode bloquear o Silêncio de sobrecarga pelas regras normais de resistência a status e seu custo de mana. Não há exceção ou enfraquecimento da proteção. O estado por jogador não contém referências globais a mundos ou entidades. Carga de dados não aplica punição. As verificações usam o servidor, não uma preferência gráfica do cliente.

Playground e outros mods não foram editados. Os descritores públicos de ExtendedProperties permanecem; o transformador real do Playground para manaBurnoutTick deve continuar válido. Manter CR44 e identificar o arquivo pelo checksum. Para a regra funcionar em multiplayer, o servidor precisa do AM2.5 corrigido.

Resultados, limites dos testes, backup e SHA-256 estão no relatório local da tarefa `burnout-silence-20261121/RESULTADO.md`. Não anunciar resultados dedicados antes de conferir a execução correspondente.

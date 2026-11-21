# Recall dimensional — CR44

A extensão de Recall pertence ao AM2.5. Os identificadores opcionais de componentes são lidos do registro da etapa ativa, sem importar classes Java do Playground. Sem os componentes extras, o Recall normal continua na mesma dimensão, como antes. A marca pertence ao conjurador; o jogador alvo é transportado para ela.

## Combinação solicitada

Na mesma etapa: Recall + EnderIntervention + NetherIntervention, ou Recall + CrossDimensionalIntervention. Apoios em outra etapa não habilitam a extensão. Os apoios não escolhem outro destino nem executam um segundo teleporte. O custo continua sendo o custo normal dos componentes incluídos; não foram alteradas receitas, habilidades aprendidas ou progressão.

O componente Recall prepara a solicitação e o laço nativo confirma somente ao retornar sucesso. Uma rejeição do addon, exceção ou etapa fracassada não confirma a solicitação. As guardas dos apoios ficam na entrada dos componentes (Ender no núcleo e dois adaptadores opcionais em memória), depois da validação do ponto de chamada. Os hooks de Silver não foram removidos/desativados; não há cópia da política de compras/progressão do addon.

## Segurança e ciclo de vida

Transferência de jogadores ao fim do tick, fila limitada a256 solicitações e uma por jogador, limpeza por logout/descarregamento. A marca é capturada no momento do componente. Coordenadas precisam ser finitas e respeitar limites do mundo. O destino precisa estar registrado, disponível e ter altura de construção válida; não se usa a altura do terreno do Nether para rejeitar marcas sobre o teto.

Distorção Astral no conjurador/alvo continua bloqueando, e o Escudo de Mana pode resistir a movimentação hostil. Usuário e passageiros devem desmontar primeiro. A implementação não cria plataformas, portais ou terreno e não aplica a escala8:1 do Nether às coordenadas salvas. Retorno do End confirma inclusão na lista de entidades; recusa do spawn tenta recuperar o jogador na origem. Os dados de inventário não são copiados nem restaurados.

A compatibilidade com o provedor de sonhos do Witchery usa métodos verificados no JAR instalado e somente os três estados booleanos que essas rotinas alteram. As flags anteriores são recuperadas se a transferência falhar. Isso não é uma auditoria geral das regras privadas de todas as dimensões/mods.

Recall comum para entidades e os rituais existentes permanecem; a nova travessia entre dimensões está limitada a jogadores. Um uso direto do componente fora do laço nativo não confirma uma travessia preparada.

## Compatibilidade binária

O Playground0.12.1 usa a variável local14 de SpellHelper.applyStageToEntity como componente. Não acrescentar um booleano antes do foreach; isso desloca o slot e causa VerifyError. Um teste verifica o contrato. No ASM legado do Forge, FrameNode deve receber new Object[0] para pilha vazia; null falha durante o carregamento. As duas guardas opcionais são verificadas nos JARs reais e não se duplicam numa segunda passagem.

Os resultados do build, servidor e cliente e o SHA-256 da entrega estão no relatório da tarefa. Somente testes aprovados devem ser anunciados; probes e dependências de inspeção não entram no JAR.

# Melhorias da Bússola Astral — CR44 — 17/11/2026

## Uso

Cada linha mostra o nome da estrutura, seu mod e a dimensão da consulta.
Os botões Mod e Busca alternam os filtros disponíveis no catálogo recebido.
A pesquisa aceita nome traduzido, nome original, ID e nome do mod.

O botão Favoritar ou o botão direito na linha marca uma estrutura. A aba
Favoritos reúne essas entradas, priorizadas também na lista normal.
A aba Histórico guarda os últimos 12 alvos observados, com coordenadas, separados
por mundo/servidor e jogador. Selecionar um histórico escolhe o tipo para uma
nova busca; não concede coordenadas autenticadas nem dispensa o custo de mana.

Divinar mantém o menu aberto e mostra o estado recebido do servidor. Enquanto
há uma busca, o mesmo botão permite cancelá-la. A confirmação de cancelamento
não promete reembolso se a busca já tiver terminado antes de o pedido chegar.
Uma consulta sem resultado informa o limite dos dados e do alcance disponíveis,
sem afirmar que a estrutura inexiste no mundo inteiro.

A engrenagem abre os ajustes da HUD: arraste a prévia, altere o tamanho entre
50% e 150% e a transparência do fundo entre 0% e 100%, ou alterne os cantos no
botão Posição. Restaurar recupera os valores iniciais. As preferências são locais
em `config/AM2/AstralCompass.properties` e não modificam permissões do servidor.

## Implementação e limites

- A correção anterior da inicialização do servidor integrado foi preservada.
- Mod conhecido é derivado do ID e do catálogo; entradas sem identificação
  confiável mostram origem não identificada. Não atribuímos essas entradas ao
  Minecraft apenas porque o identificador não contém namespace.
- Dimensão da consulta não significa lista completa das dimensões de geração.
  A bússola mantém a pesquisa na dimensão validada pelo servidor, sem carregar
  mundos adicionais para preencher o filtro. Dimensões desconhecidas mostram ID;
  o nome da dimensão atual também acompanha o catálogo num trailer opcional.
- O protocolo do catálogo 79 foi preservado; clientes anteriores ignoram o
  trailer. Estado de busca usa o novo pacote 80 e cancelamento usa a ação 3.
  Cliente e servidor precisam deste build para a funcionalidade completa.
- A fila mantém o limite por jogador e orçamento compartilhado, com cancelamento
  no tick do servidor após validar o item e slot. Favoritos/histórico não escrevem
  inventário, mana ou coordenadas no servidor.
- Histórico é limitado e não mantém referência forte a mundos ou jogadores.
- Permanecem as limitações anteriores de retroindexação e alturas negativas.

## Validação desta entrega

Por solicitação explícita do usuário, foi executada apenas a compilação
`gradlew assemble`. Não foram executados testes, verificadores, cliente ou
servidor nesta etapa. O teste em jogo será realizado pelo usuário no
Forbidden Series - Teste. Os testes aprovados na entrega anterior se referem
ao build anterior e não validam estas novas funcionalidades.

O nome permanece `AM2.5-LTS-1.6.11-LE-CR44.jar`; distinguir pelo SHA-256 da entrega.

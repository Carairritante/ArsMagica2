# ChromatiCraft e Bússola Astral — CR44 — 17/11/2026

## Problemas identificados no código

O catálogo listava seis ruínas (CAVERN, BURROW, OCEAN, DESERT, SNOWSTRUCT e
BIOMEFRAG), sem pylons. Pylons pertencem a outro gerador e cache.

O adaptador antigo exigia SUCCESS e `StructureGenData.generatedLocation` não
nula. No JAR V33a inspecionado, o construtor não preenche esse campo;
`recacheStructureTile` chama `markChunkStatus`, que atualiza o status, mas não
atribui a localização exata. A chave WorldLocation identifica o canto do chunk
com Y=0. Assim, um registro de geração bem-sucedida podia ser descartado.

## Correção no AM2.5

- Pylon (qualquer cor) e 16 opções específicas, conforme CrystalElement do JAR.
- Leitura do `PylonGenerator.colorCache`: cor e WorldLocation de PylonEntry.
- Leitura dos tiles já instanciados nos chunks carregados: CrystalPylon.getColor
  e StructControl.getStructureType, com coordenadas do próprio tile.
- Coordenada exata de generatedLocation quando disponível. Caso contrário,
  SUCCESS fornece o centro horizontal do chunk, marcado Região registrada
  (posição aproximada), sem altura conhecida. Não tratamos o Y=0 da chave como
  altura real da estrutura.
- Controlador carregado tem preferência sobre fallback regional do mesmo chunk.
- Exclusão do alvo atual leva em conta a região quando a posição é aproximada.

O adaptador usa reflexão sobre os JARs V33a/DragonAPI V33b do pack. Não chama
generate, getNearestPylonSpawn, recacheStructureTile, getWorld, load ou provideChunk.
As rotinas de leitura TileEntityCache.get/keySet e WorldLocation.getChunk foram
inspecionadas por bytecode: consultam metadados; getChunk cria um WorldChunk
imutável, não carrega o terreno.

## Limites

Até 8.192 chunks já carregados são considerados, priorizados por distância.
Tiles são consultados incrementalmente com limite de 65.536 por busca e
snapshot de até 4.096 por chunk. Não são varridos blocos nem criados tiles.
Há uma nova verificação de chunk carregado imediatamente antes de consultar
o tile, para evitar reabrir chunks que tenham sido descarregados entre ticks.

Os caches opcionais têm leitura limitada a 65.536 entradas inspecionadas e
8.192 candidatos por consulta. A leitura de ruínas filtra dimensão e região
antes do limite de candidatos. Mapas são copiados para não reter iteradores
mutáveis entre ticks. Falhas opcionais são registradas uma vez por categoria.

Registro não garante que a construção ainda esteja intacta; cache pode conter
dados históricos. Ruínas antigas sem controlador carregado e sem registro no
ChromatiCraft continuam sem reconstrução universal. O raio selecionado, custos,
nível, orçamento global e timeout da Bússola Astral foram preservados.

## Validação e instalação

Somente compilação assemble nesta etapa, sem testes/verificadores e sem abrir
o Minecraft, mantendo o fluxo de teste manual solicitado pelo usuário.
O resultado em jogo ainda será confirmado no Forbidden Series - Teste.
Nome preservado CR44; distinguir pelo checksum da entrega.

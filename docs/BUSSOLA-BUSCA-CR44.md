# Busca da Bússola Astral — CR44 — 17/11/2026

## Controles

- Alcance alterna o raio horizontal entre 2.000, 5.000 e 10.000 blocos.
  O limite vale também para posições já registradas, não apenas previsões.
- Encontrar outra ignora o alvo atual do mesmo tipo e dimensão, usando o NBT
  autenticado do item no servidor. Não aceita coordenadas enviadas pelo cliente.
  Ignora somente o alvo atual; não exclui todo o histórico. Não se aplica ao
  bosque, pois outro ponto do mesmo bioma não representa necessariamente outro bosque.
- Cada consulta bem-sucedida mantém o custo de 20.000 de mana e o requisito
  de nível 50. Cancelamento, falha, timeout e ausência de resultado não gastam mana.
- A interface exibe etapas e número de verificações; esse número não é uma
  porcentagem nem quantidade de chunks gerados.
- Alvos indicam Registro do mundo, Posição prevista, Área do bioma ou origem
  não informada para alvos antigos. Registro do mundo não garante que os blocos
  continuem intactos: a estrutura pode ter sido alterada ou destruída.

## Motor de busca

1. Dados MapGenStructureData existentes para o tipo consultado.
2. Bounding boxes já presentes nos caches dos geradores MapGenStructure ativos.
3. Índice AM2 por tipo, com snapshots estáveis e invalidação quando há inserção
   ou remoção. Deduplicação por posição completa, preservando diferenças de altura.
4. Registros dos adaptadores opcionais já existentes.
5. Previsões vanilla e amostragem de bioma. Para vilas e templos calcula-se o
   candidato da região diretamente com RNG privado, evitando testar cada chunk.

O índice preserva inteiros assinados de altura entre -4096 e 4095 para acomodar
extensões verticais sem dependência delas. Registros anteriormente descartados
não reaparecem automaticamente; só podem ser recuperados se outra fonte ainda
guardar os dados. O formato NBT existente permanece legível.

Geradores e suas configurações ativos não são alterados. A consulta não chama
construtores de estruturas, loadChunk, provideChunk, geração de terreno ou varredura
de blocos. A recuperação adicional lê metadados já disponíveis. Mods sem dados
persistidos ou cache reconhecível continuam dependendo das notificações de geração.

## Limites e protocolo

- Até 32 buscas ativas e 64 pedidos pendentes. Continuam o orçamento global de
  512 amostras por tick e o prazo cooperativo de 2 ms entre amostras. Isso não é
  uma garantia rígida para o tempo de cada chamada externa ou primeira leitura de dados.
- Índice: 8.192 posições por dimensão; snapshots de cache de geradores: até 8.192
  bounding boxes; chaves de dados persistidos: até 65.536 por consulta.
- Buscas normais têm limite de 2.400 ticks; raio de 10.000 permite 12.000 ticks.
  O timeout é informado sem cobrar mana. Sob concorrência, uma consulta pode
  expirar antes de examinar todos os dados disponíveis.
- Campos/opções são validados no servidor. Extensão opcional do pedido 79:
  int raio e boolean ignorar atual. Assinaturas públicas antigas foram mantidas.
- Catálogo anuncia capacidade 2 no trailer opcional. Servidores sem essa
  capacidade mantêm busca básica e os novos controles ficam indisponíveis.
  Servidores sem mensagens de estado conservam o fechamento do menu ao consultar.
- Pacote 81 informa etapa/amostras/raio, com frequência limitada por busca;
  pacote 80 conserva seu formato de estado anterior, acrescentando o código 7 de timeout.
- NBT `targetSource` guarda a origem do resultado. Histórico local usa formato
  versionado e continua lendo registros anteriores sem origem.

## Entrega

Nome mantido CR44. Compilação `assemble` somente, sem testes automatizados,
verificadores, cliente ou servidor por solicitação do usuário. O teste manual
ficará no Forbidden Series - Teste. Não reutilizar resultados de builds anteriores
para afirmar validação desta busca.

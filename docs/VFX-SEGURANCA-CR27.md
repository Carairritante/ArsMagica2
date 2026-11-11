# Segurança dos novos VFX — CR27 local

Auditoria da CR26 recebida em 09/11/2026. O arquivo instalado tinha SHA-256
`03c9858c3a3c6e52c918ced1a9f2f1ab0e22e3e5c9a4ae0bae44999e46c0b1c1`.
A versão de manutenção anterior usava outro conteúdo sob o mesmo sufixo CR26.
Por isso a correção atual recebe CR27 e deve ser identificada também por checksum.

## Constatações

O delta recebido acrescentava ou alterava três fontes de renderização e 102
imagens. As classes novas são de cliente. Não foi reproduzido um novo crash
no servidor dedicado durante a inicialização e os cenários exercitados.

Foram encontrados custos e riscos no cliente: reconstrução repetida de ItemStacks
na leitura Solar/Lunar a cada quadro; partículas criadas dentro do render em
função do FPS; ausência de limite de distância/quantidade para os grandes círculos;
e leitura sem limite de contagens de estágios ou de afinidades inválidas.
Esses problemas podiam causar travadas gráficas ou encerrar a renderização.

## Correções

- Análise visual de livros, varas e pergaminhos reaproveitada por quatro ticks.
  Troca de item, reinício da conjuração ou retorno após interrupção renovam o estado.
- Uma emissão de partículas por jogador/tick elegível, respeitando partículas
  reduzidas/mínimas e uma distância de 32 blocos.
- Até oito conjuradores desenhados por quadro, com prioridade ao jogador local;
  distância de 96 blocos e teste de visibilidade que inclui o anel completo de
  75 blocos e as camadas no céu.
- Constantes geométricas reutilizadas, evitando arrays por quadro.
- Metadados visuais com mais de 64 estágios, 32 grupos ou 256 partes em uma lista
  são ignorados pelo efeito. Afinidade inválida usa NONE no renderizador do livro.
- Cache com chaves fracas, sem referência de volta ao jogador, limpo no unload.

O tamanho, as cores, as cinco famílias e as fases dos efeitos foram preservados.
As 1.660 imagens PNG são idênticas às do JAR recebido. Custos de magia, dano,
receitas, progressão e comunicação de rede não foram alterados. O Playground e
os addons continuam separados e não foram editados.

## Validação e limites

A compilação Java 8 e os seis verificadores do JAR reofuscado passaram. A CR26
e a CR27 passaram em servidor dedicado mínimo sem bibliotecas gráficas, incluindo
as regressões de efeitos sem conjurador, dano, morte e expiração de Storm.

O teste gráfico usa cópia descartável do Forbidden Series do Prism, sem copiar
saves ou contas, com livros comum/Arcano, primeira/terceira pessoa e jogador
remoto. A carga inclui oito jogadores simulados e os cinco arquétipos com três
modificadores Solar/Lunar, atravessando suas três fases. Os resultados finais,
checksum e medições dessa execução ficam no relatório local da auditoria.

A primeira instrumentação de medição não registrou o evento gráfico no barramento
Forge. Ela serviu para capturas e cenários visuais da CR26, mas seu teste de
medição ficou incompleto; isso não foi classificado como crash do mod. O lançador
foi corrigido para o ensaio da CR27.

Não se trata de teste prolongado, de um servidor real com vários computadores ou
de garantia de FPS. O custo de geração do mundo e de outros mods continua fora
do controle desta correção. Nenhum commit ou publicação foi solicitado nesta etapa.

## Resultado final desta execução

CR27 aprovada no servidor dedicado e na cópia do Forbidden (214 JARs de origem),
com oito conjuradores e cinco famílias de efeito. Trinta renders adicionais no
mesmo tick não geraram partículas adicionais. Cinquenta renders com análise
reaproveitada levaram 46,7262 ms no total e alocaram 660.312 bytes na medição
controlada com pixels descartados; isso não mede FPS nem o custo final de GPU.
O ensaio da CR26 não forneceu uma medição comparável, portanto não há percentual
de ganho de desempenho declarado. Dados visuais inválidos e limpeza do cache
passaram nas verificações.

A primeira captura do livro comum em primeira pessoa coincidiu com a sincronização
do inventário inicial e mostrou outro manual. Esse quadro foi excluído da
conferência do livro comum; sua terceira pessoa e os cenários Arcano, vazio e
remoto foram conferidos. A próxima execução do lançador espera essa sincronização.

JAR final: AM2.5-LTS-1.6.11-LE-CR27.jar. SHA-256:
`c4f6e2935e0aefbb2389a89a9fea1688abea3d1be26e933c72db471b097ffb53`.
Instalado no teste-ars com backup da CR26 recebida e outros mods preservados.
A instância original do Forbidden não foi editada. Sem publicação no GitHub.

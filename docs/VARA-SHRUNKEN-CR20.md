# Vara Arcana e Shrunken — CR20

A Vara Arcana consultava o item na mão sem verificar se existia. Se uma linha
de pesca continuasse ativa enquanto a mão estivesse vazia, a renderização do
ícone lançava NullPointerException. A CR20 trata mão vazia e ausência de jogador,
preservando os ícones normal e lançado.

O Shrunken aplicava escala antes de posicionar o jogador e novamente no renderer
de entidades vivas. Isso reduzia também o deslocamento em relação à câmera.
Além disso, o handler atribuía aos jogadores remotos o offset de visão do cliente
local, embora suas posições de rede representem os pés. O corpo podia afundar.

A CR20 mantém a escala do renderer ancorada na entidade e retira a escala extra
do evento do jogador. O handler preserva os pés e a interpolação ao redimensionar;
jogadores remotos e do servidor usam offset zero. Só o cliente local recebe o
offset proporcional, restaurado ao término. O ajuste da câmera usa a altura
proporcional em vez de um deslocamento fixo de um bloco. O efeito passa a
reduzir o jogador a um quarto do tamanho original (2x menor que a redução
anterior à metade) e mantém sua transição e progressão nativas.

O processamento ocorre em LivingUpdateEvent, uma vez por atualização; eventos de
salto não avançam o progresso nem repetem o redimensionamento.

## Verificação

O build executa os verificadores de manutenção existentes. O teste autoral
tools/integration/RodShrinkProbe.java compara a CR19 e a versão corrigida em
cliente real: reproduz o NPE da vara, testa o desenho do item, reproduz o offset
incorreto do avatar remoto e confere a matriz de escala única da correção.
Confere posição dos pés, tamanho e recuperação em jogador local, avatar remoto
e jogador do servidor integrado, e gera uma imagem de comparação no mundo.

Esta descrição registra a cobertura do teste. O resultado da execução e o hash
do JAR efetivamente verificado devem acompanhar a entrega local. O probe não é
incluído no mod jogável. Não foram alterados o Playground ou outros addons.

## Resultado local

Build e verificadores aprovados. A execução em cópia descartável do Forbidden
passou com saída zero: crash CR19 reproduzido e corrigido no desenho real do
item; offset remoto antigo reproduzido; escala única, ancoragem dos pés e
recuperação local/remota/servidor verificadas. Imagem conferida e mundo ativo
por mais 30 segundos. A instância original permaneceu intacta.

Não houve teste em servidor Thermos real nem com dois clientes independentes.
O Ring of Recursion ficou fora desta atualização por decisão do usuário.

SHA-256 do JAR local testado: `14f72cac107e6a0facd7b33aeb9417115dffbd1cc23a0964c32b439ae56e0945`.

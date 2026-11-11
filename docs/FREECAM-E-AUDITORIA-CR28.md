# Compatibilidade visual e revisão da CR28 — local

A entrada desta revisão foi a CR27 local do teste-ars, SHA-256
`7efd12f5e2c35be75bb129ea1d5e3aa80a5eb212ca258b26ca745804422ce133`.
Ela acrescentava paletas por afinidade aos círculos normais e um ajuste do
comparador de teclas para categorias com traduções iguais. Essas mudanças foram
preservadas. O comparador e a tradução do Freecam já estavam na entrada recebida.

## Correção do Freecam

O Freecam de Zergatul 2.3.1 substitui temporariamente as coordenadas da entidade
vista pela câmera durante renderWorld. No RenderWorldLastEvent, o jogador local
pode portanto ter coordenadas da câmera, embora seu corpo permaneça em outro lugar.
O renderizador do círculo lia essas coordenadas e levava o efeito até a câmera.

A CR28 captura a posição interpolada dos jogadores no início do quadro, antes do
render do mundo, e usa essa posição para os círculos e suas partículas. A posição
da câmera continua usada para a transformação de perspectiva e os limites de
distância. O cache tem chaves fracas, valores com apenas três coordenadas e limpeza
no descarregamento do mundo. Não há importação, reflexão ou dependência do Freecam
no código de produção do AM2.5. Nenhuma posição física ou regra de conjuração foi
alterada.

Os limites da CR27 foram preservados: oito conjuradores, distância de 96 blocos,
análise por quatro ticks e partículas limitadas por tick. Todas as 1.660 imagens,
as novas paletas, as traduções e o ajuste do menu de teclas continuam idênticos
aos da entrada. Playground e AM2 Archmage não foram editados.

## Servidor e registros antigos

A compilação Java 8 e os seis verificadores do JAR passaram. A CR27 recebida e a
CR28 passaram em servidor dedicado mínimo sem classes gráficas e sem Playground.
Foram exercitados a abertura do container da Mesa de Inscrição, efeitos sem
conjurador, preservação de autoria do dano, morte e expiração de Storm.

O registro antigo de 09/11 às 17:34 mostrava falha ao carregar
ContainerInscriptionTable, com ClassReader recebendo bytes nulos no transformador
do Playground. A classe existe e seu código não mudou no JAR recebido às 18:06.
A falha não foi reproduzida no teste dedicado desta revisão. O registro das 17:47
apontava o construtor da lista de teclas; a entrada atual já continha um ajuste
para categorias com traduções iguais. Não se atribuiu uma causa definitiva aos
bytes ausentes do registro antigo sem reprodução.

## Validação gráfica

Aprovada a cópia descartável do Forbidden Series (214 JARs de origem), com
substituição explícita apenas nessa cópia pelo Playground do teste-ars e adição
do Freecam 2.3.1. Seus saves e contas não foram copiados; a instância original foi
preservada. O teste abriu o container da Mesa de Inscrição no servidor integrado
com esse Playground e abriu o menu de teclas após criar categorias distintas com
a mesma tradução. Os dois caminhos passaram sem reproduzir os registros antigos.

Também passaram os livros comum, Arcano e vazio, primeira/terceira pessoa, o uso
sincronizado de um jogador remoto simulado, oito conjuradores com cinco famílias
Solar/Lunar, as onze paletas normais, dados visuais inválidos e a limpeza dos
caches. Trinta renders extras no mesmo tick produziram zero partículas extras.
Cinquenta renders com pixels descartados levaram 40,0515 ms e alocaram 673.912
bytes nesta execução; isso não é uma medição de FPS nem comparação controlada
com outras versões.

O Freecam real foi ativado, afastado e reposicionado. Durante RenderWorldLast,
a entidade local estava em X=46,5, mas o snapshot do corpo permaneceu em X=32,5,
Y=81,62 e Z=32,5. As capturas e a verificação das partículas confirmaram a origem
no corpo tanto no círculo normal quanto no Solar. A desativação do Freecam e a
limpeza do cache também passaram.

Reprodução: `tools/integration/run_vfx_safety_probe.py` aceita `--freecam-jar`
para esse cenário opcional e `--playground-jar` para testar um JAR específico
somente na cópia descartável. Sem esses argumentos, o AM2 continua sem depender
desses mods.

JAR final: `AM2.5-LTS-1.6.11-LE-CR28.jar`. SHA-256:
`e38b06555297227f77f7483986fb03656d8035ab7b136a339be323b13d3acf13`.

Não é um ensaio prolongado nem teste com vários computadores reais. As medições
controladas do renderizador não garantem FPS nem eliminam travadas de geração de
mundo ou de outros mods. Esta versão permanece local, sem publicação no GitHub.

# Giz do Arquiteto Arcano — CR6

Ferramenta reutilizável para orientar os 30 rituais de magia nativos apresentados
no Arcane Compendium, incluindo World Fusion, Purification, Corruption, os
rituais Hourglass, Recall e os rituais espaciais. A primeira versão cobre esses
rituais de magia; as invocações dos Guardiões usam outro sistema.

## Como usar

1. Procure **Giz do Arquiteto Arcano** ou **Arcane Architect's Chalk** no NEI.
   O registro é `arsmagica2:ritualArchitect`.
2. Segure a ferramenta e clique com o botão direito no ar para abrir o menu.
   Agachar e clicar em um bloco também abre o menu.
3. Escolha um ritual. No survival, valem os conhecimentos já descobertos no
   Compendium e a configuração nativa de descoberta; o criativo mostra todos.
4. Clique no topo do chão para marcar o centro. Se o bloco central correto já
   estiver colocado, clique no topo dele.
5. Use **Girar** no menu se quiser mudar a orientação. Mantenha a ferramenta
   marcada na barra rápida enquanto usa giz comum, velas e blocos para construir.
6. Verde indica peças corretas, azul indica peças faltantes e vermelho indica
   posições bloqueadas ou sem chão de apoio. O contorno roxo mostra a região dos
   ingredientes no chão; o painel apresenta o que falta e como ativar o ritual.
7. Para esconder o desenho, use **Limpar centro** ou retire a ferramenta da barra
   rápida. A projeção também desaparece ao sair da dimensão ou afastar mais de
   32 blocos do centro.

No criativo, **Montar** coloca o giz, as velas e o bloco central. O servidor
confere a área inteira antes de construir e respeita os eventos de proteção do
Forge. A ferramenta não destrói blocos que estejam no caminho. Ingredientes,
magias de ativação e demais condições continuam sendo usados normalmente.

No survival, a ferramenta apenas orienta; os materiais normais ainda precisam
ser colocados pelo jogador. A ferramenta não gasta durabilidade ou mana.

## Receita

Produz uma ferramenta, usando Wizard's Chalk novo:

| | | |
|---|---|---|
| Cinza arcana | Essência arcana | Sunstone |
| Wizard's Chalk | Nether Star | Runa em branco |
| Moonstone | Essência arcana | Cinza arcana |

## O que a conferência indica

O desenho é gerado a partir das definições reais dos rituais. Os reagentes são
contados na região definida por cada ritual, sem modificar pilhas ou consumi-las.
As runas de Recall dependem da combinação e da ordem do destino; o guia informa
as runas presentes, mas não confirma que o destino existe ou corresponde à chave.

Uma estrutura completa não confirma condições externas, nem executa o ritual.
O guia não concede World Fusion, habilidades, pontos ou recompensas. A entrada
de instruções não desbloqueia outros capítulos por associação.

## Validação

Compilação e verificações do projeto concluídas com sucesso, usando Java 8 para
o mod. O teste integrado em servidor Forge descartável confirmou:

- Registro do item e da receita com Nether Star.
- 30 rituais em quatro orientações: 120 montagens válidas.
- Survival e pedidos forjados de montagem sem alterações no mundo.
- Conferência de reagentes sem consumo ou modificação das pilhas, incluindo
  as runas dinâmicas de Recall.
- Recusa de posições bloqueadas, sem apoio ou sem espaço para o Obelisco.
- Limites de distância, dimensão e regiões descarregadas.
- Cancelamento pelos eventos de proteção do Forge com restauração da montagem.
- Processamento na thread do servidor, troca de ferramenta e persistência NBT.

O encaminhamento do clique do cliente foi conferido no bytecode. A aparência da
projeção e do menu ainda precisa de conferência visual dentro do jogo; não foi
executado um cliente gráfico nesta validação.

Artefato local testado: `AM2.5-LTS-1.6.11-LE-CR6.jar`, SHA-256
`ead7c7a7c8d313bda538a6c875f30e7f55fbd5bf7e606343882c07c87777a388`.
O JAR do Actions tem seu próprio checksum, publicado junto do arquivo.

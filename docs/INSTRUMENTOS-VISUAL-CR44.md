# Bússola e bate-estacas: apresentação — 17/11/2026

As três frentes desta entrega são visuais e do lado cliente.

1. Novas poses dos modelos existentes na primeira e terceira pessoa. A bússola
   fica menor e inclinada para expor o mostrador. O bate-estacas tem um renderer
   de item próprio, com escala reduzida, sem reutilizar a transformação de bloco
   colocado. As geometrias, UVs e animações existentes não foram exportadas ou alteradas.
2. Ícones vetoriais de inventário/hotbar, desenhados diretamente pelo renderer
   INVENTORY do Forge em 16×16. São emblemas estáticos legíveis; o apontamento
   do alvo continua no modelo equipado e na HUD de navegação. Não há PNG novo.
3. Painéis e botões internos de instrumentos. A bússola usa o mostrador como
   emblema; o bate-estacas usa a máquina e graduação do medidor. Presets e botões
   selecionados acompanham o estado sincronizado. Texto é limitado à largura da
   tela e ações ficam abaixo dos dados. Os IDs e envios de ações foram preservados.

O modelo colocado, o manequim, dano, intervalos, busca de estruturas, mana,
progressão, dados salvos e tráfego de rede não foram alterados. A HUD circular
de navegação e as preferências existentes também foram preservadas.

Comparação binária: seis classes cliente alteradas e três novas; todos os assets
idênticos. Nenhuma classe comum/servidor foi alterada. Apenas assemble nesta
etapa, sem testes automatizados, cliente ou servidor. Enquadramento na mão e
aparência final ainda precisam do teste visual do usuário no pack.

Memória compartilhada MCP indisponível nesta sessão; o registro de continuidade
foi salvo localmente em ARS-MAGICA-ESTADO-ATUAL.md e HANDOFF-ANTIGRAVITY.md.

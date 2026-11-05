# Estabiliza??o da Box of Illusions e item na m?o na CR21

## Problemas corrigidos

1. **Preso como galinha / mob em servidores multiplayer**:
   - Na implementa??o anterior de `RenderItemBoxOfIllusions.setCustomRenderer()`, todos os ramos davam `return;` antes de atualizar o `cooldown` e antes de enviar o pacote `SYNCMAPTOSERVER`.
   - Ao mesmo tempo, `AMClientEventHandler.onEntityJoinWorldClient` retransmitia o `playerModelMap` inteiro de outros clientes sempre que qualquer jogador entrava no alcance visual, e `AMPacketProcessorClient.handleSyncMap` apenas inseria chaves (`put`) sem remover entradas limpas nem proteger o estado do pr?prio jogador local.
   - Isso criava um efeito de reinfec??o em servidores: mesmo revertendo localmente, esvaziando o invent?rio, descartando a caixa, descarregando chunks ou reconectando, outro cliente retransmitia o estado antigo (`"chicken"`) poucos segundos depois.

2. **Item na m?o invis?vel durante a transforma??o**:
   - Nas transforma??es em criaturas (`chicken`, `cow`, `spider`, `creeper`, `snowman`, `ender`), `ArmorEventHandler.onPlayerRenderPre` cancelava `RenderPlayerEvent.Pre` e delegava a renderiza??o ao `RendererLivingEntity` do mob, cujo `renderEquippedItems` n?o desenha o item segurado pelo jogador.
   - Nas transforma??es `maid0`..`maid7`, `ModelPlayerSpecial.render()` incrementava `bipedRightArm.rotationPointX += 1.0F` a cada quadro no modelo est?tico compartilhado, deslocando o bra?o direito e o item segurado para fora da tela.

## Solu??o implementada na CR21

- **Autoridade local e sincroniza??o limpa (`ItemBoxOfIllusions`, `RenderItemBoxOfIllusions`, `AMClientEventHandler`, `AMPacketProcessorClient`)**:
  - A altern?ncia ocorre imediatamente no clique direito sobre a `Box of Illusions`, enquanto a anima??o de rota??o roda apenas para o portador local.
  - O cliente local mant?m `localPlayerModel` como fonte de verdade para o pr?prio jogador: pacotes `SYNCMAPTOCLIENTS` remotos nunca sobrescrevem o estado humano do jogador local e disparam uma limpeza corretiva (`""`) quando outro cliente envia um estado obsoleto.
  - Ao reverter para humano, o cliente remove a entrada local e sincroniza explicitamente a limpeza com o servidor e os demais clientes.
  - A sele??o de catalisador verifica primeiro a hotbar (`0..8`) e depois o restante do invent?rio principal.
- **Renderiza??o do item na m?o (`ArmorEventHandler`, `ModelPlayerSpecial`, `RenderPlayerSpecial`)**:
  - `ArmorEventHandler.renderIllusionHeldItem` desenha o `ItemStack` segurado pelo jogador (blocos 3D, arcos, itens `isFull3D` e itens 2D em m?ltiplas passadas de cor) posicionado e rotacionado junto ao corpo e ao progresso de ataque de cada criatura.
  - `ModelPlayerSpecial` mant?m `bipedRightArm.rotationPointX = -5.0F` est?vel em `setRotationAngles`, preservando o bra?o direito e o item na m?o nas skins `maid`.

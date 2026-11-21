# Encolhimento com Futura3D — CR44

O nome e a caixa do jogador diminuíam, mas o corpo do modelo continuava grande. O Futura3D substitui a chamada ao renderizador vanilla; por isso o hook de escala do Ars em RendererLivingEntity não alcançava a geometria substituta.

A correção pertence somente ao AM2.5: um adaptador opcional aplica o percentual de encolhimento à matriz final `ThirdPersonRenderer.placement`. A assinatura e os locais foram inspecionados nos dois binários instalados, `futura3d-90c7159.jar` e `futura3d-0.1.0-dev-1c242cb.jar`. É método estático, jogador no local 0 e matriz no local 10. O hook roda uma vez em cada retorno normal; uma segunda passagem do transformador não o duplica.

A matriz é multiplicada pela mesma escala do renderizador vanilla, de 1 a 0,25, mantendo a coluna de translação. Corpo, armadura e localizadores de itens partem dessa transformação. Não se altera o tamanho artístico relativo do modelo, as animações, a física do encolhimento ou a câmera por causa desta correção visual. O método continua reconstruindo a matriz a cada desenho, evitando escala acumulativa.

O transformador só é chamado para a classe opcional exata. Nenhuma classe Java do Futura3D é importada ou distribuída pelo Ars; o JAR do outro mod não é alterado e sua ausência não o torna obrigatório. A lógica nova é visual, sem mensagens de rede ou varreduras de entidades por tick. Na mesma sessão, cada cliente precisa do AM2.5 corrigido para enxergar a escala do corpo substituto.

Testes binários verificam assinatura real, local da matriz, cobertura dos retornos, validade ASM e idempotência nas duas versões. Testes de matriz verificam transição, escala final, restauração e preservação da âncora. A rodada visual completa e o checksum estão no relatório local da tarefa, e devem ser conferidos antes de anunciar a conclusão em jogo.

Mantido CR44; Playground e Segundo Cérebro não fazem parte desta alteração.

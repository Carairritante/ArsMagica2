# Teste do menu com servidor integrado

Mod temporário de teste para um cliente Forge 1.7.10. Não acompanha o AM2.5.
Ele cria um mundo descartável, fornece uma ferramenta, clica nos botões reais
por meio do GuiScreen, aguarda o processamento de rede e a atualização do item,
e verifica seleção, páginas, rotação, limpeza, marcação e montagem.

Compile para Java 8 com as APIs Forge/Minecraft e empacote somente o pacote
clientarchitectprobe. Execute com AM2.5 em uma instalação de cliente separada,
sem saves de uso normal. O teste escreve client-architect-result.txt, guarda
capturas em screenshots e encerra o processo. Não instale em seu modpack normal.

A CR6 falha na primeira troca, permanecendo na corrupção. A CR7 passou com
os mods da instância de teste, sem acesso a saves existentes.

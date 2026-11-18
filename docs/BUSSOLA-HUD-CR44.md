# HUD da Bússola Astral — desenho próprio — 17/11/2026

Alteração visual exclusiva de `AstralCompassHudRenderer.java`. A HUD não usa mais
o selo/runa do giz nem seu painel: possui mostrador circular, vidro escuro,
graduações, rosa dos ventos e uma placa lateral com as informações.

N/E/S/W giram conforme a orientação do personagem. A ponta ciano da agulha e a
marca externa indicam o alvo; o contrapeso dourado aponta para o lado oposto.
O pequeno índice fixo indica a frente do personagem. O cálculo da direção segue
a convenção existente do Minecraft (yaw zero para sul/+Z). Não aponta o alvo de
outra dimensão como se estivesse na dimensão atual.

A chegada conserva a indicação verde. Ao estar exatamente sobre o ponto, a
agulha dá lugar a uma confirmação central; alvos em outra dimensão recebem um
símbolo próprio. Os dados de nome, mod, dimensão, distância, coordenadas e origem
ou estado continuam presentes. A última informação pode ocupar duas linhas.

Os limites continuam 264×96, preservando o posicionamento relativo e as dimensões
usadas na prévia/arraste. Configurações e tela de ajustes não foram alteradas.
A transparência continua controlando o fundo; textos, graduação e agulha
continuam legíveis. Não há texturas novas, partículas ou lógica no servidor.

As tabelas trigonométricas do círculo são criadas uma vez; o estado OpenGL é
preservado/restaurado no escopo de desenho. Sem benchmark ou teste em jogo nesta
entrega: apenas compilação assemble, conforme o fluxo de teste manual do usuário.
O nome CR44 foi mantido; conferir o checksum do JAR instalado.

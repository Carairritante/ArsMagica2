# Probes da compatibilidade opcional de rituais

Fontes originais de teste, fora do JAR do AM2.5. Compile para Java 8 com as APIs
Forge/Minecraft 1.7.10 e empacote cada pacote de probe separadamente.

PGArchitectProbe é um mod de servidor dedicado descartável. A propriedade
pgprobe.mode aceita full, minimal ou am2only. Ele verifica catálogo, variantes,
conteúdo dos itens, recipientes, preservação, proteção e persistência. Encerra o
servidor ao escrever pg-architect-probe-result.txt.

PGClientArchitectProbe é um mod de cliente descartável para o cenário completo.
Ele usa o menu real, o canal de rede e o servidor integrado, troca seleções e
orientações e monta o desenho de Eldritch Mobilization. Guarda capturas em
screenshots, escreve client-architect-result.txt e encerra o processo.

Use instalações separadas, sem saves de uso normal. Os probes criam e removem
blocos e itens nas áreas de teste; não devem ser instalados no modpack de jogo.
Veja docs/COMPATIBILIDADE-RITUAIS-CR8.md para resultados e limites.

# Probe integrado do Giz do Arquiteto Arcano

Fonte de um mod temporário de verificação para Forge 1.7.10. Ele usa reflexão
para testar o JAR reobfuscado e não faz parte do artefato AM2.5.

Compile para Java 8 com as APIs do Forge e do Minecraft 1.7.10; coloque somente
as classes do pacote `architectprobe` no JAR temporário. Execute em um servidor
dedicado descartável com o AM2.5 CR6. O teste usa coordenadas próprias, gera
blocos e reagentes, escreve `architect-probe-result.txt` e encerra o servidor.
Não instale esse probe em um mundo de jogo normal.

O cenário cobre 30 rituais em quatro orientações, montagem exclusiva do criativo,
conferência sem consumo, runas de Recall, colisões, apoio, limites, proteção Forge,
fila de ações e persistência. A conferência visual do cliente é separada.

# Teste gráfico descartável das prévias do Compêndio

Compile `MobPreviewProbe.java` para Java 8 com Forge, Minecraft SRG e LWJGL 2 no
classpath. Empacote somente `mobpreviewprobe/*.class` em um JAR de teste.
Execute em um cliente Forge 1.7.10 separado com AM2.5 e suas dependências.
O Playground e outros mods são opcionais. Não instale o probe no jogo normal.

O probe cria um mundo plano descartável e abre as páginas reais de seis
criaturas: Mana Creeper, Earth Elemental, Mana Elemental, Water Elemental, Dryad
e Flicker. Gera screenshots, volta para uma página de texto, fecha o livro e
encerra o cliente. O resultado fica em `mob-preview-result.txt`.

Na CR11, exige restauração do lightmap, culling, iluminação, matrizes de modelo
e textura, profundidade da pilha e cache de coordenadas do lightmap.
O resultado esperado é `MOB_PREVIEW_STATE_AND_NAVIGATION=PASS` e saída zero.

Para reproduzir a falha na CR10, adicione `-Dpreview.baseline=true` aos argumentos
da JVM. Nesse modo, exige o vazamento de lightmap no creeper e no elemental de
terra e termina com `MOB_PREVIEW_LEAK_REPRODUCED=PASS`. Não use esse modo para
validar uma versão corrigida.

Os nomes reflexivos usam o ambiente SRG do Forge 1.7.10. As capturas precisam
ser conferidas visualmente; o teste não valida shaders externos.

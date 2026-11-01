# Compêndio: iluminação das prévias de criaturas — CR11

Ao abrir certas criaturas, como Mana Creeper e Earth Elemental, o desenho do
modelo deixava a textura de iluminação do mundo (lightmap) habilitada. O livro
era desenhado em seguida usando esse estado e ficava escuro ou tingido. O mesmo
vazamento foi reproduzido com Mana Elemental, Water Elemental e Dryad na CR10.

A prévia agora preserva e restaura o estado gráfico, as matrizes de modelo e de
textura, a unidade de textura ativa e o cache de coordenadas do lightmap. A
iluminação usada pelo modelo fica limitada à sua renderização. A restauração
usa `finally`, incluindo a transformação externa da página.

Os modelos continuam visíveis e podem ser girados. Esta correção é somente no
AM2.5; não altera o JAR do Playground, traduções, progresso ou mecânicas.

## Validação

- Build Java 8 e os quatro verificadores de manutenção concluídos com sucesso.
- CR10: teste gráfico reproduziu o lightmap desligado antes da prévia e ligado
  depois nos cinco modelos citados.
- CR11: cliente gráfico mínimo e cliente com o modpack de teste passaram.
- No modpack completo, testados Mana Creeper, Earth Elemental, Mana Elemental,
  Water Elemental, Dryad e Flicker; nenhum deixou iluminação ou culling ativos
  na interface. Matrizes, profundidade da pilha e cache do lightmap preservados.
- Conferidos retorno para página de texto e retorno ao mundo; capturas do
  creeper e do elemental de terra mostram o papel com a iluminação normal.
- O teste usa cliente Forge/LWJGL padrão, sem validar todas as combinações de
  shaders ou modificações externas de renderização.

O teste descartável está em `tools/integration/compendium-mob-preview`.
Investigação, implementação e testes realizados com participação substancial
do OpenAI Codex, preservando a autoria original do mod.

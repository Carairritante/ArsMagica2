# Bússola Astral e servidor — CR43

A CR43 melhora a busca de estruturas sem alterar o Playground, o addon de
tecnologia mágica ou os JARs dos mods integrados. Preserva os modelos, texturas
e demais funcionalidades da CR41.

## Uso e progressão

O Ritual de Adivinhação Astral e a receita da bússola permanecem iguais.
É necessário nível mágico 50. Uma localização bem-sucedida custa 20.000 de mana;
buscas sem resultado ou em dimensão incorreta não cobram. O servidor verifica
os recursos novamente antes de aplicar o resultado.

Abra a bússola com o botão direito, escolha um alvo da dimensão atual e siga
a indicação. A consulta é dividida entre ticks. Trocar de item, morrer, sair
ou mudar de dimensão cancela uma busca pendente.

## Integrações opcionais

| Origem | Suporte | Disponibilidade |
| --- | --- | --- |
| Vanilla e AM2 | Catálogo original, floresta Witchwood, estruturas persistidas e predição vanilla controlada | Depende da dimensão e do gerador ativo. |
| OreSpawn Remastered | 46 construtores, incluindo altares do King/Queen, castelos, torres e ninhos | Índice de construções concluídas após a atualização, com alteração de bloco confirmada. |
| Electroblob's Wizardry 1.1.6 | Torres amigáveis ou malignas | Registro após conclusão da torre e spawn confirmado. |
| ChromatiCraft V33a | Cavern, Burrow, Ocean, Desert, Snowstruct e Biomefrag | Posições reais do cache nativo, somente no estado SUCCESS e na dimensão correta. |
| Thaumcraft 4 | Mound, Eldritch Ring e Hilltop Stones | Índice após retorno de geração bem-sucedida. |
| Twilight Forest | Features na própria dimensão e dados de estruturas compatíveis | Adaptador opcional; cobertura de jogo nessa dimensão ainda pendente. |
| Outros mods | MapGenStructure do provedor e MapGenStructureData carregado | Apenas formatos compatíveis; não equivale a suporte universal. |

**Construções antigas do OreSpawn, Wizardry e Thaumcraft não possuem recuperação
automática universal.** O índice começa a registrar as gerações reconhecidas
depois da atualização. Estruturas compatíveis já presentes nos dados de mundo
podem continuar sendo localizadas pelo caminho genérico.

As integrações residem no AM2.5 e são opcionais. Hooks de geração são aplicados
em memória pelo próprio AM2, com assinaturas conferidas nos JARs do pack; os
arquivos dos outros mods permanecem intactos.

## Correções de servidor

- Pedidos estritamente validados, fila limitada e execução das operações de
  mundo, inventário e mana no tick do servidor.
- Orçamento global de 512 amostras por tick e prazo cooperativo de 2 ms.
  Uma leitura inicial de dados ou carregamento de classes pode ultrapassá-lo;
  isso não representa garantia rígida de tempo de tick.
- Limites de pedidos, buscas por jogador, catálogo e intervalo entre consultas.
- Cancelamento e limpeza de buscas na saída e no descarregamento do mundo.
- Predições sem alterar o gerador ativo ou o RNG do mundo, e sem chamar
  geradores arbitrários de outros mods.
- Suporte a geradores guardados em listas, incluindo mundos planos.
- Centro da bounding box dos dados persistidos e descarte de vilas inválidas.
- Remoção da anotação de cliente de um campo inicializado por classe comum.
- Índice de até 8.192 locais por mundo/dimensão, sem guardar jogadores.

O teste dedicado também encontrou `EntityAILightningRod.setMutexBits(I)V`
com nome incorreto no JAR de produção. A CR43 inclui as APIs de compilação no
classpath do reofuscador e verifica dez classes de IA. A AnimationAPI continua
externa e não é empacotada no AM2.5.

## Validação registrada

- 33 testes locais passaram, incluindo os JARs reais das integrações.
- Nove verificadores do JAR passaram.
- Servidor dedicado descartável do Forbidden Series com 196 JARs externos:
  resultado PASS e saída normal. Nenhum mundo ou conta do jogador foi copiado.
- Dez guardiões inicializados, catálogo com 64 alvos no Overworld e ausência
  do erro de métodos herdados dos guardiões no resultado final.
- Busca de estrutura persistida e predição de vila preservaram o RNG e a
  quantidade de chunks carregados.
- Construção real de altar do King e torre Wizardry, seguida de indexação.
- Dimensão, falha, mudança de recursos, cobrança exata e pedidos malformados
  verificados por chamadas do lado servidor.
- Os 1.679 arquivos PNG/BBMODEL da base permaneceram idênticos.

Não houve cliente remoto conectado, validação de HUD ou teste com muitos
jogadores. Não foram visitadas todas as estruturas de todos os adaptadores.
O teste de dados persistidos inclui uma estrutura sintética para conferir o
cálculo do centro; os testes do King e da torre usam construções reais.

## Compilação e reprodução

`./gradlew build` executa os testes e verificadores no JAR jogável reofuscado.
O teste que inspeciona os mods do pack exige os arquivos locais:

```text
./gradlew build -PastralPackMods=/caminho/do/pack/mods
```

Sem esse parâmetro, somente esse teste opcional é ignorado; os demais continuam
no CI. Os mods de terceiros e o modpack não são distribuídos neste fork.

`tools/integration/AstralCompassProbe.java` contém a instrumentação do dedicado.
O lançador portátil `run_astral_compass_probe.py --help` cria outra pasta,
copia mods/configuração, aceita exclusões e usa um mundo artificial. Prepare
uma cópia compatível com servidor e exclua os mods exclusivos de cliente.
A instrumentação encerra o servidor após o teste e não faz parte do JAR jogável.

Os artefatos do Actions são o JAR e o arquivo SHA256SUMS da própria execução.
O checksum do build local pode diferir do checksum do build do CI; confira cada
download com o SHA256SUMS correspondente. Use a mesma versão no cliente e servidor.

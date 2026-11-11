# Verificadores do JAR de manutenção

Ferramentas escritas nas sessões de manutenção com participação substancial do
OpenAI Codex. Inspecionam a estrutura do JAR, sem iniciar Minecraft ou modificar
o arquivo de entrada.

- `VerifyLightningJar.java`: pontos de chamada dos limites, classes auxiliares,
  intervalo de 30 ticks da Zone, três conjurações em 200 ticks e versão Java 8.
- `VerifySolarLunarTierJar.java`: metadados de contagem e chamadas do escalonamento
  por uma, duas e três cópias de Solar/Lunar.
- `VerifySilverPointsJar.java`: escrita dos quatro saldos nativos, leitura do quarto
  saldo e ausência de contador, tooltip e regra de compra próprios do Playground
  no núcleo. Confere também a API opcional World Fusion mantida por compatibilidade.
- `VerifyRitualArchitectJar.java`: processador de ações registrado antes dos proxies,
  incluindo o servidor integrado, e processamento pelo evento de tick do Forge.
- `VerifyServerBoundaryJar.java`: ausência de tipos gráficos no handler comum de
  armaduras e correspondência entre o nome do JAR, a anotação Forge e o campo
  `maintenanceVersion` de `mcmod.info`. Desde a CR26, o campo estático `version`
  conserva 1.6.7 para preloaders legados; o Forge e a tela de mods usam a versão CR.

Desde a CR25, `./gradlew build` executa os verificadores no JAR reofuscado jogável,
em vez de usar o artefato `-dev.jar`. A regressão de execução real para conjurador
ausente fica em `tools/integration/ServerEffectsProbe.java`; ela deve ser instalada
somente em diretório de teste descartável, nunca em um mundo do jogador.

A regressão visual `tools/integration/BookAnimationProbe.java` captura livros
comuns, do Guardião, vazios e de outro jogador. O lançador
`tools/integration/run_book_animation_probe.py --help` informa os caminhos locais
necessários. Ele cria uma instância descartável, sem copiar saves ou contas, e
encerra o jogo ao concluir. Os arquivos dessa instrumentação não entram no mod.

## Uso

São necessários JDK 17 para compilar, Java 8 para reproduzir a execução registrada
e as bibliotecas ASM 9.9 (`asm` e `asm-tree`), não incluídas neste diretório.
Exemplo para Windows, substituindo os caminhos e executando neste diretório:

```powershell
javac --release 8 -cp "C:\dependencias\asm-9.9.jar;C:\dependencias\asm-tree-9.9.jar" -d build VerifyLightningJar.java VerifySolarLunarTierJar.java
java -cp "build;C:\dependencias\asm-9.9.jar;C:\dependencias\asm-tree-9.9.jar" VerifyLightningJar "C:\mods\AM2.5-LTS-1.6.11-LE-CR2.jar"
java -cp "build;C:\dependencias\asm-9.9.jar;C:\dependencias\asm-tree-9.9.jar" VerifySolarLunarTierJar "C:\mods\AM2.5-LTS-1.6.11-LE-CR2.jar"
```

Uma saída de sucesso indica que as verificações estruturais passaram. Não equivale
a um teste de desempenho ou a uma validação completa dentro do jogo. Veja o
[registro de validação](../../docs/VALIDACAO-CR2.md).


A regressão gráfica também verifica as onze paletas, a abertura do container da
Mesa de Inscrição e o menu de teclas com categorias de tradução igual.
`run_vfx_safety_probe.py --freecam-jar <jar>` acrescenta o ensaio opcional com
Zergatul Freecam 2.3.1; `--playground-jar <jar>` troca apenas o addon na cópia
descartável. Não execute o teste numa instância original. A classe de diagnóstico
nunca deve ser incluída no JAR distribuído. A regressão dedicada também abre o
container da Mesa de Inscrição sem bibliotecas gráficas.

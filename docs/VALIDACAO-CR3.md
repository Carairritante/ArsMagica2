# Validação da versão CR3

Registro: 31 de outubro de 2026.

- Compilação completa e reobfuscação com Gradle 8.8, RetroFuturaGradle 1.4.9,
  JDK 17 para o Gradle e JDK 8 para o mod.
- Verificadores anteriores de Lightning/Zone/limites e Solar/Lunar passaram.
- Novo verificador confirmou a leitura Silver no pacote e o contador no Oculus.
- Teste de integração em servidor Forge 1.7.10 descartável: quatro saldos de pontos
  preservados no ciclo codificar/decodificar do pacote real de SkillData.
- Os quatro saldos também foram preservados no ciclo salvar/carregar NBT.
- Uma habilidade Silver disponível foi aprendida, consumindo exatamente um ponto;
  os ciclos de pacote e salvamento foram repetidos depois da compra.

O contador Silver aparece abaixo do vermelho na mesma caixa de pontos do Oculus.
Não houve inspeção visual completa da interface nem teste de todas as magias.
Os testes da CR2 continuam documentados separadamente; seu hash não identifica
o JAR compilado da CR3. Cada build do Actions publica seu próprio SHA256SUMS.

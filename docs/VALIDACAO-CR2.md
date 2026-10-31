# Validação da versão CR2

Registro: 31 de outubro de 2026.

## Artefato publicado

Arquivo: `AM2.5-LTS-1.6.11-LE-CR2.jar`.

SHA-256:

```text
919efdfb0ec0c52115feaa8a0296d4839e2feaed2cbbd80cd9664623edcdec13
```

Esse hash corresponde ao JAR preparado no trabalho anterior e ao arquivo ativo
conferido na instância de teste. A versão que bloqueava Bless foi descartada pelo
mantenedor e não é o artefato desta release.

O arquivo foi produzido por patches incrementais sobre um JAR existente. As fontes
da manutenção foram recuperadas e publicadas sobre a base LE 1.6.11; não houve uma
nova compilação completa/reobfuscação do projeto pelo Gradle nesta publicação.
Portanto, não se afirma uma reconstrução byte a byte do JAR a partir deste checkout.

## Verificações repetidas antes da publicação

- Compilação dos auxiliares independentes e dos dois verificadores com JDK 17,
  `--release 8`.
- Verificação estrutural do JAR com os verificadores publicados, executados em Java 8.
- Conferência do hash do JAR e da presença dos auxiliares necessários.
- Confirmação de que o auxiliar de bloqueio de Bless está ausente do artefato.
- Revisão dos arquivos selecionados para publicação, sem caminhos pessoais ou
  credenciais acrescentados.

Saídas observadas:

```text
Verified: per-block limiter, 3-pulse base Zone interval, player cast limiter, and Java 8 helper classes.
Verified: repeated Solar/Lunar modifiers use capped 1/3, 2/3, 3/3 tiers.
```

Os verificadores usam inspeção de bytecode: conferem os pontos de chamada,
constantes, auxiliares e compatibilidade Java 8 descritos em seu código. Eles não
simulam todas as combinações de magias, desempenho, rede ou uso multiplayer.

## Limites conhecidos

- Os testes no jogo relatados durante a manutenção não cobrem todas as combinações.
- Uma área grande ainda pode exigir bastante processamento, mesmo com esses limites.
- Tempos em segundos supõem 20 TPS; com lag, 200 ticks duram mais que 10 segundos reais.
- O identificador e a versão interna herdados podem não distinguir os JARs durante
  a conexão do Forge. Compare o hash e use o mesmo arquivo nos dois lados.
- Um aviso de compatibilidade Solar/Lunar do binário anterior pode exibir acentos
  incorretos; isso não foi tratado como uma correção nova nesta publicação.
- A compilação completa do projeto legado e uma revisão independente permanecem
  pendentes. A release está marcada como versão de teste.

As instruções dos verificadores estão em [tools/verification](../tools/verification/README.md).

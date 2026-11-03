# Validação local e publicação — CR14 a CR18

As atualizações estão em commits separados, acrescentados ao histórico da branch
`correcoes` na ordem em que foram desenvolvidas:

| Versão | Mudança | Commit |
| --- | --- | --- |
| CR14 | 41 modelos nativos e busca parcial em inglês/idioma do jogo | [aa0af08](https://github.com/Carairritante/ArsMagica2/commit/aa0af089ca1a20ba5fae8db041fade87065a942a) |
| CR15 | HUD azul com runas, progresso, materiais e reagentes | [f3d1a66](https://github.com/Carairritante/ArsMagica2/commit/f3d1a663545c0c99cce93957975b9495269849ae) |
| CR16 | Menu no mesmo estilo e navegação ao redimensionar | [eaec4a4](https://github.com/Carairritante/ArsMagica2/commit/eaec4a48bee98d7b4c53d22e8261586a373fd28c) |
| CR17 | HUD no canto superior esquerdo e fundo mais transparente | [67df9e0](https://github.com/Carairritante/ArsMagica2/commit/67df9e0378089b0c7245a83956ee1b6a0bbddedf) |
| CR18 | Configuração local de posição, tamanho e transparência | [466ad91](https://github.com/Carairritante/ArsMagica2/commit/466ad915ae608b3be947a62c7af7debd8e7e01f6) |

## Verificações concluídas

- Build Gradle da CR18 e verificadores do JAR concluídos com sucesso.
- Tradução: 535 entradas e 474 descrições do Compêndio, preservando IDs,
  receitas e marcações. Os novos controles da HUD têm textos PT-BR e inglês.
- Fontes alterados de cada etapa compilados com Java 8 antes dos commits.
- CR18 executada em cópia descartável completa do Forbidden Series, com
  Forge 10.13.4.1614, Java 8u504 e 214 JARs do pack, além do auxiliar de teste.
- Abertura da configuração pelo botão do menu, arraste da HUD, mudança de
  tamanho e transparência, recarga das preferências salvas e restauração.
- Limites de tamanho e transparência e adaptação a janelas de 1024 × 768 e
  640 × 480, com escala de interface 2.
- Alterações da HUD permanecem locais: o NBT do giz no servidor foi preservado,
  e a montagem automática continuou bloqueada no survival.
- Mods, configurações, opções e metadados dos saves da instância original
  foram conferidos antes e depois; a origem permaneceu inalterada.

O Playground instalado nessa cópia do Forbidden era o Beta 0.12.1 original do
pack, SHA-256 `58930be3daa1e527e378850c7ac7b036c9d51d3b1dfade840961fd26fa02d83b`.
Nenhum arquivo do Playground foi alterado pelas CR14–CR18. O JAR corrigido
`independent-silver` continua separado, no arquivo privado existente.

A validação atual segue a preferência do mantenedor: nossa versão do AM2.5 em
cópia do Forbidden Series. As matrizes anteriores ficam como registro histórico;
não há afirmação de compatibilidade universal nem de novos testes com outros forks.

## JARs locais validados

| Arquivo | SHA-256 |
| --- | --- |
| AM2.5-LTS-1.6.11-LE-CR14.jar | `2f1a7eeffa0f4c1ef571b4fc1d5051a73ed5091aa26bdcb03836cd814a527ee6` |
| AM2.5-LTS-1.6.11-LE-CR15.jar | `682e3021eceb4f15384451883e9c18134dc34c4fa0684024826511f2f0647666` |
| AM2.5-LTS-1.6.11-LE-CR16.jar | `f95d0cb400c5cf1989db64662d2d386b93f393153aee7163b187ce22ea2e81ce` |
| AM2.5-LTS-1.6.11-LE-CR17.jar | `ee7db2c2a9070cb3a6b58e6b41c60b7d1738f55e50d93583eefa617ee00e123b` |
| AM2.5-LTS-1.6.11-LE-CR18.jar | `cd9cb3de0365a58b1639e83d2523415cfe7e24249f00bac97ef0524ccc9a9cad` |

Esses hashes identificam os arquivos locais testados. Cada execução do
[Actions](https://github.com/Carairritante/ArsMagica2/actions) disponibiliza o
próprio JAR e `SHA256SUMS`, separadamente, sem ZIP externo, por 14 dias.
O checksum daquela execução deve ser usado para o arquivo baixado dela.
Build local e resultado do Actions são verificações distintas.

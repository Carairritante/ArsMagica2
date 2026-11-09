# CR25: efeitos de magia sem conjurador

## Problema e correção

Na CR24, a associação de efeitos de área e os inicializadores das fusões de
Explosion, Lightning e Storm chamavam `DummyEntityPlayer.fromEntityLiving(caster)`
antes de proteger o conjurador nulo. O conversor acessava os campos da entidade,
causando `NullPointerException` durante a criação do efeito.

A CR25 centraliza esses inicializadores em `SetCasterAndStack`. Na ausência do
conjurador, usa `EntityDummyCaster` convertido no mesmo contexto neutro já usado
por Zone/Wall. Efeitos sem dono recebem ID -1; um conjurador existente continua
associado ao efeito, sem substituição de identidade. Não há alteração dos raios,
danos, duração ou limites das magias.

## Identificação e verificações automáticas

O JAR, a anotação `@Mod` e `mcmod.info` informam `1.6.11-LE-CR25`. A versão anterior
exibia `1.6.7` internamente, mesmo quando o arquivo se chamava CR24.

O build passa a executar os verificadores de manutenção no JAR reofuscado
distribuído, em vez do `-dev.jar`. Uma nova verificação impede referências a
classes gráficas do Minecraft/Forge/LWJGL no handler comum de armaduras e confere
a identificação do arquivo. Isso protege a separação cliente/servidor corrigida
nas revisões anteriores da CR24. O Actions continua oferecendo JAR e SHA-256
separados, sem ZIP externo.

## Validação local

- Compilação Java 8 e seis verificadores do JAR reofuscado concluídos com sucesso.
- Servidor dedicado Forge 10.13.4.1614, Minecraft 1.7.10, com AM2 e suas dependências
  AnimationAPI/CoFH copiadas do modpack, sem classes do cliente no classpath.
- No JAR da CR24, os quatro casos reproduzem o NPE em `DummyEntityPlayer`.
- Na CR25, os quatro casos passam; a identidade de um conjurador existente é
  preservada, e o contexto neutro não possui privilégios de criativo ou comandos.
- Explosion e Lightning completam sua execução, a mensagem de morte funciona e
  o Storm avança nos ticks do mundo e expira dentro da duração esperada.
- Cópia do Forbidden instalado, incluindo Playground CR2: inicialização, criação
  de mundo descartável e repetição dos quatro casos no servidor integrado
  concluídas com sucesso, incluindo execução, mensagem de morte e expiração.

Os testes usam diretórios novos, mundos descartáveis e identidade de teste.
Saves, contas e instância original do jogador não são alterados. O mod de teste
não integra o JAR distribuído. O executor reproduzível está em
`tools/integration/run_server_effects_probe.py`, com caminhos locais recebidos por
argumentos; execute `--help` para as opções.

## Limites

A reprodução confirma um defeito real da CR24 e sua correção. Não identifica,
por si só, a causa de qualquer incidente remoto sem os respectivos registros.
O servidor dedicado usado nesta regressão contém o núcleo e suas dependências;
não representa um teste de carga de todo o modpack ou de múltiplos jogadores.
O CI estrutural não substitui esses testes de execução.

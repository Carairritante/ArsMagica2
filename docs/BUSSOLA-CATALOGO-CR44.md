# Catálogo da Bússola Astral no singleplayer — CR44

## Causa

`AstralCompassService` era registrado em
`CommonProxy.InitializeAndRegisterHandlers`. O proxy físico do cliente substitui
esse método. Portanto, o servidor integrado recebia pedidos na fila, mas não tinha
o consumidor de ticks para processá-los. O catálogo permanecia nas oito entradas
básicas do cliente, inclusive após selecionar a aba Mods.

A reprodução anterior à correção, em cópia do Forbidden Series, registrou
`SERVER_CATALOG=64`, `CLIENT_CATALOG=8` e `FILTERED=8` na interface real.

## Correção

O registro da mesma instância nos barramentos FML e Forge foi movido para
`CommonProxy.init`, executado tanto pelo dedicado quanto por `ClientProxy.init`
através de `super.init`. O registro antigo foi removido para evitar duplicações.
O protocolo de rede, a fila, os limites, a validação de nível/mana/dimensão e os
adaptadores opcionais foram preservados. Não há dependência nova do Playground.

O nome continua `AM2.5-LTS-1.6.11-LE-CR44.jar`, conforme orientação do usuário.
Identifique este build pelo SHA-256 informado junto ao arquivo entregue.

## Validação reproduzível

- Resultado local de 17/11/2026: 35 testes sem falhas e nove verificadores
  aprovados. No cliente do Forbidden, `CLIENT_CATALOG=64`, `MODS_TAB=56`,
  OreSpawn/ChromatiCraft/Wizardry presentes, busca enviada pela GUI e alvo
  confirmado no servidor integrado. Teste encerrado com código zero.
- Dedicado descartável com 196 mods externos: aprovado, incluindo dez
  guardiões, catálogo, consultas OreSpawn/Wizardry, mana, dimensão e pedidos
  inválidos. Não houve cliente remoto conectado nem teste de carga.
- `AstralCompassLifecycleTest` verifica o caminho compartilhado com o integrado.
- A compilação executa os testes locais e os nove verificadores do JAR final.
- `tools/integration/run_compass_gui_probe.py` inicia uma cópia descartável do
  modpack, sem saves ou contas. Abre a bússola pelo uso normal do item, confirma
  o catálogo recebido, clica na aba Mods, verifica OreSpawn, ChromatiCraft e
  Wizardry e envia uma busca pela interface. O alvo de teste é uma coordenada
  artificial no índice do mundo descartável; isso verifica o fluxo completo da
  interface/fila/resposta, não a geração de uma torre real nessa coordenada.
- Os testes de geração real e consulta no dedicado são separados desse teste
  de interface. Uma lista exibida não demonstra cobertura universal de estruturas.

Exemplo, informando os caminhos locais disponíveis:

```powershell
python tools/integration/run_compass_gui_probe.py --output <pasta-nova> --jar <jar> --prism <PrismLauncher> --instance <instancia/minecraft> --java <Java8/bin/java.exe> --javac <JDK17/bin/javac.exe>
```

O relatório é `compass-gui-result.txt`; o sucesso exige
`COMPASS_GUI_REGRESSION=PASS`. A captura fica em `screenshots/compass-mods.png`.

## Limites conhecidos

A correção não cria uma API universal de estruturas para o Minecraft 1.7.10.
Os adaptadores e índices existentes continuam com os mesmos limites: construções
antigas de geradores sem dados persistidos podem não estar indexadas. A rejeição
de alturas negativas no índice, identificada na auditoria anterior, também é uma
pendência separada desta correção do catálogo.

# Mana Shield — CR44

A proteção usa a mana do alvo e mantém duração/ícone, sem VFX próprio. O estado autoritativo fica em ForgeData/AM2ManaShieldWard; a poção é espelho. A ativação pelo componente é imediata. Aplicações novas de poção por addons são reconhecidas; dados de poções antigas são migrados sem ressuscitar uma proteção já encerrada.

| Nível exibido | Mana por dano efetivamente perdido |
|---|---:|
| I | 50 |
| II | 37,5 |
| III | 25 |
| IV | 17,5 |
| V ou maior | 12,5 |

A cobrança ocorre na alteração de vida, após armaduras e demais defesas. Uma reserva insuficiente absorve apenas a parcela pagável. Cada golpe numérico é cobrado; morte numérica não troca o preço do dano pela taxa mais barata de morte direta. O gasto usa float com débito representável, preservando frações e impedindo absorções gratuitas por arredondamento. Regeneração de mana não é alterada.

| Operação especial bloqueada | Custo maior entre |
|---|---|
| Efeito negativo / controle | 1% da mana máxima e 25 |
| Desarme coberto | 2% e 50 |
| Remoção de efeito coberta | 5% e 100 |
| Morte direta coberta | 25% e 250 |

Para estas operações, uma reserva insuficiente deixa a ação seguir. Dissipação não resistida encerra o estado independente. Escritas da mesma origem/categoria no mesmo tick são agrupadas; não existe recarga gratuita de dez ticks. Inventário não é restaurado depois de roubo: os desarmes específicos são bloqueados antes da cópia/remoção, evitando duplicação. Leite e dissipação voluntária encerram a proteção normalmente. Life Tap mantém sua troca de vida por mana; contra um alvo protegido, o reembolso é proporcional somente à vida realmente paga (sem reembolso se o escudo absorveu tudo); dano acidental do próprio usuário permanece protegido.

## Integração

Somente código do AM2.5 é distribuído. ManaShieldTransformer aplica guardas em memória sem renomear métodos vanilla. Suporta os nomes MCP/SRG e bytecode vanilla ainda ofuscado usando os mapas reais do Forge. Escopos de dano, entidade e pacotes são liberados também em saídas excepcionais. Os despachantes estáticos de ServerPlayerAPI preservam guardas quando o Galacticraft substitui implementações vanilla. Witchery EffectRegistry$12.disarm e Wizardry Telekinesis usam pontos verificados nos JARs locais. Essas integrações são opcionais, sem dependências Java rígidas dos três mods.

Mapas de estado usam chaves fracas; o cache de resistências é limitado a64 entradas por entidade. Logout permite a limpeza normal; a duração acompanha ticks online e não se renova ao reconstruir o ícone. Morte efetiva encerra o estado. Operações próprias reconhecidas por origem, incluindo pérolas do Ender vanilla, continuam disponíveis.

## Limites

Vida, poções, fogo, movimento e morte passam por guardas comuns; isso cobre vários mods, sem garantir imunidade universal. Escrita direta em DataWatcher/campos, remoção direta do mundo, maldições em sistemas privados e roubos fora dos caminhos auditados exigem adaptadores específicos. Remoção bruta do mapa de poções é detectada para o escudo; não é restauração universal dos demais buffs. Nenhuma alegação de cobertura total de Witchery, ChromatiCraft, OreSpawn ou Wizardry.

## Validação

O relatório de entrega registra o SHA-256 e os resultados do servidor dedicado/cliente correspondentes ao JAR, incluindo limitações da instrumentação. Os probes descartáveis não são distribuídos no mod.

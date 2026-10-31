# Validação CR4 — pontos Silver para talentos

31 de outubro de 2026. Compilação completa para Java 8 com os três verificadores
do repositório; cenário adicional em servidor Forge 1.7.10 descartável.

- Interface retorna CANNOT_LEARN para componente e modificador Silver não conhecidos.
- Pedidos de compra dessas habilidades enviados ao servidor não ensinam a parte
  nem descontam pontos, mesmo com saldo Silver suficiente.
- Pedido de compra de um talento Silver disponível ensina o talento e desconta
  exatamente um ponto.
- Desbloqueio direto por recompensa especial continua ensinando as partes
  Silver, sem consumir o saldo livre reservado aos talentos.
- Os quatro saldos continuam preservados na sincronização e no salvamento NBT.

O cenário usa os métodos reais de SkillData e pedidos serializados de compra.
Não equivale a uma sessão visual completa de cliente nem a um teste entre dois PCs.

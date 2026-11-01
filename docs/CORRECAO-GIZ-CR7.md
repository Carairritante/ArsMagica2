# Correção da seleção do Giz do Arquiteto Arcano

Na CR6, o menu podia marcar um ritual novo enquanto o mundo continuava mostrando
o desenho anterior. Seleção, rotação, limpeza e montagem enviavam ações ao servidor,
mas, no modo de um jogador, faltava o processador que retirava essas ações da fila.

A inicialização do servidor dedicado registrava esse processador em CommonProxy.
O cliente usa ClientProxy, que substitui essa inicialização; seu servidor integrado
não recebia o registro. A CR7 registra o processador uma única vez no caminho
comum de AMCore, antes da configuração dos proxies.

## Reprodução e validação

Um cliente descartável com AM2.5 CR6 reproduziu exatamente o sintoma: o clique
solicitou World Fusion (índice 0), mas servidor e cliente permaneceram no ritual
da corrupção (índice 2). O teste falhou por falta de sincronização, sem drenar
a fila manualmente.

O mesmo cenário foi executado com CR7 e a cópia completa dos mods de teste.
Foram usados o menu verdadeiro, a conexão do servidor integrado e a sincronização
normal do inventário. Passaram:

- Seleções World Fusion, Purification, ambas Corruptions, Hourglass, Recall e
  Otherworldly Ritual, incluindo navegação entre as páginas.
- Rotação e sincronização do novo estado no item do cliente e do servidor.
- Limpeza do centro, seguida de marcação pelo pacote vanilla de clique no chão.
- Montagem criativa pelo botão do menu; a estrutura não executa o ritual.
- Capturas da interface e da estrutura montada, conferidas visualmente.

O teste dedicado também passou novamente: 30 rituais em quatro orientações,
proteções de montagem, reagentes e persistência. Agora ele publica o evento real
ServerTickEvent no barramento do Forge em vez de chamar drain diretamente.
World Fusion e a progressão avançada existente passaram na regressão adicional.

O build e o Actions executam um novo verificador estrutural que recusa a
inicialização exclusiva do servidor dedicado encontrada na CR6.

## Atualização

Substitua o AM2.5 pelo CR7 no cliente e no servidor. Não é necessário fabricar
ou obter outro giz: a seleção e o centro das ferramentas existentes são mantidos.
O uso e a receita continuam no [guia do giz](GIZ-ARQUITETO-CR6.md).

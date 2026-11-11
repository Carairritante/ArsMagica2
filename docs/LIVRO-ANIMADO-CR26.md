# Livro animado — CR26 local

Atualização local de 09/11/2026, ainda sem commit ou publicação. Preserva a base
CR25 e sua correção de efeitos sem conjurador. Não modifica o Playground nem o
addon tecnomago e não acrescenta dependência deles.

## Causa e comportamento corrigido

O SpellScrollRenderer da versão experimental recebia o estado de utilização,
mas posicionava o livro comum abaixo da área visível em primeira pessoa.
O Livro Arcano do Guardião continuava registrado em outro renderizador, que
desenhava somente um modelo fechado. Livros vazios não tinham contagem de uso
e não abriam; a contagem local também não é uma fonte confiável para jogadores
remotos.

- O renderizador anterior do Guardião delega apenas as duas formas empunhadas ao
  livro animado. Seus modelos de inventário e item no chão são preservados.
- Abertura e fechamento interpolados por jogador, com páginas folheando e glifo
  da afinidade sobre as páginas. A tecla de uso configurada funciona no cliente
  local; jogadores remotos usam o indicador sincronizado de utilização.
- Transformações de primeira e terceira pessoa compensam as poses que o
  Minecraft/Forge 1.7.10 já aplicam antes de chamar o renderizador.
- Alterações são visuais: não iniciam magias, alteram custos ou prolongam o uso.
- O estado usa referências fracas e é limpo ao sair do mundo. As rotinas
  restauram a iluminação depois de desenhar o livro e o círculo aos pés.

## Identificação e preloaders legados

O Playground usado no teste-ars lê diretamente a versão estática de mcmod.info
antes de o Forge carregar os mods. Ele rejeitava o identificador completo CR26
e encerrava a inicialização. Não foi necessário modificar o addon.

O campo estático `version` conserva **1.6.7**, como na base original. O novo campo
`maintenanceVersion`, a anotação Forge e o nome do JAR registram
**1.6.11-LE-CR26**. Na pré-inicialização, o AM2 atualiza a versão exibida nos
metadados em memória; a tela de mods, o protocolo do Forge e os logs continuam
identificando a manutenção real. A regra é genérica, sem referência ao addon.

## Validação

- Compilação Java 8 e os seis verificadores do JAR reofuscado aprovados.
- Versão experimental anterior reproduziu o modelo estático do Guardião, a
  ausência do livro comum na tela e a falta de abertura do livro vazio.
- Cópia descartável do teste-ars: onze capturas, conferência visual do livro
  comum e Arcano em primeira e terceira pessoa, livro vazio e jogador remoto.
  Abertura, soltura da tecla, troca de item e versão exibida verificadas.
- Servidor dedicado mínimo sem classes gráficas: inicialização e regressões da
  CR25 aprovadas, incluindo efeitos sem dono, dano, morte e expiração de Storm.
- Cópia descartável do Forbidden Series do Prism: onze capturas e os mesmos
  cenários aprovados. Conferidos também o livro comum, o Arcano em primeira e
  terceira pessoa e o livro de um jogador remoto com os renderizadores do pack.
  Um primeiro ensaio ficou coberto pelo manual inicial do OpenBlocks; o lançador
  de testes passou a fechar essas telas somente na instância descartável.

Os testes usam mundos novos descartáveis e não alteram saves do usuário.
Não houve ensaio de sessão prolongada ou conexão real entre dois computadores.
Nenhum resultado aqui equivale a um novo CI publicado; a última publicação
verificada continua sendo a CR25. A CR26 não foi enviada ao GitHub.

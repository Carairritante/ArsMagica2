# Molduras do Compêndio e tarefas do mundo — CR19

Na investigação de uma desconexão de singleplayer em 3 de novembro de 2026,
o relatório registrou `ConcurrentModificationException` na atualização do mundo
do servidor integrado. O cliente permaneceu aberto. O log também mostrou
`ItemFrameWatcher.checkFrameRadius` consultando `WorldServer` em `Client thread`.

O proxy do AM2 usa a mesma instância do observador no singleplayer. As rotinas
do cliente e do servidor percorriam a mesma coleção. Marcar uma moldura para
remoção não interrompia a consulta: o cliente ainda podia ler blocos do servidor.
A inscrição ocorre na construção das entidades, inclusive molduras preexistentes
ou geradas por outros mods; não depende de o jogador ter colocado uma moldura.

## Correção nativa

- Estados e filas separados para os dois lados lógicos.
- Filas concorrentes recebem os callbacks; o tick apropriado processa os dados.
- Chamadas nativas com lado explícito, verificando o lado efetivo da execução.
- `checkWatchedFrames()` continua disponível para compatibilidade de addons.
- Busca da receita verifica `blockExists` antes de `getBlock`, sem gerar chunks.
- Descarregar o mundo limpa seus dados e rejeita callbacks atrasados.
- Receita e contador de processamento preservados; somente o servidor cria o item.

Não há referências ao Playground ou ao Arcane Engineering nessa implementação.
As regras de Silver e suas interfaces continuam pertencendo ao Playground.

## Evidências e limites

O JAR da instância que apresentou o problema coincide com a CR18 local:
`cd9cb3de0365a58b1639e83d2523415cfe7e24249f00bac97ef0524ccc9a9cad`.
O Playground coincide com o artefato atualizado `independent-silver-oculus`:
`a04423434834c29ba1ec6675da00993cbd762525dd0b7d53db15b751dc76b544`.
O Segundo Cérebro não consta da lista de mods daquele relatório.

Build local e verificadores do JAR passaram. O ensaio gráfico integrado está
registrado no manifesto local do artefato, com hashes e resultado do processo.
O teste usa uma cópia descartável do Forbidden e um mundo de testes anterior,
sem copiar os saves do jogador. A comparação carrega apenas o observador da
CR18 em um classloader isolado, reproduzindo o acesso incorreto com o servidor
temporariamente parado no ponto de comparação.

Na execução concluída em 3 de novembro, a chamada antiga no cliente alterou o
contador do servidor de 9 para 18; a CR19 impediu a alteração. Também passaram:
progresso visual sem criação do item no cliente, receita real no servidor,
consulta a chunks ausentes, callbacks concorrentes e limpeza ao descarregar.
Depois dessas verificações, o mundo permaneceu conectado por 150 segundos,
com 3.142 ticks do servidor observados no ensaio completo. O processo saiu com
código zero e a instância original permaneceu inalterada.

JAR validado: `AM2.5-LTS-1.6.11-LE-CR19.jar`, SHA-256
`929608453fc0a2b2bbe65c30ecee1396d6ea02b975b724da3ab959d54eb0c544`.
Não houve alteração no Playground. Na comparação dos arquivos do JAR, a classe
anônima `ItemsCommonProxy$1` teve somente nomes de variáveis locais de depuração
alterados pelo compilador; seu código e os recursos permaneceram iguais.

O relatório também contém geração de terreno do Distant Horizons e acessos
fora da tarefa principal. Corrigir o defeito demonstrado do AM2 não prova que ele
foi o único responsável pela exceção final, nem elimina problemas de outros mods.
Nenhum código ou configuração do Distant Horizons foi alterado. Build local
e ensaio em jogo não equivalem a publicação ou a novo GitHub Actions concluído.

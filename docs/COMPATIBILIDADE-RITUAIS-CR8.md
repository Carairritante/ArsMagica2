# Compatibilidade de rituais do Giz do Arquiteto Arcano — CR8

O AM2.5 reconhece opcionalmente os rituais do AM2 Playground instalado. A
integração é escrita no AM2.5 e lê as definições públicas do addon por reflexão,
usando a interface IRitualInteraction do próprio AM2. O JAR do addon não é alterado,
copiado ou incluído neste fork; sem ele, os 30 rituais nativos continuam disponíveis.

## Usar o giz

Use o mesmo item e os mesmos controles. As novas opções aparecem com o prefixo
**Playground:**. No survival, o giz orienta a construção e respeita os capítulos
descobertos do Compendium. As infusões dinâmicas também exigem o componente
conhecido quando a descoberta por etapas está ativada. O criativo pode montar
a estrutura. Ingredientes, magia de ativação e condições normais continuam
necessários; construir o desenho não executa nem concede a conclusão do ritual.

Os grupos de orientação do addon podem incluir espelhamentos. O botão mostra
**Orientação n/total**, selecionando uma variante realmente definida pelo addon.
Os círculos nativos continuam usando o controle de giro anterior.

## Catálogo opcional

- 15 rituais registrados pela base do addon: invocações, Village Relocation,
  Soultrap, Torn Space, Breakthrough, efeitos em entidades e quatro infusões.
- 15 adicionais com Blood Magic: quatorze Hellspawn Calls e Damned Mobilization.
- 2 adicionais com Thaumcraft: Chilling Look e Eldritch Mobilization.
- Infusões de buffs criadas dinamicamente a partir dos componentes Hourglass
  aceitos pelo handler original; Banish Rain não faz parte desse caminho.

Na configuração completa testada, são **32 rituais + 33 infusões dinâmicas**,
totalizando 65 opções do addon e 95 opções com os rituais nativos. Sem Thaumcraft
e Blood Magic, foram reconhecidos 15 rituais e 31 infusões. Os números dependem
das integrações e dos componentes registrados na instalação.

## Preparação específica

- A região dos ingredientes do addon usa os limites reais de seu sistema,
  inclusive a segunda camada de altura. A leitura não separa pilhas nem consome
  itens; as funções consumidoras do addon nunca são chamadas pelo giz.
- Soultrap reconhece Phylactery preenchida com uma criatura válida, mantendo
  intacto o NBT do item. Uma Phylactery sem conteúdo válido não satisfaz o aviso.
- Infusões verificam as poções nos três slots do suporte. Clear exige ao menos
  uma poção com efeitos; o guia não aplica ou remove efeitos.
- Para Breakthrough, prepare quatro faces laterais sólidas ao redor do Liquid
  Essence. Sem contenção, o fluido pode espalhar e apagar o giz. O botão de
  montagem recusa a área antes de colocar qualquer parte até haver contenção.
- Para Hell Cow, deixe Netherrack sob o fogo central para mantê-lo aceso.
- A seleção é salva por ID estável. Se uma integração deixar de existir, a
  ferramenta não transforma aquela seleção em outro ritual por coincidência
  de índice. O jogador pode escolher uma opção disponível novamente.

## Limites conhecidos

Bind Ender Mark, Impact Event e Chilling Look passam um núcleo nulo à checagem
de ativação do addon examinado, que aparenta rejeitá-lo. O giz apresenta aviso
nessas três entradas e pode orientar o desenho; esta integração não corrige nem
promete a ativação desses caminhos originais.

Os dois ritos acrescentados ao Witchery usam outro sistema de círculos e altar.
Receitas de Witch Cauldron também são diferentes dos rituais de giz. Esses sistemas
não fazem parte desta primeira integração. Torn Space é uma opção de ritual
distinta da receita de Void Paradox no caldeirão.

## Validação

O build Java 8 e os quatro verificadores de manutenção passaram. Em servidores
Forge descartáveis, com o mesmo JAR CR8:

- Instalação completa: 32 rituais e 33 infusões, 260 montagens cobrindo quatro
  parâmetros de orientação por entrada, com repetições quando só há 1 ou 2 variantes.
- Sem Thaumcraft/Blood Magic: 15 rituais e 31 infusões, 184 montagens.
- Sem Playground: 30 rituais nativos, sem falha de dependência opcional.
- Teste nativo: as 120 montagens anteriores, consumo, proteção e persistência.

Foram conferidos os grupos preenchidos, além de checkStructure, pois algumas
definições do addon incluem um grupo padrão vazio. Passaram também os limites
dos ingredientes, conteúdo dos recipientes, conservação dos itens, proteção
Forge com rollback, seleção persistida e suporte do fogo. Foi reproduzido o
fluido apagando giz sem contenção e preservando o desenho com contenção.

Um cliente gráfico com a cópia completa dos mods testou seleções nativas e do
addon, navegação, orientações, rotação, limpeza, clique no chão e montagem do
desenho de Eldritch Mobilization. As capturas da interface e do desenho foram
conferidas visualmente. As execuções usaram mundos descartáveis e não editaram
saves existentes. Os probes estão fora do mod e não devem ser instalados no jogo normal.
